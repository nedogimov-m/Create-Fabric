package com.simibubi.create.content.contraptions;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** A structure-local block view for outlines/collision queries; it never reads world chunks. */
public final class ContraptionWorld implements BlockGetter {
  private final Contraption contraption;
  private final java.util.Map<BlockPos, BlockEntity> entities = new java.util.HashMap<>();

  public ContraptionWorld(Contraption contraption) {
    this.contraption = contraption;
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    if (contraption.isHiddenInPortal(pos)) return Blocks.AIR.defaultBlockState();
    var info = contraption.getBlocks().get(pos);
    return info == null ? Blocks.AIR.defaultBlockState() : info.state();
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    if (contraption.isHiddenInPortal(pos)) return null;
    var rendered = contraption.getBlockEntityClientSide(pos);
    if (rendered != null) return rendered;
    if (entities.containsKey(pos)) return entities.get(pos);
    var info = contraption.getBlocks().get(pos);
    if (info == null
        || !(info.state().getBlock()
            instanceof net.minecraft.world.level.block.EntityBlock factory)) return null;
    var be = factory.newBlockEntity(pos, info.state());
    if (be != null && info.nbt() != null && contraption.entity != null)
      be.loadWithComponents(
          net.minecraft.world.level.storage.TagValueInput.create(
              net.minecraft.util.ProblemReporter.DISCARDING,
              contraption.entity.registryAccess(),
              info.nbt().copy()));
    entities.put(pos.immutable(), be);
    return be;
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    return getBlockState(pos).getFluidState();
  }

  @Override
  public int getMinY() {
    return contraption.bounds == null ? -64 : (int) Math.floor(contraption.bounds.minY) - 16;
  }

  @Override
  public int getHeight() {
    return contraption.bounds == null
        ? 384
        : (int) Math.ceil(contraption.bounds.maxY) - getMinY() + 16;
  }

  public static BlockGetter loadedWorld(Level level) {
    return new BlockGetter() {
      @Override
      public BlockState getBlockState(BlockPos pos) {
        return LoadedLevelAccess.blockState(level, pos);
      }

      @Override
      public BlockEntity getBlockEntity(BlockPos pos) {
        return LoadedLevelAccess.blockEntity(level, pos);
      }

      @Override
      public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
      }

      @Override
      public int getMinY() {
        return level.getMinY();
      }

      @Override
      public int getHeight() {
        return level.getHeight();
      }
    };
  }
}
