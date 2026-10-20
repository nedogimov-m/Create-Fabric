package com.simibubi.create.foundation.data.recipe;

import com.google.common.collect.MapMaker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;

/** Actual native reload contents, scoped to the exact pending lookup that uses them. */
public final class PendingItemTagContents {
    // weakKeys uses identity equivalence. Native item holders never retain the pending lookup.
    private static final ConcurrentMap<HolderGetter<?>, Map<TagKey<?>, List<? extends Holder<?>>>> PENDING =
        new MapMaker().weakKeys().makeMap();

    public static <T> void capture(TagLoader.LoadResult<T> loaded, Registry.PendingTags<T> pending) {
        if (!loaded.key().equals(Registries.ITEM)) return;
        Map<TagKey<?>, List<? extends Holder<?>>> contents = new HashMap<>();
        loaded.tags().forEach((tag, holders) -> contents.put(tag, List.copyOf(holders)));
        PENDING.put(pending.lookup(), Map.copyOf(contents));
    }

    public static boolean isEmpty(HolderGetter<Item> lookup, TagKey<Item> tag) {
        var contents = PENDING.get(lookup);
        if (contents != null) return contents.getOrDefault(tag, List.of()).isEmpty();
        // Construction lookups may expose unbound sentinels. Never dereference them or admit a recipe.
        return lookup.get(tag).map(holders -> !holders.isBound() || holders.size() == 0).orElse(true);
    }

    /** New pending Named holders can be unbound; older ones can still expose yesterday's contents. */
    public static List<Item> items(HolderGetter<Item> lookup, TagKey<Item> tag) {
        var contents = PENDING.get(lookup);
        if (contents != null)
            return contents.getOrDefault(tag, List.of()).stream().map(Holder::value).map(Item.class::cast).toList();
        return lookup.get(tag).filter(holders -> holders.isBound())
            .map(holders -> holders.stream().map(Holder::value).toList()).orElse(List.of());
    }

    private PendingItemTagContents() {}
}
