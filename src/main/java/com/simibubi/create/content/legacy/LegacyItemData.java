package com.simibubi.create.content.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;

/** Actual per-entity persistent source flags; a compound is never used as a material inventory. */
public interface LegacyItemData {
 CompoundTag create$legacyData();
 void create$legacySyncStack();
 static CompoundTag of(ItemEntity entity){return ((LegacyItemData)entity).create$legacyData();}
}
