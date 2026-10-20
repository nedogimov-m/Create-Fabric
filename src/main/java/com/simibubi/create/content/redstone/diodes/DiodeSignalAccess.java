package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Native signal rules over immediately available chunks, including conductor and wire inputs. */
record DiodeSignalAccess(Level level) implements SignalGetter {
    @Override public BlockState getBlockState(BlockPos pos){return LoadedLevelAccess.blockState(level,pos);}
    @Override public BlockEntity getBlockEntity(BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos);}
    @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
    @Override public int getHeight(){return level.getHeight();}
    @Override public int getMinY(){return level.getMinY();}
}
