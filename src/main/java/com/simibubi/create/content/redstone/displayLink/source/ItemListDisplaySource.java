package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.*;
import java.util.stream.Stream;

public final class ItemListDisplaySource extends ValueListDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&ObserverDisplayAccess.available(context);}

    @Override protected Stream<Entry> provideEntries(DisplayLinkContext context,int maxRows){return ObserverDisplayAccess.items(context,false).entrySet().stream().sorted(Map.Entry.<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant,Long>comparingByValue().reversed()).limit(maxRows).map(entry->new Entry(ObserverDisplayAccess.count(entry.getValue()),entry.getKey().toStack().getHoverName().copy()));}
    @Override protected boolean valueFirst(){return true;}
    @Override protected String getTranslationKey(){return "list_items";}
}
