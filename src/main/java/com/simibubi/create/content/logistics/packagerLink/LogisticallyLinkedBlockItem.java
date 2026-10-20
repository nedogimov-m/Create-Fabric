package com.simibubi.create.content.logistics.packagerLink;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class LogisticallyLinkedBlockItem extends BlockItem {
    public LogisticallyLinkedBlockItem(Block block,Properties properties) { super(block,properties); }
    public static UUID networkFromStack(ItemStack stack) { var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);return data==null?null:data.copyTagWithoutId().read("Freq",UUIDUtil.CODEC).orElse(null); }
    public static boolean isTuned(ItemStack stack) { return networkFromStack(stack)!=null; }
    @Override public boolean isFoil(ItemStack stack) { return isTuned(stack); }
    public static void assignFrequency(ItemStack stack,Player player,UUID id) {
        if(!(stack.getItem() instanceof LogisticallyLinkedBlockItem))return;var tag=new net.minecraft.nbt.CompoundTag();tag.store("Freq",UUIDUtil.CODEC,id);var type=stack.getItem() instanceof com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem?com.simibubi.create.AllBlockEntityTypes.FACTORY_PANEL:stack.getItem() instanceof com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockItem?com.simibubi.create.AllBlockEntityTypes.REDSTONE_REQUESTER:stack.getItem() instanceof BlockItem blockItem&&blockItem.getBlock() instanceof com.simibubi.create.content.logistics.stockTicker.StockTickerBlock?com.simibubi.create.AllBlockEntityTypes.STOCK_TICKER:com.simibubi.create.AllBlockEntityTypes.PACKAGER_LINK;stack.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(type,tag));
        if(player!=null)player.sendOverlayMessage(Component.translatable("create.logistically_linked.tuned"));
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) { var stack=player.getItemInHand(hand);if(!isTuned(stack))return super.use(level,player,hand);if(!level.isClientSide()){stack.remove(DataComponents.BLOCK_ENTITY_DATA);player.sendOverlayMessage(Component.translatable("create.logistically_linked.cleared"));}return InteractionResult.SUCCESS; }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();if(player==null||!player.mayBuild()||!context.getLevel().mayInteract(player,context.getClickedPos()))return InteractionResult.PASS;
        if(!player.isShiftKeyDown()){
            var link=BlockEntityBehaviour.get(context.getLevel(),context.getClickedPos(),LogisticallyLinkedBehaviour.TYPE);
            if(link!=null){if(!context.getLevel().isClientSide()&&link.mayInteractMessage(player))assignFrequency(context.getItemInHand(),player,link.freqId);return InteractionResult.SUCCESS;}
        }
        boolean tuned=isTuned(context.getItemInHand());var result=super.useOn(context);
        if(result.consumesAction()&&!context.getLevel().isClientSide())player.sendOverlayMessage(Component.translatable(tuned?"create.logistically_linked.connected":"create.logistically_linked.new_network_started"));return result;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) { if(isTuned(stack)){lines.accept(Component.translatable("create.logistically_linked.tooltip").withStyle(ChatFormatting.GOLD));lines.accept(Component.translatable("create.logistically_linked.tooltip_clear").withStyle(ChatFormatting.GRAY));} }
}
