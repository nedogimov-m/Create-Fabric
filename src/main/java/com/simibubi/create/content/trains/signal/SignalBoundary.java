package com.simibubi.create.content.trains.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.OverlayState;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Actual two-sided graph boundary, native world owners and source train traffic state. */
public final class SignalBoundary extends TrackEdgePoint implements TrackEdgePoint.SignalBoundaryPoint {
    public record Owner(GlobalPos position, boolean power) {
        public static final Codec<Owner> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.fieldOf("Owner").forGetter(Owner::position),
            Codec.BOOL.fieldOf("Power").forGetter(Owner::power)).apply(i, Owner::new));
        public Owner { position = GlobalPos.of(position.dimension(), position.pos().immutable()); }
    }
    public record Side(List<Owner> owners, SignalType type, Optional<UUID> group,
                       boolean update, SignalState state) {
        public static final Codec<Side> CODEC = RecordCodecBuilder.create(i -> i.group(
            Owner.CODEC.listOf().fieldOf("Tiles").forGetter(Side::owners),
            SignalType.CODEC.fieldOf("Type").forGetter(Side::type),
            UUIDUtil.CODEC.optionalFieldOf("Group").forGetter(Side::group),
            Codec.BOOL.fieldOf("Update").forGetter(Side::update),
            SignalState.CODEC.fieldOf("State").forGetter(Side::state)).apply(i, Side::new));
        public Side { owners = List.copyOf(owners); }
    }
    public static final Codec<SignalBoundary> CODEC = RecordCodecBuilder.create(i -> i.group(
        TrackEdgePoint.baseCodec(SignalBoundary::new).fieldOf("Point").forGetter(p -> p),
        Side.CODEC.fieldOf("First").forGetter(p -> p.side(true)),
        Side.CODEC.fieldOf("Second").forGetter(p -> p.side(false))
    ).apply(i, (point, first, second) -> {
        point.restore(true, first); point.restore(false, second); point.validateOwners(); return point;
    }));
    private final Couple<Map<GlobalPos, Boolean>> owners = Couple.create(LinkedHashMap::new);
    private final Couple<SignalType> types = Couple.create(() -> SignalType.ENTRY_SIGNAL);
    private final Couple<UUID> groups = Couple.create(null, null);
    private final Couple<Boolean> updates = Couple.create(true, true);
    private final Couple<SignalState> cachedStates = Couple.create(() -> SignalState.INVALID);

    public Map<GlobalPos, Boolean> owners(boolean primary) { return Map.copyOf(owners.get(primary)); }
    public UUID group(boolean primary) { return groups.get(primary); }
    public SignalType type(boolean primary){return types.get(primary);}
    public boolean isForcedRed(TrackNode side){return isForcedRed(isPrimary(side));}
    @Override public UUID groupFor(TrackNode side) { return groups.get(isPrimary(side)); }
    public boolean updateQueued(boolean primary) { return updates.get(primary); }
    public boolean hasOwner(GlobalPos owner) { return owners.getFirst().containsKey(owner) || owners.getSecond().containsKey(owner); }
    private Side side(boolean primary) {
        return new Side(owners.get(primary).entrySet().stream().map(e -> new Owner(e.getKey(), e.getValue())).toList(),
            types.get(primary), Optional.ofNullable(groups.get(primary)), updates.get(primary), cachedStates.get(primary));
    }
    private void restore(boolean primary, Side side) {
        var values = owners.get(primary); values.clear();
        for (var owner : side.owners())
            if (values.putIfAbsent(owner.position(), owner.power()) != null)
                throw new IllegalArgumentException("Duplicate actual signal owner");
        types.set(primary, side.type()); groups.set(primary, side.group().orElse(null));
        updates.set(primary, side.update()); cachedStates.set(primary, side.state());
    }
    private void validateOwners() {
        for (var owner : owners.getFirst().keySet())
            if (owners.getSecond().containsKey(owner)) throw new IllegalArgumentException("Signal owner on both boundary sides");
        var byPosition = new HashMap<BlockPos, GlobalPos>();
        for (boolean primary : new boolean[] {true, false}) for (var owner : owners.get(primary).keySet()) {
            var previous = byPosition.putIfAbsent(owner.pos(), owner);
            if (previous != null && !previous.equals(owner))
                throw new IllegalArgumentException("Ambiguous signal owner position across dimensions");
        }
    }
    private boolean ownerSide(GlobalPos owner) {
        if (owners.getFirst().containsKey(owner)) return true;
        if (owners.getSecond().containsKey(owner)) return false;
        throw new IllegalArgumentException("Unregistered actual signal owner " + owner);
    }
    @Override public boolean canMerge() { return true; }
    @Override public boolean canCoexistWith(EdgePointType<?> otherType, boolean front) { return otherType == getType(); }
    @Override public void blockEntityAdded(BlockEntity entity, boolean front) {
        if (!(entity instanceof SignalBlockEntity signal) || entity.getLevel() == null)
            throw new IllegalArgumentException("Actual native signal block entity required");
        var owner = GlobalPos.of(entity.getLevel().dimension(), entity.getBlockPos());
        if (owners.get(!front).containsKey(owner)) throw new IllegalArgumentException("Same signal owner on opposite boundary sides");
        for (boolean side : new boolean[] {true, false}) for (var previous : owners.get(side).keySet())
            if (previous.pos().equals(owner.pos()) && !previous.equals(owner))
                throw new IllegalArgumentException("Ambiguous signal owner coordinate across dimensions");
        var values = owners.get(front);
        if (values.isEmpty()) types.set(front, entity.getBlockState().getValue(SignalBlock.TYPE));
        values.put(owner, signal.getReportedPower());
        updates.set(front, true);
        if (graph() != null) graph().markDirty();
    }
    public void updateBlockEntityPower(SignalBlockEntity signal) {
        if (signal.getLevel() == null) return;
        var owner = GlobalPos.of(signal.getLevel().dimension(), signal.getBlockPos());
        for (boolean side : new boolean[] {true, false}) {
            var previous = owners.get(side).get(owner);
            if (previous != null && previous != signal.getReportedPower()) {
                owners.get(side).put(owner, signal.getReportedPower());
                if (graph() != null) graph().markDirty();
            }
        }
    }
    @Override public void blockEntityRemoved(BlockPos position, boolean front) {
        boolean changed = owners.getFirst().keySet().removeIf(owner -> owner.pos().equals(position));
        changed |= owners.getSecond().keySet().removeIf(owner -> owner.pos().equals(position));
        if (!changed) return;
        if (owners.getFirst().isEmpty() && owners.getSecond().isEmpty()) removeFromAllGraphs();
        else if (graph() != null) graph().markDirty();
    }
    @Override public Collection<GlobalPos> invalidationTargets(LevelAccessor level) {
        var result = new LinkedHashSet<GlobalPos>(owners.getFirst().keySet());
        result.addAll(owners.getSecond().keySet()); return List.copyOf(result);
    }
    @Override public void invalidate(LevelAccessor level) {
        preflightInvalidation(level);
        for (var owner : invalidationTargets(level)) {
            if (!(level instanceof Level current)) throw new IllegalArgumentException("Actual native level required");
            var actual = current.dimension().equals(owner.dimension()) ? current
                : current.getServer() == null ? null : current.getServer().getLevel(owner.dimension());
            if (actual == null) throw new IllegalStateException("Signal owner dimension disappeared after preflight");
            invalidateAt(actual, owner.pos());
        }
    }
    @Override public void onRemoved(TrackGraph graph) { super.onRemoved(graph); SignalTopology.rebuild(graph); }
    @Override public boolean canNavigateVia(TrackNode side) { return !owners.get(isPrimary(side)).isEmpty(); }
    public boolean isForcedRed(boolean primary) { return owners.get(primary).values().stream().anyMatch(Boolean::booleanValue); }
    public SignalType getTypeFor(GlobalPos owner) { return types.get(ownerSide(owner)); }
    public SignalState getStateFor(GlobalPos owner) { return hasOwner(owner) ? cachedStates.get(ownerSide(owner)) : SignalState.INVALID; }
    public OverlayState getOverlayFor(GlobalPos owner) {
        if (!hasOwner(owner)) return OverlayState.SKIP;
        boolean primary = !owners.getFirst().isEmpty();
        // Exactly one actual owner draws the shared marker; DUAL already contains both arrows.
        var representative = owners.get(primary).keySet().stream().min(Comparator
            .comparing((GlobalPos p) -> p.dimension().identifier().toString()).thenComparingLong(p -> p.pos().asLong())).orElseThrow();
        if (!representative.equals(owner)) return OverlayState.SKIP;
        return owners.get(!primary).isEmpty() ? OverlayState.RENDER : OverlayState.DUAL;
    }
    public void cycleSignalType(GlobalPos owner) {
        boolean primary = ownerSide(owner);
        types.set(primary, types.get(primary) == SignalType.ENTRY_SIGNAL ? SignalType.CROSS_SIGNAL : SignalType.ENTRY_SIGNAL);
        if (graph() != null) graph().markDirty();
    }
    public void setSignalType(GlobalPos owner, SignalType type) {
        boolean primary = ownerSide(owner);
        if (types.get(primary) == type) return;
        types.set(primary, Objects.requireNonNull(type)); if (graph() != null) graph().markDirty();
    }
    void assignGroup(boolean primary, UUID group) {
        if (!Objects.equals(groups.get(primary), group)) {
            groups.set(primary, group); cachedStates.set(primary, SignalState.INVALID);
            if (graph() != null) graph().markDirty();
        }
        updates.set(primary, false);
    }
    @Override public void tick(TrackGraph graph, boolean preTrains) {
        if (preTrains) { SignalTopology.refresh(graph); return; }
        for (boolean primary : new boolean[] {true, false}) {
            if (owners.get(primary).isEmpty()) continue;
            var manager=graph.manager();
            var server=manager==null?null:manager.server();
            var traffic=server==null?null:com.simibubi.create.content.trains.TrainManager.get(server);
            var next=traffic==null||!traffic.isReady()?SignalState.INVALID:SignalTraffic.evaluate(graph,this,primary,traffic);
            if (cachedStates.get(primary) != next) { cachedStates.set(primary, next); graph.markDirty(); }
        }
    }
    @Override public void write(CompoundTag tag, HolderLookup.Provider registries, DimensionPalette dimensions) {
        super.write(tag, registries, dimensions);
        for (boolean primary : new boolean[] {true, false}) {
            int index = primary ? 1 : 2;
            tag.store("Tiles" + index, Owner.CODEC.listOf(), side(primary).owners());
            if (groups.get(primary) != null) tag.store("Group" + index, UUIDUtil.CODEC, groups.get(primary));
            if (updates.get(primary)) tag.putBoolean("Update" + index, true);
            tag.putString("Type" + index, types.get(primary).name());
            tag.putString("State" + index, cachedStates.get(primary).name());
        }
    }
    @Override public void read(CompoundTag tag, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions) {
        super.read(tag, registries, migration, dimensions);
        if (migration) return;
        for (boolean primary : new boolean[] {true, false}) {
            int index = primary ? 1 : 2;
            var decoded = new ArrayList<Owner>();
            for (var value : tag.getListOrEmpty("Tiles" + index)) {
                if (!(value instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid signal owner entry");
                var owner = entry.read("Owner", GlobalPos.CODEC).orElseGet(() -> {
                    var pos = entry.read("Pos", BlockPos.CODEC).orElseGet(() -> {
                        var old = entry.getCompoundOrEmpty("Pos");
                        return new BlockPos(old.getIntOr("X", 0), old.getIntOr("Y", 0), old.getIntOr("Z", 0));
                    });
                    return GlobalPos.of(edgeLocation.getFirst().dimension, pos);
                });
                decoded.add(new Owner(owner, entry.getBooleanOr("Power", false)));
            }
            restore(primary, new Side(decoded, SignalType.valueOf(tag.getStringOr("Type" + index, "ENTRY_SIGNAL")),
                tag.read("Group" + index, UUIDUtil.CODEC), tag.getBooleanOr("Update" + index, false),
                SignalState.valueOf(tag.getStringOr("State" + index, "INVALID"))));
        }
        validateOwners();
    }
    @Override public void write(FriendlyByteBuf buffer, DimensionPalette dimensions) {
        super.write(buffer, dimensions);
        buffer.writeNbt((CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow());
    }
    @Override public void read(FriendlyByteBuf buffer, DimensionPalette dimensions) {
        super.read(buffer, dimensions);
        var tag = buffer.readNbt(NbtAccounter.create(1048576));
        if (!(tag instanceof CompoundTag compound)) throw new IllegalArgumentException("Signal point payload must be a compound");
        var decoded = CODEC.parse(NbtOps.INSTANCE, compound).getOrThrow();
        if (!decoded.id.equals(id) || (!decoded.edgeLocation.getFirst().equals(edgeLocation.getFirst()) || !decoded.edgeLocation.getSecond().equals(edgeLocation.getSecond())) || decoded.position != position)
            throw new IllegalArgumentException("Signal payload geometry disagrees with actual point");
        restore(true, decoded.side(true)); restore(false, decoded.side(false)); validateOwners();
    }
}
