package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import java.util.*;

/** Actual measured threshold range; unknown targets defer display delivery. */
public final class FillLevelDisplaySource extends PercentOrProgressBarDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&context.getSourceBlockEntity()instanceof ThresholdSwitchBlockEntity sensor&&sensor.hasValidSample();}
    @Override protected Float getProgress(DisplayLinkContext context){if(!(context.getSourceBlockEntity()instanceof ThresholdSwitchBlockEntity sensor)||!sensor.hasValidSample())return null;double range=sensor.currentMaxLevel-(double)sensor.currentMinLevel;if(range<=0)return sensor.currentLevel>=sensor.currentMaxLevel?1f:0f;return (float)Math.clamp((sensor.currentLevel-(double)sensor.currentMinLevel)/range,0,1);}
    @Override protected boolean progressBarActive(DisplayLinkContext context){return context.sourceConfig().getIntOr("Mode",0)!=0;}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){var options=new ArrayList<>(super.configuration(context));options.add(DisplaySourceConfiguration.choice("Mode","create.display_source.fill_level.percent","create.display_source.fill_level.progress_bar"));return List.copyOf(options);}
    @Override protected String getTranslationKey(){return "fill_level";}
}
