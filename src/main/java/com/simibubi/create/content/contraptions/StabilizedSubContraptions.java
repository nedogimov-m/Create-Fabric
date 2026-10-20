package com.simibubi.create.content.contraptions;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.bearing.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Parent topology and cargo-free recovery metadata. Native child entities remain the sole owners. */
public final class StabilizedSubContraptions {
    public record Location(BlockPos bearing, Direction facing) {
        public Location { bearing = bearing.immutable(); Objects.requireNonNull(facing); }
        public BlockPos connected() { return bearing.relative(facing); }
        public CompoundTag write() { var tag = new CompoundTag(); tag.store("Pos", BlockPos.CODEC, bearing); tag.putString("Face", facing.name()); return tag; }
        public static Location read(CompoundTag tag) {
            BlockPos pos;
            if (tag.get("Pos") instanceof CompoundTag old) pos = new BlockPos(old.getIntOr("X", 0), old.getIntOr("Y", 0), old.getIntOr("Z", 0));
            else pos = tag.read("Pos", BlockPos.CODEC).orElseThrow(() -> new IllegalArgumentException("Missing stabilized bearing position"));
            return new Location(pos, Direction.valueOf(tag.getStringOr("Face", "").toUpperCase(Locale.ROOT)));
        }
    }
    private final Contraption owner;
    private final LinkedHashSet<Location> pending = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, Location> children = new LinkedHashMap<>();
    private final LinkedHashSet<BlockPos> restorationUpdates = new LinkedHashSet<>();
    private boolean recovering, transferring;
    public StabilizedSubContraptions(Contraption owner) { this.owner = Objects.requireNonNull(owner); }
    public void resetCapture() { if (!children.isEmpty() || recovering || !restorationUpdates.isEmpty()) throw new IllegalStateException("Cannot reset live child ownership"); pending.clear(); }
    public void queue(BlockPos localBearing, Direction facing) { pending.add(new Location(localBearing, facing)); }
    public List<Location> pending() { return List.copyOf(pending); }
    public Map<UUID, Location> children() { return Collections.unmodifiableMap(children); }
    public Location location(UUID child) { return children.get(child); }
    public boolean contains(UUID child) { return children.containsKey(child); }
    public void remember(UUID child, Location location) {
        var state = owner.getBlocks().get(location.bearing());
        if (state == null || !state.state().is(AllBlocks.MECHANICAL_BEARING) || state.state().getValue(BearingBlock.FACING) != location.facing()) throw new IllegalArgumentException("Child location is not a captured mechanical bearing");
        if (children.containsKey(child) || children.containsValue(location)) throw new IllegalArgumentException("Duplicate stabilized child identity/location");
        children.put(child, location);
    }
    public void completed(UUID child) { children.remove(child); }
    public boolean isRecovering() { return recovering; }
    public void setRecovering(boolean value) { recovering = value; }
    public boolean hasPendingRestoration() { return !restorationUpdates.isEmpty(); }
    public Collection<BlockPos> pendingRestoration() { return Collections.unmodifiableSet(restorationUpdates); }
    Collection<BlockPos> restorationJournal(){return restorationUpdates;}
    public void addRestored(Contraption structure, StructureTransform transform) { restorationUpdates.addAll(structure.restorationNotificationPositions(transform)); }
    public boolean finishRestoration(ServerLevel level) {
        if (!children.isEmpty()) return false;
        DeferredContraptionRestoration.finishOrQueue(level, restorationUpdates);
        restorationUpdates.clear(); recovering = false; return true;
    }
    public boolean enterTransfer() { if (transferring) return false; transferring = true; return true; }
    public boolean isTransferring() { return transferring; }
    public void leaveTransfer() { transferring = false; }
    public boolean accepts(Entity child) {
        return owner.entity != null && child instanceof OrientedContraptionEntity oriented && contains(child.getUUID()) && Objects.equals(oriented.getParentId(), owner.entity.getUUID()) && Objects.equals(oriented.getParentLocation(), location(child.getUUID()));
    }
    public Vec3 passengerPosition(OrientedContraptionEntity child, float partial) {
        var location = children.get(child.getUUID());
        return location == null ? null : owner.entity.toGlobalVector(Vec3.atCenterOf(location.connected()), partial).add(0, -.5, 0);
    }
    /** Only loaded native UUIDs are queried. A missing saved owner must keep ownership unresolved. */
    public OrientedContraptionEntity resolve(ServerLevel level, UUID id) throws AssemblyException {
        var location = children.get(id);
        if (location == null) throw new IllegalArgumentException("Unknown child UUID");
        if (!(level.getEntity(id) instanceof OrientedContraptionEntity child) || child.isRemoved() || !accepts(child) || child.getContraption() == null) throw AssemblyException.entitiesNotReady(owner.anchor);
        return child;
    }
    public void reattachLoaded(ServerLevel level) {
        for (var id : List.copyOf(children.keySet())) {
            if (!(level.getEntity(id) instanceof OrientedContraptionEntity child) || !accepts(child) || child.getContraption() == null) continue;
            if (child.getVehicle() == owner.entity) continue;
            if (child.getVehicle() == null) child.startRiding(owner.entity, true, true);
        }
    }
    public boolean hasStalledChild(ServerLevel level) {
        for (var id : children.keySet()) {
            if (!(level.getEntity(id) instanceof OrientedContraptionEntity child) || !accepts(child) || child.getContraption() == null || child.isStalled() || child.getContraption().getStabilizedSubContraptions().isRecovering()) return true;
        }
        return false;
    }
    public void write(CompoundTag tag) {
        var list = new ListTag();
        for (var entry : children.entrySet()) { var child = new CompoundTag(); child.store("Id", UUIDUtil.CODEC, entry.getKey()); child.put("Location", entry.getValue().write()); list.add(child); }
        tag.put("SubContraptions", list);
        tag.putBoolean("GraphRecovering", recovering);
        if (!restorationUpdates.isEmpty()) tag.store("GraphRestorationUpdates", BlockPos.CODEC.listOf(), List.copyOf(restorationUpdates));
    }
    public void read(CompoundTag tag) {
        pending.clear(); children.clear(); restorationUpdates.clear(); transferring = false;
        for (var value : tag.getListOrEmpty("SubContraptions")) {
            if (!(value instanceof CompoundTag child)) throw new IllegalArgumentException("Invalid child entry");
            var id = child.read("Id", UUIDUtil.CODEC).orElseThrow(() -> new IllegalArgumentException("Missing child UUID"));
            remember(id, Location.read(child.getCompoundOrEmpty("Location")));
        }
        for (var pos : tag.read("GraphRestorationUpdates", BlockPos.CODEC.listOf()).orElse(List.of())) restorationUpdates.add(pos.immutable());
        recovering = tag.getBooleanOr("GraphRecovering", false) || !restorationUpdates.isEmpty();
    }
}
