package com.simibubi.create.content.processing.sequenced;

import com.simibubi.create.AllDataComponents;
import net.minecraft.world.item.*;

public final class SequencedAssemblyItem extends Item {
    public SequencedAssemblyItem(Properties properties){super(properties.stacksTo(1));}
    public float getProgress(ItemStack stack){var progress=stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);return progress==null?0:progress.progress();}
    @Override public boolean isBarVisible(ItemStack stack){return true;}
    @Override public int getBarWidth(ItemStack stack){return Math.round(getProgress(stack)*13);}
    @Override public int getBarColor(ItemStack stack){float progress=getProgress(stack);int from=0xFFC074,to=0x46FFE0,result=0;for(int shift:new int[]{0,8,16})result|=(int)(((from>>shift)&255)*(1-progress)+((to>>shift)&255)*progress)<<shift;return result;}
}
