package com.simibubi.create.foundation.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Source ingredient visibility follows the real bound ore tag, including datapack changes. */
public class TagDependentIngredientItem extends Item {
    private final TagKey<Item> tag;

    public TagDependentIngredientItem(Properties properties, TagKey<Item> tag) {
        super(properties);
        this.tag = tag;
    }

    public boolean shouldHide() {
        for (Holder<Item> ignored : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            return false;
        }
        return true;
    }
}
