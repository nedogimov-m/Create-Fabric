package com.simibubi.create.content.trains.signal;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.track.UnloadedTrackException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One real owning native block in one explicit dimension. */
public abstract class SingleBlockEntityEdgePoint extends TrackEdgePoint {
  protected GlobalPos owner;

  public BlockPos getBlockEntityPos() {
    return Objects.requireNonNull(owner).pos();
  }

  public ResourceKey<Level> getBlockEntityDimension() {
    return Objects.requireNonNull(owner).dimension();
  }

  public GlobalPos owner() {
    return Objects.requireNonNull(owner);
  }

  protected void restoreOwner(GlobalPos owner) {
    this.owner = Objects.requireNonNull(owner);
  }

  @Override
  public boolean canMerge() {
    return false;
  }

  @Override
  public void blockEntityAdded(BlockEntity entity, boolean front) {
    if (entity.getLevel() == null) throw new IllegalArgumentException("Detached station owner");
    var actual = GlobalPos.of(entity.getLevel().dimension(), entity.getBlockPos());
    if (owner != null && !owner.equals(actual))
      throw new IllegalArgumentException("Edge point already has a different world owner");
    owner = actual;
  }

  @Override
  public void blockEntityRemoved(BlockPos pos, boolean front) {
    if (owner != null && owner.pos().equals(pos)) removeFromAllGraphs();
  }

  @Override
  public Collection<GlobalPos> invalidationTargets(LevelAccessor level) {
    return List.of(owner());
  }

  @Override
  public void invalidate(LevelAccessor access) {
    preflightInvalidation(access);
    Level current = access instanceof Level world ? world : null;
    Level world =
        current != null && current.dimension().equals(owner().dimension())
            ? current
            : current != null && current.getServer() != null
                ? current.getServer().getLevel(owner().dimension())
                : null;
    if (world == null) throw new UnloadedTrackException(List.of(owner()));
    invalidateAt(world, owner().pos());
  }

  @Override
  public void read(
      CompoundTag tag,
      HolderLookup.Provider registries,
      boolean migration,
      DimensionPalette dimensions) {
    super.read(tag, registries, migration, dimensions);
    if (!migration) restoreOwner(tag.read("Owner", GlobalPos.CODEC).orElseThrow());
  }

  @Override
  public void write(
      CompoundTag tag, HolderLookup.Provider registries, DimensionPalette dimensions) {
    super.write(tag, registries, dimensions);
    tag.store("Owner", GlobalPos.CODEC, owner());
  }
}
