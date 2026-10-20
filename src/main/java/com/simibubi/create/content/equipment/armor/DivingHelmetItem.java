package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.EquipmentAdvancements;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.equipment.*;

public class DivingHelmetItem extends BaseArmorItem {
    public static final EquipmentSlot SLOT=EquipmentSlot.HEAD;
    public static final ArmorType TYPE=ArmorType.HELMET;
    public DivingHelmetItem(ArmorMaterial material,Properties properties,Identifier textureLoc){super(material,TYPE,properties,textureLoc,material.createAttributes(TYPE).withModifierAdded(Attributes.SUBMERGED_MINING_SPEED,new AttributeModifier(Create.asResource("diving_helmet_aqua_affinity"),4,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),EquipmentSlotGroup.HEAD));}
    public boolean supportsEnchantment(ItemStack stack,Holder<Enchantment> enchantment){return !enchantment.is(Enchantments.AQUA_AFFINITY);}
    public static boolean isWornBy(Entity entity){return !getWornItem(entity).isEmpty();}
    public static ItemStack getWornItem(Entity entity){if(!(entity instanceof LivingEntity living))return ItemStack.EMPTY;var stack=living.getItemBySlot(SLOT);return stack.getItem() instanceof DivingHelmetItem?stack:ItemStack.EMPTY;}
    public static boolean canProvideAir(LivingEntity entity,boolean lava){
        var helmet=getWornItem(entity);if(helmet.isEmpty()||lava&&!NetheriteDivingHandler.isFireResistant(helmet))return false;var tanks=BacktankUtil.getAllWithAir(entity);
        return !tanks.isEmpty()&&(!lava||tanks.stream().anyMatch(NetheriteDivingHandler::isFireResistant));
    }
    /** Called only at the actual native decrease-air boundary after vanilla has ruled out natural breathing. */
    public static boolean breatheUnderwater(LivingEntity entity){if(entity.isInLava()||!canProvideAir(entity,false))return false;consumeSecond(entity);if(entity instanceof net.minecraft.world.entity.player.Player player)EquipmentAdvancements.award(player,"diving_suit");return true;}
    public static void breatheLava(LivingEntity entity){
        if(entity.level().isClientSide()||!entity.isInLava())return;
        var helmet=getWornItem(entity);if(helmet.isEmpty()||!NetheriteDivingHandler.isFireResistant(helmet))return;
        var tanks=BacktankUtil.getAllWithAir(entity);if(tanks.isEmpty())return;
        // The original criterion rewards the real attempt before checking fireproof air supply.
        if(entity instanceof net.minecraft.server.level.ServerPlayer player&&!(player instanceof net.fabricmc.fabric.api.entity.FakePlayer))EquipmentAdvancements.award(player,"diving_suit_lava");
        if(tanks.stream().noneMatch(NetheriteDivingHandler::isFireResistant))return;
        consumeSecond(entity);
    }
    private static void consumeSecond(LivingEntity entity){if(entity.level().getGameTime()%20==0){var tanks=BacktankUtil.getAllWithAir(entity);if(!tanks.isEmpty())BacktankUtil.consumeAir(entity,tanks.getFirst(),1);}}
}
