package com.simibubi.create.content.logistics.packager;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
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

public class PackagerBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,LINKED=BooleanProperty.create("linked");
    public PackagerBlock(Properties properties){super(properties.strength(3,6).requiresCorrectToolForDrops().noOcclusion().isRedstoneConductor((state,level,pos)->false).mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_BLUE).sound(SoundType.NETHERITE_BLOCK));var state=defaultBlockState().setValue(FACING,Direction.NORTH).setValue(POWERED,false);if(state.hasProperty(LINKED))state=state.setValue(LINKED,false);registerDefaultState(state);}
    @Override public void setPlacedBy(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,LINKED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PackagerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.PACKAGER,PackagerBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction preferred=null;var level=context.getLevel();for(var face:context.getNearestLookingDirections()){var pos=context.getClickedPos().relative(face);if(!level.isLoaded(pos)||LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerBlockEntity)continue;if(com.simibubi.create.content.logistics.funnel.FunnelTransfer.find(level,pos,null)!=null){preferred=face.getOpposite();break;}}
        if(preferred==null){var facing=context.getNearestLookingDirection();preferred=context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown()?facing:facing.getOpposite();}return defaultBlockState().setValue(FACING,preferred).setValue(POWERED,level.hasNeighborSignal(context.getClickedPos()));
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.getItem() instanceof com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem)return InteractionResult.PASS;
        if(stack.is(AllItems.WRENCH)||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos)||!(LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerBlockEntity packager))return InteractionResult.PASS;
        if(level.isClientSide()||packager.animationTicks>0)return InteractionResult.SUCCESS;
        if(!packager.heldBox.isEmpty()){ItemStack taken;try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){taken=packager.heldBox.copy();if(packager.inventory.extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(taken),1,tx)!=1)return InteractionResult.SUCCESS;tx.commit();}player.getInventory().placeItemBackInInventory(taken,net.minecraft.util.Prediction.SERVER_ONLY);level.playSound(null,pos,net.minecraft.sounds.SoundEvents.ITEM_PICKUP,net.minecraft.sounds.SoundSource.PLAYERS,.2f,1);return InteractionResult.SUCCESS;}
        if(PackageItem.isPackage(stack)&&packager.unwrapBox(stack,false)){stack.shrink(1);level.playSound(null,pos,net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,net.minecraft.sounds.SoundSource.BLOCKS,.25f,1);}return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){
        if(level.isClientSide())return;if(LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerBlockEntity packager){packager.targetChanged();boolean powered=level.hasNeighborSignal(pos);if(powered==state.getValue(POWERED))return;level.setBlock(pos,state.setValue(POWERED,powered),Block.UPDATE_CLIENTS);if(powered)packager.activate();}
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerBlockEntity packager&&(!packager.heldBox.isEmpty()||packager.animationTicks>0)?15:0;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
