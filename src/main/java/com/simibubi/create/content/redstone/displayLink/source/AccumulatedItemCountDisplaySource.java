package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.*;

public class AccumulatedItemCountDisplaySource extends NumericSingleLineDisplaySource {
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){return Component.literal(Integer.toString(context.sourceConfig().getIntOr("Collected",0)));}
    public void itemReceived(DisplayLinkBlockEntity link,int amount){if(amount<=0||link.getBlockState().getOptionalValue(DisplayLinkBlock.POWERED).orElse(true))return;int previous=link.getSourceConfig().getIntOr("Collected",0);link.getSourceConfig().putInt("Collected",(int)Math.min(Integer.MAX_VALUE,(long)previous+amount));link.updateGatheredData();}
    @Override public int getPassiveRefreshTicks(){return 200;}
    @Override public void onSignalReset(DisplayLinkContext context){context.sourceConfig().remove("Collected");}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "accumulate_items";}
}
