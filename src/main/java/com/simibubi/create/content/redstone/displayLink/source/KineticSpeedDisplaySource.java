package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.*;
import net.minecraft.network.chat.MutableComponent;

public final class KineticSpeedDisplaySource extends NumericSingleLineDisplaySource {
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){if(!(context.getSourceBlockEntity()instanceof SpeedGaugeBlockEntity gauge))return ZERO.copy();float speed=context.sourceConfig().getIntOr("Directional",0)==0?Math.abs(gauge.getSpeed()):gauge.getSpeed();return DisplayFormatting.number(speed).append(" ").append(DisplayFormatting.translate("generic.unit.rpm"));}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "kinetic_speed";}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){var options=new ArrayList<>(super.configuration(context));options.add(DisplaySourceConfiguration.choice("Directional","create.display_source.kinetic_speed.absolute","create.display_source.kinetic_speed.directional"));return options;}
}
