package com.simibubi.create.content.kinetics.base;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public abstract class DirectionalKineticBlock extends KineticBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    protected DirectionalKineticBlock(Properties properties){super(properties);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    public Direction getPreferredFacing(BlockPlaceContext context){
        Direction preferred=null;
        for(var side:Direction.values()){
            var pos=context.getClickedPos().relative(side);if(!context.getLevel().isLoaded(pos))continue;
            var state=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(context.getLevel(),pos);
            if(state.getBlock() instanceof IRotate rotate&&rotate.hasShaftTowards(context.getLevel(),pos,state,side.getOpposite())){
                if(preferred!=null&&preferred.getAxis()!=side.getAxis())return null;preferred=side;
            }
        }
        return preferred;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var preferred=getPreferredFacing(context);boolean shift=context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown();return defaultBlockState().setValue(FACING,preferred!=null&&!shift?preferred.getOpposite():shift?context.getNearestLookingDirection():context.getNearestLookingDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
