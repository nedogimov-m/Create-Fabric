package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.trains.display.*;
import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.material.Fluid;

public final class FluidListDisplaySource extends ValueListDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&ObserverDisplayAccess.available(context);}

    @Override protected Stream<Entry> provideEntries(DisplayLinkContext context,int maxRows){var amounts=new LinkedHashMap<Fluid,Long>();var names=new HashMap<Fluid,FluidVariant>();ObserverDisplayAccess.fluids(context).forEach((variant,amount)->{amounts.merge(variant.getFluid(),amount,ObserverDisplayAccess::sum);names.putIfAbsent(variant.getFluid(),variant);});return amounts.entrySet().stream().sorted(Map.Entry.<Fluid,Long>comparingByValue().reversed()).limit(maxRows).map(entry->new Entry(ObserverDisplayAccess.count(DisplayFormatting.millibuckets(entry.getValue())),FluidVariantAttributes.getName(names.get(entry.getKey())).copy()));}
    @Override protected List<MutableComponent> createComponentsFromEntry(DisplayLinkContext context,Entry entry){boolean shortened=shortenNumbers(context)&&entry.value()>=1000;var value=Component.literal(shortened?String.format(Locale.ROOT,"%.1f",entry.value()/1000d):Integer.toString(entry.value()));var unit=DisplayFormatting.translate(shortened?"generic.unit.buckets":"generic.unit.millibuckets");return List.of(value,unit,entry.name().copy().append(" "));}
    @Override public void loadFlapDisplayLayout(DisplayLinkContext context,FlapDisplayBlockEntity board,FlapDisplayLayout layout){int max=context.flapDisplayContext instanceof int[] values?values[0]:0,length=DisplayFormatting.fluid(max,shortenNumbers(context)).length();var key="FluidList_"+length;if(layout.isLayout(key))return;int capacity=board.getMaxCharCount(1),numberLength=Math.min(capacity,Math.max(3,length-2)),nameLength=Math.max(capacity-numberLength-2,0);layout.configure(key,List.of(new FlapDisplaySection(FlapDisplaySection.MONOSPACE*numberLength,"number",false,false).rightAligned(),new FlapDisplaySection(FlapDisplaySection.MONOSPACE*2,"fluid_units",true,true),new FlapDisplaySection(FlapDisplaySection.MONOSPACE*nameLength,"alphabet",false,false)));}
    @Override protected boolean valueFirst(){return false;}
    @Override protected String getTranslationKey(){return "list_fluids";}
}
