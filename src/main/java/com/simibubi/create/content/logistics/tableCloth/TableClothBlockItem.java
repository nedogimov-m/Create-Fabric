package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.AllDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public final class TableClothBlockItem extends BlockItem {
    public TableClothBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public boolean isFoil(ItemStack stack){return stack.has(AllDataComponents.AUTO_REQUEST_DATA);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        var data=stack.get(AllDataComponents.AUTO_REQUEST_DATA);if(data==null)return;lines.accept(Component.translatable("create.table_cloth.shop_configured").withStyle(ChatFormatting.GOLD));
        for(var entry:data.encodedRequest().stacks())lines.accept(entry.stack.getHoverName().copy().append(" x"+entry.count).withStyle(ChatFormatting.GRAY));
    }
}
