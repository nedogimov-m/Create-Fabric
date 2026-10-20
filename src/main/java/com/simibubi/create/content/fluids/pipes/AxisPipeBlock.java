package com.simibubi.create.content.fluids.pipes;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
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

public class AxisPipeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock,IAxisPipe {
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.AXIS;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public AxisPipeBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(AXIS,Direction.Axis.Y).setValue(WATERLOGGED,false));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof StraightPipeBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AXIS,WATERLOGGED);}
    @Override public Direction.Axis getAxis(BlockState state){return state.getValue(AXIS);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StraightPipeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.STRAIGHT_PIPE,StraightPipeBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(AXIS,context.getClickedFace().getAxis()).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof StraightPipeBlockEntity pipe)pipe.updateNetwork();}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){for(var side:Direction.values()){var pipe=FluidPropagator.getPipe(level,pos.relative(side));if(pipe!=null)FluidPropagator.propagateChangedPipe(level,pos.relative(side),pipe.blockEntity.getBlockState());}}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(state.getValue(AXIS)){case X->box(0,4,4,16,12,12);case Y->box(4,0,4,12,16,12);case Z->box(4,4,0,12,12,16);};}
    @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(!stack.is(AllBlocks.COPPER_CASING.asItem())||!player.mayBuild())return net.minecraft.world.InteractionResult.PASS;
        if(!level.isClientSide()){
            var flow=FluidPropagator.getPipe(level,pos);var data=flow==null?null:flow.interfaces;
            var next=AllBlocks.ENCASED_FLUID_PIPE.getAxisState(state.getValue(AXIS)).setValue(WATERLOGGED,state.getValue(WATERLOGGED));level.setBlockAndUpdate(pos,next);var after=FluidPropagator.getPipe(level,pos);if(after!=null&&data!=null){after.interfaces=data;after.wipePressure();}
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override protected net.minecraft.world.item.ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return new net.minecraft.world.item.ItemStack(AllBlocks.FLUID_PIPE);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(AXIS))).getAxis());}
}
