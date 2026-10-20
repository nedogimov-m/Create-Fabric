package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.*;

public final class ItemCountDisplaySource extends NumericSingleLineDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&ObserverDisplayAccess.available(context);}

    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){long count=0;for(long value:ObserverDisplayAccess.items(context,true).values())count=ObserverDisplayAccess.sum(count,value);return Component.literal(Long.toString(count));}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "count_items";}
}
