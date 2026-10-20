package com.simibubi.create.content.logistics.redstoneRequester;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public final class RedstoneRequesterBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public RedstoneRequesterBlock(Properties properties) { super(properties.strength(1.5f,6).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().isRedstoneConductor((s,l,p)->false));registerDefaultState(defaultBlockState().setValue(POWERED,false).setValue(AXIS,Direction.Axis.X)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(POWERED,AXIS); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(AXIS,context.getHorizontalDirection().getAxis()).setValue(POWERED,LoadedLevelAccess.getBestNeighborSignal(context.getLevel(),context.getClickedPos())>0); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new RedstoneRequesterBlockEntity(pos,state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.REDSTONE_REQUESTER,(l,p,s,requester)->requester.tick()); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved) {
        if(level.isClientSide())return;boolean powered=LoadedLevelAccess.getBestNeighborSignal(level,pos)>0;
        if(state.getValue(POWERED)!=powered)level.setBlock(pos,state.setValue(POWERED,powered),Block.UPDATE_CLIENTS);
        if(LoadedLevelAccess.blockEntity(level,pos) instanceof RedstoneRequesterBlockEntity requester)requester.onRedstonePowerChanged();
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide()||!(LoadedLevelAccess.blockEntity(level,pos) instanceof RedstoneRequesterBlockEntity requester))return;
        var data=AutoRequestData.readFromItem(level,placer instanceof Player player?player:null,pos,stack);
        if(data!=null){requester.encodedRequest=data.encodedRequest();requester.encodedTargetAdress=data.encodedTargetAddress();requester.notifyUpdate();}
    }
    public static void programRequester(Player player,com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity source,com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts order,String address) {
        var stack=player.getMainHandItem();if(!stack.is(AllBlocks.REDSTONE_REQUESTER.asItem())&&!(stack.getItem() instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlockItem))return;
        new AutoRequestData(order,address,source.getBlockPos(),player.level().dimension().identifier().toString(),false).writeToItem(BlockPos.ZERO,stack);
        if(stack.is(AllBlocks.REDSTONE_REQUESTER.asItem()))com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem.assignFrequency(stack,null,source.behaviour.freqId);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.is(AllItems.WRENCH)||stack.getItem() instanceof com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem)return InteractionResult.PASS;
        return useWithoutItem(state,level,pos,player,hit);
    }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction direction) { return LoadedLevelAccess.blockEntity(level,pos) instanceof RedstoneRequesterBlockEntity requester&&requester.lastRequestSucceeded?15:0; }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(player.isShiftKeyDown()||player instanceof net.fabricmc.fabric.api.entity.FakePlayer||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof RedstoneRequesterBlockEntity requester&&requester.behaviour.mayInteractMessage(player))RedstoneRequesterMenu.open(player,requester);
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(AXIS))).getAxis()); }
}
