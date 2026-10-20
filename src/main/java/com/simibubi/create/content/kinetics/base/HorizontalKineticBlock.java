package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public abstract class HorizontalKineticBlock extends KineticBlock {
    public static final EnumProperty<Direction> HORIZONTAL_FACING=BlockStateProperties.HORIZONTAL_FACING;
    protected HorizontalKineticBlock(Properties properties){super(properties);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HORIZONTAL_FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());}
    public Direction getPreferredHorizontalFacing(BlockPlaceContext context){
        Direction preferred=null;
        for(var side:Direction.Plane.HORIZONTAL){
            var pos=context.getClickedPos().relative(side);var state=LoadedLevelAccess.blockState(context.getLevel(),pos);
            if(state.getBlock() instanceof IRotate rotate&&rotate.hasShaftTowards(context.getLevel(),pos,state,side.getOpposite())){
                if(preferred!=null&&preferred.getAxis()!=side.getAxis())return null;preferred=side;
            }
        }return preferred;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HORIZONTAL_FACING,rotation.rotate(state.getValue(HORIZONTAL_FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));}
}
