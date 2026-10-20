package com.simibubi.create.content.redstone.thresholdSwitch;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.*;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings;
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

/** Source stockpile_switch identity, directed observation and delayed output, using native block models. */
public final class ThresholdSwitchBlock extends DirectedDirectionalBlock {
    public static final IntegerProperty LEVEL=IntegerProperty.create("level",0,5);
    public static final FilteredDetectorFilterSlot FILTER_SLOT=new FilteredDetectorFilterSlot(true);
    public ThresholdSwitchBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(LEVEL,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(LEVEL);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ThresholdSwitchBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.THRESHOLD_SWITCH,ThresholdSwitchBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction target=null;var level=context.getLevel();
        for(var side:context.getNearestLookingDirections()){var pos=context.getClickedPos().relative(side);if(!level.isLoaded(pos))continue;if(LoadedLevelAccess.blockEntity(level,pos)!=null&&(SmartObserverBlockEntity.findItems(level,pos)!=null||SmartObserverBlockEntity.findFluids(level,pos)!=null)){target=side;break;}}
        if(target==null){var facing=context.getNearestLookingDirection();target=context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown()?facing:facing.getOpposite();}
        return target.getAxis().isVertical()?defaultBlockState().setValue(TARGET,target==Direction.UP?AttachFace.CEILING:AttachFace.FLOOR).setValue(FACING,context.getHorizontalDirection()):defaultBlockState().setValue(FACING,target);
    }
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=getTargetDirection(state).getOpposite()&&level.getBlockEntity(pos)instanceof ThresholdSwitchBlockEntity sensor&&sensor.isPowered()?15:0;}
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null&&side.getOpposite()!=getTargetDirection(state);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){
        // Native LevelChunk publishes the captured placement state to its owner after this callback.
        // A recursive visual LEVEL write here would leave that owner behind the actual section state.
        if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos)instanceof ThresholdSwitchBlockEntity sensor)sensor.resetInventoryTracking();
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(LoadedLevelAccess.blockEntity(level,pos)instanceof ThresholdSwitchBlockEntity sensor)sensor.updatePowerAfterDelay();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(LoadedLevelAccess.blockEntity(level,pos)instanceof ThresholdSwitchBlockEntity sensor)sensor.resetInventoryTracking();}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(AllItems.WRENCH))return InteractionResult.PASS;
        var filter=FilteringSettings.interact(level,player,hand,hit);if(filter.consumesAction())return filter;
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos)||!(LoadedLevelAccess.blockEntity(level,pos)instanceof ThresholdSwitchBlockEntity sensor))return InteractionResult.PASS;
        if(!level.isClientSide())ThresholdSwitchMenu.open(player,sensor);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){if(!context.getLevel().isClientSide()){context.getLevel().setBlockAndUpdate(context.getClickedPos(),getRotatedBlockState(state,context.getClickedFace()));if(LoadedLevelAccess.blockEntity(context.getLevel(),context.getClickedPos())instanceof ThresholdSwitchBlockEntity sensor){sensor.resetInventoryTracking();sensor.updateCurrentLevel();}}return InteractionResult.SUCCESS;}
}
