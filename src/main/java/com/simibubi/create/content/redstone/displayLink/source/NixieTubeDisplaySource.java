package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.*;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import net.minecraft.network.chat.MutableComponent;

public final class NixieTubeDisplaySource extends SingleLineDisplaySource {
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){if(!(context.getSourceBlockEntity()instanceof NixieTubeBlockEntity nixie))return EMPTY_LINE;var text=nixie.getFullText();try{Integer.parseInt(text.getString());context.flapDisplayContext=Boolean.TRUE;}catch(NumberFormatException ignored){}return text;}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return !(context.blockEntity().activeTarget instanceof NixieTubeDisplayTarget);}
    @Override protected String getFlapDisplayLayoutName(DisplayLinkContext context){return context.flapDisplayContext==Boolean.TRUE?"Number":super.getFlapDisplayLayoutName(context);}
    @Override protected FlapDisplaySection createSectionForValue(DisplayLinkContext context,int size){return context.flapDisplayContext==Boolean.TRUE?new FlapDisplaySection(size*FlapDisplaySection.MONOSPACE,"numeric",false,false):super.createSectionForValue(context,size);}
    @Override protected String getTranslationKey(){return "nixie_tube";}
}
