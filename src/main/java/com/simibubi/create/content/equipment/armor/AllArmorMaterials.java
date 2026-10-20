package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.*;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.equipment.*;

/** Pinned defense/enchantability/repair values expressed as real native armor materials. */
public final class AllArmorMaterials {
    public static final ArmorMaterial COPPER=new ArmorMaterial(7,Map.of(ArmorType.HELMET,2,ArmorType.CHESTPLATE,4,ArmorType.LEGGINGS,3,ArmorType.BOOTS,1,ArmorType.BODY,4),7,Holder.direct(AllSoundEvents.COPPER_ARMOR_EQUIP),0,0,TagKey.create(Registries.ITEM,Create.asResource("repairs/copper_armor")),ResourceKey.create(EquipmentAssets.ROOT_ID,Create.asResource("copper_diving")));
    public static final ArmorMaterial CARDBOARD=new ArmorMaterial(4,Map.of(ArmorType.HELMET,1,ArmorType.CHESTPLATE,1,ArmorType.LEGGINGS,1,ArmorType.BOOTS,1,ArmorType.BODY,2),4,SoundEvents.ARMOR_EQUIP_LEATHER,0,0,TagKey.create(Registries.ITEM,Create.asResource("repairs/cardboard_armor")),ResourceKey.create(EquipmentAssets.ROOT_ID,Create.asResource("cardboard")));
    private AllArmorMaterials(){}
}
