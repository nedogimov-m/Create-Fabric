package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.AllDataComponents;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ListFilterItem extends Item {
    public static final int SLOTS=18;
    public ListFilterItem(Properties properties){super(properties);}
    public static List<ItemStack> getFilterItems(ItemStack filter){var items=NonNullList.withSize(SLOTS,ItemStack.EMPTY);filter.getOrDefault(AllDataComponents.FILTER_ITEMS,ItemContainerContents.EMPTY).copyInto(items);return items;}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player.isShiftKeyDown()||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server){var held=player.getMainHandItem();int selected=player.getInventory().getSelectedSlot();server.openMenu(new ExtendedMenuProvider<FilterMenu.Opening>(){
            @Override public Component getDisplayName(){return held.getHoverName();}
            @Override public FilterMenu.Opening getScreenOpeningData(ServerPlayer ignored){return new FilterMenu.Opening(held.copy(),selected);}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){return new FilterMenu(id,inventory,held,selected);}
        });}return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        if(FilterItemTooltip.shiftDown())return;
        var entries=getFilterItems(stack).stream().filter(item->!item.isEmpty()).toList();if(entries.isEmpty())return;lines.accept(Component.empty());lines.accept(Component.translatable("create.gui.filter."+(stack.getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST,false)?"deny_list":"allow_list")).withStyle(ChatFormatting.GOLD));for(int i=0;i<Math.min(4,entries.size());i++)lines.accept(Component.literal("- ").append(entries.get(i).getHoverName()).withStyle(ChatFormatting.GRAY));if(entries.size()>4)lines.accept(Component.literal("- ...").withStyle(ChatFormatting.DARK_GRAY));
    }
}
