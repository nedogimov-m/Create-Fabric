package com.simibubi.create.content.fluids.pump;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
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

public final class PumpBlock extends KineticBlock implements ICogWheel,SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public PumpBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof PumpBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,WATERLOGGED);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(FACING).getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return false;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PumpBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.MECHANICAL_PUMP,PumpBlockEntity::tick);}
    public static boolean isPump(BlockState state){return state.getBlock() instanceof PumpBlock;}
    public static boolean isOpenAt(BlockState state,Direction side){return side.getAxis()==state.getValue(FACING).getAxis();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var facing=com.simibubi.create.content.kinetics.drill.DrillBlock.facingForPlacement(context);boolean shift=context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown();
        var target=shift?context.getNearestLookingDirection():context.getNearestLookingDirection().getOpposite();Direction best=null;int distance=Integer.MAX_VALUE;
        for(var side:Direction.values()){
            var pos=context.getClickedPos().relative(side);if(!context.getLevel().isLoaded(pos))continue;
            if(!FluidPipeBlock.canConnectTo(context.getLevel(),pos,context.getLevel().getBlockState(pos),side))continue;
            int squared=(side.getStepX()-target.getStepX())*(side.getStepX()-target.getStepX())+(side.getStepY()-target.getStepY())*(side.getStepY()-target.getStepY())+(side.getStepZ()-target.getStepZ())*(side.getStepZ()-target.getStepZ());
            if(squared<=distance){best=side;distance=squared;}
        }
        if(best!=null&&best.getAxis()!=target.getAxis()&&!shift)facing=best;
        return defaultBlockState().setValue(FACING,facing).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));
    }
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof PumpBlockEntity pump)pump.updatePressureChange();}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(state.getValue(FACING).getAxis()){case X->box(0,2,2,16,14,14);case Y->box(2,0,2,14,16,14);case Z->box(2,2,0,14,14,16);};}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){for(var side:Direction.values()){var pipe=FluidPropagator.getPipe(level,pos.relative(side));if(pipe!=null)FluidPropagator.propagateChangedPipe(level,pos.relative(side),pipe.blockEntity.getBlockState());}}
}
