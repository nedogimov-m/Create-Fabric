package com.simibubi.create.content.fluids.pipes.valve;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.*;

public final class FluidValveBlock extends DirectionalAxisKineticBlock implements IAxisPipe,SimpleWaterloggedBlock {
    public static final BooleanProperty ENABLED=BooleanProperty.create("enabled"),WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public FluidValveBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP).setValue(AXIS_ALONG_FIRST_COORDINATE,false).setValue(ENABLED,false).setValue(WATERLOGGED,false));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidValveBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(ENABLED,WATERLOGGED);}
    public static Direction.Axis getPipeAxis(BlockState state){
        var facing=state.getValue(FACING);boolean first=!state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        for(var axis:Direction.Axis.values())if(axis!=facing.getAxis()){if(first)return axis;first=true;}throw new IllegalStateException("Valve has no pipe axis");
    }
    @Override public Direction.Axis getAxis(BlockState state){return getPipeAxis(state);}
    @Override protected boolean prefersConnectionTo(LevelReader level,BlockPos pos,Direction side,boolean shaft){
        if(shaft)return super.prefersConnectionTo(level,pos,side,true);var next=pos.relative(side);return FluidPipeBlock.canConnectTo(level,next,FluidPropagator.stateAt(level,next),side);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FluidValveBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.FLUID_VALVE,FluidValveBlockEntity::tick);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(!level.isClientSide())FluidPropagator.propagateChangedPipe(level,pos,state);}
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(!level.isClientSide()&&state!=old)FluidPropagator.propagateChangedPipe(level,pos,state);}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){for(var side:Direction.values()){var pipe=FluidPropagator.getPipe(level,pos.relative(side));if(pipe!=null)FluidPropagator.propagateChangedPipe(level,pos.relative(side),pipe.blockEntity.getBlockState());}}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var pole=switch(getPipeAxis(state)){case X->box(0,3,3,16,13,13);case Y->box(3,0,3,13,16,13);case Z->box(3,3,0,13,13,16);};return Shapes.or(pole,box(2,2,2,14,14,14));}
}
