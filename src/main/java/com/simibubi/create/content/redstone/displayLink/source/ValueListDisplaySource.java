package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/** Actual counted-entry/list layout kernel, with8 entries per real lectern page and source shortened/full formats. */
public abstract class ValueListDisplaySource extends DisplaySource {
    public record Entry(int value,MutableComponent name){}
    protected abstract Stream<Entry> provideEntries(DisplayLinkContext context,int maxRows);
    protected abstract boolean valueFirst();
    protected boolean shortenNumbers(DisplayLinkContext context){return context.sourceConfig().getIntOr("Format",0)==0;}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){return List.of(DisplaySourceConfiguration.choice("Format","create.display_source.value_list.shortened","create.display_source.value_list.full_number"));}
    protected List<MutableComponent> createComponentsFromEntry(DisplayLinkContext context,Entry entry){var name=entry.name().copy().append(" ");if(shortenNumbers(context)){var suffix=entry.value()>=1000000?DisplayFormatting.translate("display_source.value_list.million").append(" "):entry.value()>=1000?DisplayFormatting.translate("display_source.value_list.thousand").append(" "):Component.literal(" ");int value=entry.value()>=1000000?entry.value()/1000000:entry.value()>=1000?entry.value()/1000:entry.value();var number=Component.literal(Integer.toString(value));return valueFirst()?List.of(number,suffix,name):List.of(name,number,suffix);}var value=Component.literal(Integer.toString(entry.value())).append(" ");return valueFirst()?List.of(value,name):List.of(name,value);}
    @Override public List<MutableComponent> provideText(DisplayLinkContext context,DisplayTargetStats stats){boolean book=context.getTargetBlockEntity()instanceof LecternBlockEntity;var rows=provideEntries(context,stats.maxRows()*(book?8:1)).map(entry->{var result=Component.empty();for(var component:createComponentsFromEntry(context,entry))result.append(component);return result;}).toList();if(!book)return rows;var pages=new ArrayList<MutableComponent>();for(int i=0;i<rows.size();i+=8){var page=Component.empty();for(int row=i;row<Math.min(i+8,rows.size());row++){if(row>i)page.append("\n");page.append(rows.get(row));}pages.add(page);}return pages;}
    @Override public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context,DisplayTargetStats stats){var highest=new int[1];context.flapDisplayContext=highest;return provideEntries(context,stats.maxRows()).map(entry->{highest[0]=Math.max(highest[0],entry.value());return createComponentsFromEntry(context,entry);}).toList();}
    @Override public void loadFlapDisplayLayout(DisplayLinkContext context,FlapDisplayBlockEntity board,FlapDisplayLayout layout){boolean first=valueFirst(),shorten=shortenNumbers(context);int highest=context.flapDisplayContext instanceof int[] value?value[0]:0;int format=shorten?0:Math.max(4,Integer.toString(Math.max(0,highest)).length());var key="ValueList_"+first+"_"+format;if(layout.isLayout(key))return;int max=board.getMaxCharCount(1),numberLength=Math.min(max,Math.max(3,format)),nameLength=Math.max(max-numberLength-(shorten?1:0),0);var name=new FlapDisplaySection(FlapDisplaySection.MONOSPACE*nameLength,"alphabet",false,!first);var value=new FlapDisplaySection(FlapDisplaySection.MONOSPACE*numberLength,"number",false,!shorten&&first).rightAligned();if(shorten){var suffix=new FlapDisplaySection(FlapDisplaySection.MONOSPACE,"shortened_numbers",false,first);layout.configure(key,first?List.of(value,suffix,name):List.of(name,value,suffix));}else layout.configure(key,first?List.of(value,name):List.of(name,value));}
}
