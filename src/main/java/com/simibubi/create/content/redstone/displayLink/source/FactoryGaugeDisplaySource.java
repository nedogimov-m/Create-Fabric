package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class FactoryGaugeDisplaySource extends ValueListDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&context.blockEntity().factoryPanelSupport.getLinkedPanels().stream().allMatch(pos->com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(context.level(),pos.pos()));}
    @Override protected Stream<Entry> provideEntries(DisplayLinkContext context,int maxRows){return context.blockEntity().factoryPanelSupport.getLinkedPanels().stream().map(position->{var panel=FactoryPanelBehaviour.at(context.level(),position);if(panel==null)return null;var filter=panel.getFilter();var symbol=panel.demand()==0?" ":panel.satisfied?"✔":panel.getPromised()!=0?"↑":"▪";return new Entry(panel.getLevelInStorage(),Component.literal(symbol+" ").withStyle(style->style.withColor(panel.getIngredientStatusColor())).append(filter.getHoverName().plainCopy().withStyle(ChatFormatting.RESET)));}).filter(Objects::nonNull).limit(maxRows);}
    @Override protected boolean valueFirst(){return true;}
    @Override protected String getTranslationKey(){return "gauge_status";}
}
