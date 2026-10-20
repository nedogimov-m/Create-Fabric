package com.simibubi.create.content.redstone.smartObserver;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.content.redstone.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public final class SmartObserverBlock extends DirectedDirectionalBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public static final FilteredDetectorFilterSlot FILTER_SLOT=new FilteredDetectorFilterSlot(false);
    public SmartObserverBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(POWERED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(POWERED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SmartObserverBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.SMART_OBSERVER,SmartObserverBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction preferred=null;var level=context.getLevel();
        for(var side:context.getNearestLookingDirections()){
            var target=context.getClickedPos().relative(side);if(!level.isLoaded(target))continue;var entity=LoadedLevelAccess.blockEntity(level,target);
            if(BlockEntityBehaviour.get(entity,TransportedItemStackHandlerBehaviour.TYPE)!=null||FluidPropagator.getPipe(level,target)!=null||SmartObserverBlockEntity.findItems(level,target)!=null||SmartObserverBlockEntity.findFluids(level,target)!=null||entity instanceof FunnelBlockEntity||entity instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity){preferred=side;break;}
        }
        if(preferred==null){var facing=context.getNearestLookingDirection();preferred=context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown()?facing:facing.getOpposite();}
        return preferred.getAxis().isVertical()?defaultBlockState().setValue(TARGET,preferred==Direction.UP?AttachFace.CEILING:AttachFace.FLOOR).setValue(FACING,context.getHorizontalDirection()):defaultBlockState().setValue(FACING,preferred);
    }
    @Override protected boolean isSignalSource(BlockState state){return state.getValue(POWERED);}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return state.getValue(POWERED)&&side!=getTargetDirection(state).getOpposite()?15:0;}
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=state.getValue(FACING).getOpposite();}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){level.setBlock(pos,state.setValue(POWERED,false),Block.UPDATE_CLIENTS);level.updateNeighborsAt(pos,this);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(LoadedLevelAccess.blockEntity(level,pos) instanceof SmartObserverBlockEntity observer)observer.resetInventoryTracking();}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){var next=getRotatedBlockState(state,context.getClickedFace());if(!context.getLevel().isClientSide())context.getLevel().setBlockAndUpdate(context.getClickedPos(),next);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.interact(level,player,hand,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    public void onFunnelTransfer(Level level,BlockPos pos,ItemStack stack){
        if(level.isClientSide()||stack.isEmpty())return;
        for(var side:Direction.values()){var observerPos=pos.relative(side);var state=LoadedLevelAccess.blockState(level,observerPos);if(!state.is(this)||getTargetDirection(state)!=side.getOpposite())continue;if(LoadedLevelAccess.blockEntity(level,observerPos) instanceof SmartObserverBlockEntity observer&&observer.filtering.test(stack))observer.activate(4);}
    }
}
