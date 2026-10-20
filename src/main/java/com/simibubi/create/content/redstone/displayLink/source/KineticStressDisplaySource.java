package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import java.util.*;
import net.minecraft.network.chat.MutableComponent;

public final class KineticStressDisplaySource extends PercentOrProgressBarDisplaySource {
    private int mode(DisplayLinkContext context){return context.sourceConfig().getIntOr("Mode",0);}
    @Override protected Float getProgress(DisplayLinkContext context){if(!(context.getSourceBlockEntity()instanceof StressGaugeBlockEntity gauge))return null;double capacity=gauge.getNetworkCapacity(),stress=gauge.getNetworkStress();if(capacity==0)return 0f;return (float)switch(mode(context)){case 0,1->stress/capacity;case 2->stress;case 3->capacity;case 4->capacity-stress;default->0;};}
    @Override protected MutableComponent formatNumeric(DisplayLinkContext context,Float value){if(mode(context)==1)return super.formatNumeric(context,value);var text=DisplayFormatting.number(value);if(context.getTargetBlockEntity()instanceof FlapDisplayBlockEntity)text.append(" ");return text.append(DisplayFormatting.translate("generic.unit.stress"));}
    @Override protected boolean progressBarActive(DisplayLinkContext context){return mode(context)==0;}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "kinetic_stress";}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){var options=new ArrayList<>(super.configuration(context));options.add(DisplaySourceConfiguration.choice("Mode","create.display_source.kinetic_stress.progress_bar","create.display_source.kinetic_stress.percent","create.display_source.kinetic_stress.current","create.display_source.kinetic_stress.max","create.display_source.kinetic_stress.remaining"));return options;}
}
