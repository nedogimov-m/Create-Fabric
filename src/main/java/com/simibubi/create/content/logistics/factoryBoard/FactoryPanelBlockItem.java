package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public final class FactoryPanelBlockItem extends LogisticallyLinkedBlockItem {
    public FactoryPanelBlockItem(Block block,Properties properties) { super(block,properties); }
    public static ItemStack fixCtrlCopiedStack(ItemStack stack) {
        if(networkFromStack(stack)!=null)return stack;var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(data==null)return stack;var tag=data.copyTagWithoutId();java.util.UUID frequency=null;for(var slot:FactoryPanelBlock.PanelSlot.values())frequency=tag.getCompoundOrEmpty(slot.getSerializedName()).read("Freq",UUIDUtil.CODEC).orElse(frequency);if(frequency!=null){var cleaned=new net.minecraft.nbt.CompoundTag();cleaned.store("Freq",UUIDUtil.CODEC,frequency);stack.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(AllBlockEntityTypes.FACTORY_PANEL,cleaned));}return stack;
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        var stack=fixCtrlCopiedStack(context.getItemInHand());var frequency=networkFromStack(stack);if(frequency==null){if(context.getPlayer()!=null)context.getPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.factory_panel.tune_before_placing"));return InteractionResult.FAIL;}
        var pos=context.getClickedPos();var level=context.getLevel();var previous=LoadedLevelAccess.blockEntity(level,pos);var state=level.getBlockState(pos);
        if(previous instanceof FactoryPanelBlockEntity gauge){var slot=FactoryPanelBlock.getTargetedSlot(pos,state,context.getClickLocation());if(gauge.panels.get(slot).active)return InteractionResult.FAIL;if(!level.isClientSide()&&gauge.addPanel(slot,frequency)&&context.getPlayer()!=null&&!context.getPlayer().isCreative())stack.shrink(1);return InteractionResult.SUCCESS;}
        var safe=new net.minecraft.nbt.CompoundTag();safe.store("Freq",UUIDUtil.CODEC,frequency);stack.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(AllBlockEntityTypes.FACTORY_PANEL,safe));var result=super.place(context);
        if(result.consumesAction()&&LoadedLevelAccess.blockEntity(level,pos) instanceof FactoryPanelBlockEntity gauge){gauge.addPanel(FactoryPanelBlock.getTargetedSlot(pos,gauge.getBlockState(),context.getClickLocation()),frequency);gauge.refreshRestocker();}return result;
    }
}
