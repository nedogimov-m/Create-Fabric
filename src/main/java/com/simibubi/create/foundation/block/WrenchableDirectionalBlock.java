package com.simibubi.create.foundation.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** Actual source directional helper block; registration may deliberately omit an item. */
public class WrenchableDirectionalBlock extends DirectionalBlock {
    public WrenchableDirectionalBlock(Properties properties){super(properties);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    public BlockState getRotatedBlockState(BlockState state,Direction face){var facing=state.getValue(FACING);return facing.getAxis()==face.getAxis()?state:state.setValue(FACING,facing.getClockWise(face.getAxis()));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getNearestLookingDirection());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
