package com.simibubi.create.content.contraptions.actors;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

/** Original rear-mounted horizontal actor, including waterlogging and row placement. */
public abstract class AttachedActorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    protected AttachedActorBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,WATERLOGGED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var clicked=context.getClickedFace();Direction facing;
        if(clicked.getAxis().isVertical())facing=context.getHorizontalDirection().getOpposite();
        else{var neighbor=LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().relative(clicked.getOpposite()));facing=neighbor.getBlock() instanceof AttachedActorBlock?neighbor.getValue(FACING):clicked;}
        return defaultBlockState().setValue(FACING,facing).setValue(WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().getType()==Fluids.WATER);
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){var facing=state.getValue(FACING);var support=pos.relative(facing.getOpposite());return Block.isFaceFull(LoadedLevelAccess.blockState(level,support).getCollisionShape(level,support),facing);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(state.getValue(FACING)){case NORTH->box(0,2,13,16,14,16);case SOUTH->box(0,2,0,16,14,3);case EAST->box(0,2,0,3,14,16);case WEST->box(13,2,0,16,14,16);default->Shapes.empty();};}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
}
