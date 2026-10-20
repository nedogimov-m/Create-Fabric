package com.simibubi.create.content.logistics.redstoneRequester;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public final class RedstoneRequesterBlockItem extends LogisticallyLinkedBlockItem {
    public RedstoneRequesterBlockItem(Block block,Properties properties) { super(block,properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        if(!isTuned(stack))return;var data=stack.get(AllDataComponents.AUTO_REQUEST_DATA);if(data==null){super.appendHoverText(stack,context,display,lines,flag);return;}
        lines.accept(Component.translatable("create.logistically_linked.tooltip").withStyle(ChatFormatting.GOLD));
        for(var entry:data.encodedRequest().stacks())lines.accept(entry.stack.getHoverName().copy().append(" x"+entry.count).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("create.logistically_linked.tooltip_clear").withStyle(ChatFormatting.DARK_GRAY));
    }
}
