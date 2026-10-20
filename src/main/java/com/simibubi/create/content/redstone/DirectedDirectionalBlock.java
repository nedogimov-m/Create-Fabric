package com.simibubi.create.content.redstone;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Horizontal appearance and independently directed target, preserving original state properties. */
public abstract class DirectedDirectionalBlock extends BaseEntityBlock {
    public static final EnumProperty<AttachFace> TARGET=EnumProperty.create("target",AttachFace.class);
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    protected DirectedDirectionalBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(TARGET,AttachFace.WALL).setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(TARGET,FACING);}
    public static Direction getTargetDirection(BlockState state){return switch(state.getValue(TARGET)){case CEILING->Direction.UP;case FLOOR->Direction.DOWN;case WALL->state.getValue(FACING);};}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var direction=context.getNearestLookingDirection();return direction.getAxis().isVertical()?defaultBlockState().setValue(TARGET,direction==Direction.UP?AttachFace.CEILING:AttachFace.FLOOR).setValue(FACING,context.getHorizontalDirection()):defaultBlockState().setValue(FACING,direction.getOpposite());}
    public BlockState getRotatedBlockState(BlockState state,Direction face){
        if(face.getAxis().isVertical())return state.setValue(FACING,state.getValue(FACING).getClockWise(face.getAxis()));
        var rotated=getTargetDirection(state).getClockWise(face.getAxis());if(face.getAxisDirection()==Direction.AxisDirection.NEGATIVE)rotated=rotated.getOpposite();return rotated.getAxis().isVertical()?state.setValue(TARGET,rotated==Direction.UP?AttachFace.CEILING:AttachFace.FLOOR):state.setValue(TARGET,AttachFace.WALL).setValue(FACING,rotated);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
