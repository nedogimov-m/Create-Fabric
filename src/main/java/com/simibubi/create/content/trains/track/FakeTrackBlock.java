package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

/** The original invisible, non-colliding temporary curved-track pathfinding coverage, not a train substitute. */
public final class FakeTrackBlock extends Block implements EntityBlock,SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public FakeTrackBlock(Properties properties){super(properties.randomTicks().noCollision().noOcclusion());registerDefaultState(defaultBlockState().setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(WATERLOGGED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().isSourceOfType(Fluids.WATER));}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override protected VoxelShape getInteractionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    // Native sprint particles skip INVISIBLE. Landing particles are narrowly suppressed by the real particle hook.
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(LoadedLevelAccess.blockEntity(level,pos) instanceof FakeTrackBlockEntity coverage)coverage.randomTick();}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FakeTrackBlockEntity(pos,state);}
    public static void keepAlive(Level level,BlockPos pos){if(LoadedLevelAccess.blockEntity(level,pos) instanceof FakeTrackBlockEntity coverage)coverage.keepAlive();}
}
