package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.*;
import java.util.List;
import net.minecraft.network.chat.*;

/** Actual source label/text split and character-budgeted native Flap layouts. */
public abstract class SingleLineDisplaySource extends DisplaySource {
    protected abstract MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats);
    protected abstract boolean allowsLabeling(DisplayLinkContext context);
    @Override public List<MutableComponent> provideText(DisplayLinkContext context,DisplayTargetStats stats){var line=provideLine(context,stats);if(line==EMPTY_LINE)return EMPTY;var label=context.sourceConfig().getStringOr("Label","");return List.of(allowsLabeling(context)&&!label.isEmpty()?Component.literal(label+" ").append(line):line);}
    @Override public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context,DisplayTargetStats stats){var label=context.sourceConfig().getStringOr("Label","");return allowsLabeling(context)&&!label.isEmpty()?List.of(List.of(Component.literal(label+" "),provideLine(context,stats))):super.provideFlapDisplayText(context,stats);}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){return allowsLabeling(context)?List.of(DisplaySourceConfiguration.text("Label",256)):List.of();}
    protected String getFlapDisplayLayoutName(DisplayLinkContext context){return "Default";}
    protected FlapDisplaySection createSectionForValue(DisplayLinkContext context,int size){return new FlapDisplaySection(size*FlapDisplaySection.MONOSPACE,"alphabet",false,false);}
    @Override public void loadFlapDisplayLayout(DisplayLinkContext context,FlapDisplayBlockEntity board,FlapDisplayLayout layout){
        var key=getFlapDisplayLayoutName(context);var label=allowsLabeling(context)?context.sourceConfig().getStringOr("Label",""):"";if(label.isEmpty()){if(!layout.isLayout(key))layout.configure(key,List.of(createSectionForValue(context,board.getMaxCharCount())));return;}
        var name=label.length()+"_Labeled_"+key;if(layout.isLayout(name))return;int max=board.getMaxCharCount();var section=new FlapDisplaySection(Math.min(max,label.length()+1)*FlapDisplaySection.MONOSPACE,"alphabet",false,false);layout.configure(name,label.length()+1<max?List.of(section,createSectionForValue(context,max-label.length()-1)):List.of(section));
    }
}
