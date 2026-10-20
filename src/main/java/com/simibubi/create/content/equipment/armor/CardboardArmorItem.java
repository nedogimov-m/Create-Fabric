package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.item.CreateCookingFuels;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.ArmorType;

public class CardboardArmorItem extends BaseArmorItem {
    public CardboardArmorItem(ArmorType type,Properties properties){super(AllArmorMaterials.CARDBOARD,type,properties.durability(type.getDurability(4)).component(DataComponents.COOKING_FUEL,CreateCookingFuels.component("cardboard")),Create.asResource("cardboard"));}
}
