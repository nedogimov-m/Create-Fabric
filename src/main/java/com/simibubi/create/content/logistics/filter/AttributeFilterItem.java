package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.AllDataComponents;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class AttributeFilterItem extends Item {
    public AttributeFilterItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player.isShiftKeyDown()||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server){var held=player.getMainHandItem();int slot=player.getInventory().getSelectedSlot();server.openMenu(new ExtendedMenuProvider<FilterMenu.Opening>(){
            @Override public Component getDisplayName(){return held.getHoverName();}
            @Override public FilterMenu.Opening getScreenOpeningData(ServerPlayer ignored){return new FilterMenu.Opening(held.copy(),slot);}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){return new AttributeFilterMenu(id,inventory,held,slot);}
        });}return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        if(FilterItemTooltip.shiftDown())return;
        java.util.List<com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry> attributes=stack.getOrDefault(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES,List.of());if(attributes.isEmpty())return;lines.accept(Component.empty());var mode=stack.getOrDefault(AllDataComponents.ATTRIBUTE_FILTER_WHITELIST_MODE,AttributeFilterWhitelistMode.WHITELIST_DISJ);lines.accept(Component.translatable("create.gui.attribute_filter."+switch(mode){case WHITELIST_DISJ->"allow_list_disjunctive";case WHITELIST_CONJ->"allow_list_conjunctive";case BLACKLIST->"deny_list";}).withStyle(ChatFormatting.GOLD));for(int i=0;i<Math.min(4,attributes.size());i++){var entry=attributes.get(i);lines.accept(Component.literal("- ").append(entry.attribute().format(entry.inverted())).withStyle(ChatFormatting.GRAY));}if(attributes.size()>4)lines.accept(Component.literal("- ...").withStyle(ChatFormatting.DARK_GRAY));
    }
}
