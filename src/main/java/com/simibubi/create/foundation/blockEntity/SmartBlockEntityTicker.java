package com.simibubi.create.foundation.blockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

public class SmartBlockEntityTicker<T extends BlockEntity> implements BlockEntityTicker<T> {
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, T entity) {
        if (!(entity instanceof SmartBlockEntity smart))
            throw new IllegalArgumentException("Smart ticker requires the actual SmartBlockEntity");
        SmartBlockEntity.tickNative(level, pos, state, smart, smart::tick);
    }
}
