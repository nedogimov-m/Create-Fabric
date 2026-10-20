package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.MutableComponent;

/** Actual10-sample source rate pool and second/minute/hour interval, fed only by committed tunnel transfers. */
public final class ItemThroughputDisplaySource extends AccumulatedItemCountDisplaySource {
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){var config=context.sourceConfig();if(config.getBooleanOr("Inactive",false))return ZERO.copy();double interval=20*Math.pow(60,Math.clamp(config.getIntOr("Interval",0),0,2)),rate=config.getFloatOr("Rate",0)*interval;if(rate>0){long elapsed=context.level().getGameTime()-config.getLongOr("LastReceived",0);if(elapsed>0&&elapsed>config.getIntOr("LastReceivedAmount",0)/rate*2)config.putBoolean("Inactive",true);}return DisplayFormatting.number(rate);}
    @Override public void itemReceived(DisplayLinkBlockEntity link,int amount){
        if(amount<=0||link.getLevel()==null||link.getBlockState().getOptionalValue(DisplayLinkBlock.POWERED).orElse(true))return;var config=link.getSourceConfig();long now=link.getLevel().getGameTime();if(!config.contains("LastReceived")){config.putLong("LastReceived",now);link.notifyUpdate();return;}long previous=config.getLongOr("LastReceived",now);var rates=config.getListOrEmpty("PrevRates");if(rates.size()!=10){rates=new ListTag();for(int i=0;i<10;i++)rates.add(FloatTag.valueOf(-1));}int index=Math.floorMod(config.getIntOr("Index",0),10);rates.set(index,FloatTag.valueOf((float)(amount/(double)Math.max(1,now-previous))));float sum=0;int valid=0;for(int i=0;i<10;i++){float value=rates.get(i).asFloat().orElse(-1f);if(value>=0&&Float.isFinite(value)){sum+=value;valid++;}}config.remove("Rate");if(valid>0)config.putFloat("Rate",sum/valid);config.remove("Inactive");config.putInt("LastReceivedAmount",amount);config.putLong("LastReceived",now);config.putInt("Index",index+1);config.put("PrevRates",rates);link.updateGatheredData();
    }
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){var options=new ArrayList<>(super.configuration(context));options.add(DisplaySourceConfiguration.choice("Interval","create.display_source.item_throughput.interval.second","create.display_source.item_throughput.interval.minute","create.display_source.item_throughput.interval.hour"));return options;}
    @Override protected String getTranslationKey(){return "item_throughput";}
}
