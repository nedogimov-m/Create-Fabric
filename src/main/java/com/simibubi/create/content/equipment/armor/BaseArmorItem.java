package com.simibubi.create.content.equipment.armor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.*;

/** Native attributes, repair, enchantability, equip/swap/dispense and resource-pack equipment assets. */
public class BaseArmorItem extends Item {
    protected final Identifier textureLoc;
    protected final ArmorMaterial material;
    protected final ArmorType type;
    public BaseArmorItem(ArmorMaterial material,ArmorType type,Properties properties,Identifier textureLoc){this(material,type,properties,textureLoc,material.createAttributes(type));}
    protected BaseArmorItem(ArmorMaterial material,ArmorType type,Properties properties,Identifier textureLoc,ItemAttributeModifiers attributes){
        super(properties.stacksTo(1).attributes(attributes).enchantable(material.enchantmentValue()).repairable(material.repairIngredient()).component(DataComponents.EQUIPPABLE,Equippable.builder(type.getSlot()).setEquipSound(material.equipSound()).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID,textureLoc)).build()));
        this.material=material;this.type=type;this.textureLoc=textureLoc;
    }
    public Identifier getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot){return Identifier.fromNamespaceAndPath(textureLoc.getNamespace(),"textures/models/armor/"+textureLoc.getPath()+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png");}
    public ArmorType armorType(){return type;}
    public ArmorMaterial armorMaterial(){return material;}
}
