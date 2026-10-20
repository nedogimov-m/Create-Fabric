package com.simibubi.create.content.logistics.funnel;

import com.simibubi.create.*;
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
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;

public abstract class AbstractFunnelBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public final boolean brass;
    protected AbstractFunnelBlock(Properties properties,boolean brass){super(properties);this.brass=brass;registerDefaultState(defaultBlockState().setValue(POWERED,false).setValue(WATERLOGGED,false));}
    protected abstract Direction getFacing(BlockState state);
    public static Direction getFunnelFacing(BlockState state){return state.getBlock() instanceof AbstractFunnelBlock funnel?funnel.getFacing(state):null;}
    public static boolean isFunnel(BlockState state){return state.getBlock() instanceof AbstractFunnelBlock;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(POWERED,WATERLOGGED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return FunnelBlockEntity.create(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.FUNNEL,FunnelBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(POWERED,context.getLevel().hasNeighborSignal(context.getClickedPos())).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(!level.isClientSide())level.scheduleTick(pos,this,1);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){boolean powered=level.hasNeighborSignal(pos);if(powered!=state.getValue(POWERED))level.setBlock(pos,state.setValue(POWERED,powered),Block.UPDATE_CLIENTS);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return !(LoadedLevelAccess.blockState(level,pos.relative(getFacing(state).getOpposite())).getBlock() instanceof AbstractFunnelBlock);}
    public abstract InteractionResult onWrenched(BlockState state,UseOnContext context);
    public static ItemStack tryInsert(Level level,BlockPos pos,ItemStack stack,boolean simulate){if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof FunnelBlockEntity funnel))return stack.copy();return funnel.insert(stack,simulate);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos)||stack.is(AllItems.WRENCH))return InteractionResult.PASS;
        if(brass&&FunnelFilterSlotPositioning.testHit(state,pos,hit))return com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.interact(level,player,hand,hit);
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
}
