package com.simibubi.create.content.redstone.link;

import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.simibubi.create.content.redstone.link.controller.QueuedControllerDrops;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

/** Loaded participants only; no search of distant block/entity chunks, no server futures. */
public final class RedstoneLinkNetworkHandler {
    public static final RedstoneLinkNetworkHandler INSTANCE = new RedstoneLinkNetworkHandler();
    public final AtomicInteger globalPowerVersion = new AtomicInteger();
    private final Map<ServerLevel, Space> spaces = new IdentityHashMap<>();
    private final Set<ServerLevel> closed = Collections.newSetFromMap(new WeakHashMap<>());
    private int range = 256;
    private boolean registered;

    public static final class Frequency {
        public static final Frequency EMPTY = new Frequency(ItemStack.EMPTY);
        private final ItemStack sample;
        private final Item item;
        private final int color;
        private Frequency(ItemStack stack) {
            sample = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            item = sample.getItem();
            var dyed = sample.get(DataComponents.DYED_COLOR);
            color = dyed == null ? -1 : dyed.rgb();
        }
        public static Frequency of(ItemStack stack) { return stack.isEmpty() ? EMPTY : new Frequency(stack); }
        public ItemStack getStack() { return sample.copy(); }
        @Override public int hashCode() { return System.identityHashCode(item) * 31 ^ color; }
        @Override public boolean equals(Object other) {
            return other instanceof Frequency frequency && item == frequency.item && color == frequency.color;
        }
    }
    private record Update(IRedstoneLinkable actor, Couple<Frequency> key) {}
    private static final class Space {
        final Map<Couple<Frequency>, LinkedHashSet<IRedstoneLinkable>> networks = new HashMap<>();
        final ArrayDeque<Update> pending = new ArrayDeque<>();
        boolean updating;
    }

    public int getRange() { return range; }
    public void setRange(int range) {
        if (range < 1) throw new IllegalArgumentException("Invalid redstone link range");
        if (this.range == range) return;
        this.range = range;
        for (var space : spaces.values()) for (var entry : space.networks.entrySet())
            for (var actor : List.copyOf(entry.getValue())) space.pending.add(new Update(actor, entry.getKey()));
    }
    public void onLoadWorld(LevelAccessor world) {
        if (world instanceof ServerLevel level) {closed.remove(level);spaces.computeIfAbsent(level, ignored -> new Space());}
    }
    public void onUnloadWorld(LevelAccessor world) {
        if (world instanceof ServerLevel level) {closed.add(level);if(spaces.remove(level)!=null)globalPowerVersion.incrementAndGet();}
    }
    public void unload(MinecraftServer server) {
        for(var level:List.copyOf(spaces.keySet()))if(level.getServer()==server)onUnloadWorld(level);
        globalPowerVersion.incrementAndGet();
    }
    private Space space(LevelAccessor world) {
        return world instanceof ServerLevel level&&!closed.contains(level) ? spaces.computeIfAbsent(level, ignored -> new Space()) : null;
    }
    public boolean isRegistered(LevelAccessor world,IRedstoneLinkable actor){
        var space=world instanceof ServerLevel level?spaces.get(level):null;
        var network=space==null?null:space.networks.get(actor.getNetworkKey());return network!=null&&network.contains(actor);
    }
    public Set<IRedstoneLinkable> getNetworkOf(LevelAccessor world, IRedstoneLinkable actor) {
        var space = space(world);
        if (space == null) return Set.of();
        var network = space.networks.get(actor.getNetworkKey());
        return network == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(network));
    }
    public Map<Couple<Frequency>, Set<IRedstoneLinkable>> networksIn(LevelAccessor world) {
        var space = space(world); if (space == null) return Map.of();
        var result = new HashMap<Couple<Frequency>, Set<IRedstoneLinkable>>();
        space.networks.forEach((key, value) -> result.put(key, Collections.unmodifiableSet(new LinkedHashSet<>(value))));
        return Collections.unmodifiableMap(result);
    }
    public void addToNetwork(LevelAccessor world, IRedstoneLinkable actor) {
        var space = space(world); if (space == null || !actor.isAlive()) return;
        var key = copyKey(actor.getNetworkKey());
        space.networks.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(actor);
        enqueue(space, actor, key);
    }
    public void removeFromNetwork(LevelAccessor world, IRedstoneLinkable actor) {
        var space = world instanceof ServerLevel level?spaces.get(level):null; if (space == null) return;
        var key = copyKey(actor.getNetworkKey()); var network = space.networks.get(key);
        if (network == null || !network.remove(actor)) return;
        // Invalidate even the last participant: a future schedule query must observe power disappearing.
        globalPowerVersion.incrementAndGet();
        if (network.isEmpty()) space.networks.remove(key);
        else enqueue(space, actor, key);
    }
    public void updateNetworkOf(LevelAccessor world, IRedstoneLinkable actor) {
        var space = world instanceof ServerLevel level?spaces.get(level):null; if (space != null) enqueue(space, actor, copyKey(actor.getNetworkKey()));
    }
    private void enqueue(Space space, IRedstoneLinkable actor, Couple<Frequency> key) {
        space.pending.add(new Update(actor, key)); drain(space);
    }
    private void drain(Space space) {
        if (space.updating) return;
        space.updating = true;
        try {
            // An addon callback can enqueue another update. Leave non-converging work for the next tick.
            for (int budget = 4096; budget > 0 && !space.pending.isEmpty(); budget--) update(space, space.pending.remove());
        } finally { space.updating = false; }
    }
    private void update(Space space, Update update) {
        var network = space.networks.get(update.key); if (network == null) return;
        boolean pruned = network.removeIf(actor -> !actor.isAlive() || !update.key.equals(actor.getNetworkKey()));
        if (network.isEmpty()) { space.networks.remove(update.key); if (pruned) globalPowerVersion.incrementAndGet(); return; }
        int power = 0;
        for (var actor : List.copyOf(network))
            if (inRange(update.actor, actor)) power = Math.max(power, Math.clamp(actor.getTransmittedStrength(), 0, 15));
        globalPowerVersion.incrementAndGet();
        // Preserve the source initiator-centered update, including its single computed maximum.
        for (var actor : List.copyOf(network)) {
            if (!actor.isListening() || !actor.isAlive() || !network.contains(actor)
                || !update.key.equals(actor.getNetworkKey()) || !inRange(update.actor, actor)) continue;
            if (actor instanceof LinkBehaviour link) link.newPosition = true;
            actor.setReceivedStrength(power);
        }
    }
    public static boolean withinRange(IRedstoneLinkable from, IRedstoneLinkable to) {
        return from == to || from.getLocation().closerThan(to.getLocation(), INSTANCE.range);
    }
    private boolean inRange(IRedstoneLinkable from,IRedstoneLinkable to){return from==to||from.getLocation().closerThan(to.getLocation(),range);}
    private static Couple<Frequency> copyKey(Couple<Frequency> key) { return Couple.create(key.getFirst(), key.getSecond()); }
    public boolean hasAnyLoadedPower(Couple<Frequency> frequency) {
        return spaces.values().stream().anyMatch(space -> anyPower(space, frequency));
    }
    public boolean hasAnyLoadedPower(MinecraftServer server, Couple<Frequency> frequency) {
        return spaces.entrySet().stream().anyMatch(entry -> entry.getKey().getServer() == server && anyPower(entry.getValue(), frequency));
    }
    private static boolean anyPower(Space space, Couple<Frequency> key) {
        var network = space.networks.get(key); return network != null
            && network.stream().anyMatch(actor -> actor.isAlive() && actor.getTransmittedStrength() > 0);
    }
    public void tick(ServerLevel level) {
        var space = spaces.get(level); if (space == null) return;
        for (var entry : List.copyOf(space.networks.entrySet())) {
            var dead = entry.getValue().stream().filter(actor -> !actor.isAlive()).toList();
            for (var actor : dead) removeFromNetwork(level, actor);
        }
        drain(space);
    }
    public void register() {
        if (registered) return; registered = true;
        ServerLevelEvents.LOAD.register((server, level) -> onLoadWorld(level));
        ServerLevelEvents.UNLOAD.register((server, level) -> { LinkedControllerServerHandler.unload(level); onUnloadWorld(level); });
        ServerTickEvents.END_LEVEL_TICK.register(level -> { LinkedControllerServerHandler.tick(level); tick(level);QueuedControllerDrops.tick(level); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { LinkedControllerServerHandler.unload(server); unload(server); });
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> LinkedControllerServerHandler.releasePlayer(server, connection.player.getUUID()));
        RedstoneLinkLifecycle.register();
    }
}
