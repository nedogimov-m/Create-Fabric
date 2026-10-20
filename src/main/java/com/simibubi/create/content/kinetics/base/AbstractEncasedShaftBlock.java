package com.simibubi.create.content.kinetics.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;

/** Native vanilla properties provide source NORMAL piston and non-conductor behavior. */
public abstract class AbstractEncasedShaftBlock extends RotatedPillarKineticBlock {
    protected AbstractEncasedShaftBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(net.minecraft.world.level.block.state.BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, Direction face) { return face.getAxis() == state.getValue(AXIS); }
}
