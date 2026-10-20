package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public abstract class HorizontalAxisKineticBlock extends KineticBlock {
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    protected HorizontalAxisKineticBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(HORIZONTAL_AXIS,Direction.Axis.X));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HORIZONTAL_AXIS);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_AXIS);}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face){return face.getAxis()==getRotationAxis(state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var preferred=getPreferredHorizontalAxis(context);return defaultBlockState().setValue(HORIZONTAL_AXIS,preferred!=null?preferred:context.getHorizontalDirection().getClockWise().getAxis());}
    public static Direction.Axis getPreferredHorizontalAxis(BlockPlaceContext context){
        Direction.Axis preferred=null;
        for(var face:Direction.Plane.HORIZONTAL){var pos=context.getClickedPos().relative(face);var state=LoadedLevelAccess.blockState(context.getLevel(),pos);
            if(state.getBlock() instanceof IRotate rotate&&rotate.hasShaftTowards(context.getLevel(),pos,state,face.getOpposite())){if(preferred!=null&&preferred!=face.getAxis())return null;preferred=face.getAxis();}}
        return preferred;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HORIZONTAL_AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,getRotationAxis(state))).getAxis());}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state;}
}
