package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.*;

public final class FluidAmountDisplaySource extends SingleLineDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&ObserverDisplayAccess.available(context);}

    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){if(ObserverDisplayAccess.observer(context)==null||ObserverDisplayAccess.fluidStorage(context)==null)return EMPTY_LINE;var fluids=ObserverDisplayAccess.fluids(context);long total=0;for(long value:fluids.values())total=ObserverDisplayAccess.sum(total,value);return Component.literal(DisplayFormatting.fluid(DisplayFormatting.millibuckets(total),false));}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "fluid_amount";}
}
