package com.simibubi.create.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Registered addon-style owners and scoped observers on the actual public Fabric event. */
public final class SmartBlockEntityTestFixtures implements ModInitializer {
    public static ProbeBlock FALLBACK_BLOCK, TICKER_BLOCK;
    public static BlockEntityType<Probe> TYPE;
    public static final BehaviourType<Counting> BASE = new BehaviourType<>(), DEFERRED = new BehaviourType<>(),
        INJECTED = new BehaviourType<>(), SAFE = new BehaviourType<>();
    private static final ThreadLocal<Consumer<BlockEntityBehaviourEvent>> OBSERVER = new ThreadLocal<>();

    @Override public void onInitialize() {
        FALLBACK_BLOCK = registerBlock("smart_fallback", false);
        TICKER_BLOCK = registerBlock("smart_existing_ticker", true);
        TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("smart_probe"),
            FabricBlockEntityTypeBuilder.create(Probe::new, FALLBACK_BLOCK, TICKER_BLOCK).build());
        BlockEntityBehaviourEvent.EVENT.register(event -> {
            var observer = OBSERVER.get();
            if (observer != null)
                observer.accept(event);
        });
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("create_test", path); }
    private static ProbeBlock registerBlock(String name, boolean nativeTicker) {
        var key = ResourceKey.create(Registries.BLOCK, id(name));
        var block = Registry.register(BuiltInRegistries.BLOCK, key,
            new ProbeBlock(BlockBehaviour.Properties.of().setId(key), nativeTicker));
        var itemKey = ResourceKey.create(Registries.ITEM, id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey)));
        return block;
    }

    public static Scope observe(Consumer<BlockEntityBehaviourEvent> observer) {
        var previous = OBSERVER.get();
        OBSERVER.set(observer);
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Consumer<BlockEntityBehaviourEvent> previous;
        private Scope(Consumer<BlockEntityBehaviourEvent> previous) { this.previous = previous; }
        @Override public void close() { if (previous == null) OBSERVER.remove(); else OBSERVER.set(previous); }
    }

    public static final class ProbeBlock extends Block implements EntityBlock {
        private final boolean nativeTicker;
        ProbeBlock(Properties properties, boolean nativeTicker) { super(properties); this.nativeTicker = nativeTicker; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Probe(pos, state); }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            if (!nativeTicker || type != TYPE)
                return null;
            return (world, pos, actualState, actual) -> ((Probe) actual).tick();
        }
    }

    public static final class Probe extends SmartBlockEntity {
        public List<String> trace;
        public Counting base, deferred;
        public int constructorHooks, deferredHooks, deferredSeed, seed, ownerInitializations,
            ownerLazyTicks, productTicks, removals;
        public Probe(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
            constructorHooks++;
            trace = new ArrayList<>();
            behaviours.add(base = new Counting(this, BASE, "Base", false));
            registerAwardables(behaviours, AllAdvancements.WATER_WHEEL);
        }
        @Override public void addBehavioursDeferred(List<BlockEntityBehaviour> behaviours) {
            deferredHooks++;
            deferredSeed = seed;
            behaviours.add(deferred = new Counting(this, DEFERRED, "Deferred", false));
        }
        @Override protected void read(ValueInput input, boolean clientPacket) {
            seed = input.getIntOr("Seed", 0);
            super.read(input, clientPacket);
        }
        @Override protected void write(ValueOutput output, boolean clientPacket) {
            super.write(output, clientPacket);
            output.putInt("Seed", seed);
        }
        @Override public void initialize() { ownerInitializations++; super.initialize(); }
        @Override public void lazyTick() { ownerLazyTicks++; }
        @Override public void tick() { super.tick(); productTicks++; }
        @Override public void remove() { removals++; super.remove(); }
        public void invalidateBounds() { invalidateRenderBoundingBox(); }
    }

    public static final class Counting extends BlockEntityBehaviour {
        private final BehaviourType<Counting> type;
        public final String label;
        private final boolean safe;
        public int reads, writes, initializations, ticks, unloads, destroys, value;
        public boolean clientRead, clientWrite;
        public final List<Integer> payloads = new ArrayList<>();
        public Runnable onInitialize = () -> {}, onTick = () -> {};
        public Counting(SmartBlockEntity owner, BehaviourType<Counting> type, String label, boolean safe) {
            super(owner); this.type = type; this.label = label; this.safe = safe;
        }
        private void record(String phase) {
            if (blockEntity instanceof Probe probe)
                probe.trace.add(label + ":" + phase);
        }
        @Override public BehaviourType<?> getType() { return type; }
        @Override public void initialize() { initializations++; record("initialize"); onInitialize.run(); }
        @Override public void tick() { ticks++; record("tick"); super.tick(); onTick.run(); }
        @Override public void read(ValueInput input, boolean clientPacket) {
            reads++; clientRead = clientPacket; value = input.getIntOr(label, input.getIntOr("AddonValue", 0));
            payloads.add(value); record("read");
        }
        @Override public void write(ValueOutput output, boolean clientPacket) {
            writes++; clientWrite = clientPacket; output.putInt(label, value); record("write");
        }
        @Override public boolean isSafeNBT() { return safe; }
        @Override public void unload() { unloads++; record("unload"); }
        @Override public void destroy() { destroys++; record("destroy"); }
    }
}
