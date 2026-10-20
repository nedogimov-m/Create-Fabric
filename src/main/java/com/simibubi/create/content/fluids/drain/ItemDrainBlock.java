package com.simibubi.create.content.fluids.drain;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.*;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class ItemDrainBlock extends BaseEntityBlock {
    public ItemDrainBlock(Properties properties){super(properties);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof ItemDrainBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ItemDrainBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(0,0,0,16,13,16);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.ITEM_DRAIN,ItemDrainBlockEntity::tick);}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain&&!drain.tank.isEmpty()?1+(int)(14*drain.tank.getAmount()/drain.tank.getCapacity()):0;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||stack.is(com.simibubi.create.AllItems.WRENCH)||stack.getItem() instanceof BlockItem&&ContainerItemContext.withConstant(stack).find(FluidStorage.ITEM)==null)return InteractionResult.PASS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain))return InteractionResult.PASS;
        if(drain.emptyPlayerItem(player,hand)||GenericItemEmptying.canItemBeEmptied(level,stack))return InteractionResult.SUCCESS;
        player.getInventory().placeItemBackInInventory(drain.takeHeldItem(),Prediction.SERVER_ONLY);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator())return InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof ItemDrainBlockEntity drain)player.getInventory().placeItemBackInInventory(drain.takeHeldItem(),Prediction.SERVER_ONLY);return InteractionResult.SUCCESS;
    }
}
