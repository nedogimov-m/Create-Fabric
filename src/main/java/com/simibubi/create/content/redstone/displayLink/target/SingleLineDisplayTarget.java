package com.simibubi.create.content.redstone.displayLink.target;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.List;
import net.minecraft.network.chat.*;

public abstract class SingleLineDisplayTarget extends DisplayTarget {
    @Override public final void acceptText(int line,List<MutableComponent> text,DisplayLinkContext context){if(!text.isEmpty())acceptLine(text.getFirst(),context);}
    protected abstract void acceptLine(MutableComponent text,DisplayLinkContext context);
    protected abstract int getWidth(DisplayLinkContext context);
    @Override public final DisplayTargetStats provideStats(DisplayLinkContext context){return new DisplayTargetStats(1,getWidth(context),this);}
    @Override public Component getLineOptionText(int line){return Component.translatable("create.display_target.single_line");}
}
