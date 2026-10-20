package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.content.logistics.box.PackageItem;
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

public final class PackageFilterItem extends Item {
    public PackageFilterItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player.isShiftKeyDown()||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server){var held=player.getMainHandItem();int slot=player.getInventory().getSelectedSlot();server.openMenu(new ExtendedMenuProvider<PackageFilterMenu.Opening>(){
            @Override public Component getDisplayName(){return held.getHoverName();}
            @Override public PackageFilterMenu.Opening getScreenOpeningData(ServerPlayer ignored){return new PackageFilterMenu.Opening(held.copy(),slot);}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){return new PackageFilterMenu(id,inventory,held,slot);}
        });}return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){if(FilterItemTooltip.shiftDown())return;String address=PackageItem.getAddress(stack);if(!address.isBlank())lines.accept(Component.literal("→ ").withStyle(ChatFormatting.GRAY).append(Component.literal(address).withStyle(ChatFormatting.GOLD)));}
}
