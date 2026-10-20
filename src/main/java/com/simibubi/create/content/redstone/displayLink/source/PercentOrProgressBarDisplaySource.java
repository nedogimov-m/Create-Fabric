package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public abstract class PercentOrProgressBarDisplaySource extends NumericSingleLineDisplaySource {
    protected abstract Float getProgress(DisplayLinkContext context);
    protected abstract boolean progressBarActive(DisplayLinkContext context);
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){var progress=getProgress(context);if(progress==null||!Float.isFinite(progress))return EMPTY_LINE;if(!progressBarActive(context))return formatNumeric(context,progress);var label=context.sourceConfig().getStringOr("Label","");int length=Math.min(stats.maxColumns()-(label.isEmpty()?0:label.length()+1),128);if(context.getTargetBlockEntity()instanceof SignBlockEntity)length=(int)(length*6f/9f);if(context.getTargetBlockEntity()instanceof FlapDisplayBlockEntity)length=(int)(length*FlapDisplaySection.MONOSPACE/FlapDisplaySection.WIDE_MONOSPACE);if(length<1)return EMPTY_LINE;int filled=(int)(Math.clamp(progress,0,1)*length);return Component.literal("█".repeat(filled)+"▒".repeat(length-filled));}
    protected MutableComponent formatNumeric(DisplayLinkContext context,Float progress){return Component.literal(Math.clamp((int)(progress*100),0,100)+"%");}
    @Override protected String getFlapDisplayLayoutName(DisplayLinkContext context){return progressBarActive(context)?"Progress":super.getFlapDisplayLayoutName(context);}
    @Override protected FlapDisplaySection createSectionForValue(DisplayLinkContext context,int size){return progressBarActive(context)?new FlapDisplaySection(size*FlapDisplaySection.MONOSPACE,"pixel",false,false).wideFlaps():super.createSectionForValue(context,size);}
}
