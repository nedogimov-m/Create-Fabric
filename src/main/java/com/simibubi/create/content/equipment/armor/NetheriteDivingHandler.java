package com.simibubi.create.content.equipment.armor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;

/** Re-evaluates the actual native equipped stacks, including air depletion, after reload and component changes. */
public final class NetheriteDivingHandler {
    public static final String NETHERITE_DIVING_BITS_KEY="CreateNetheriteDivingBits",FIRE_IMMUNE_KEY="CreateFireImmune";
    public static boolean isFireResistant(ItemStack stack){var resistance=stack.get(DataComponents.DAMAGE_RESISTANT);return resistance!=null&&resistance.types().stream().anyMatch(type->type.is(DamageTypeTags.IS_FIRE));}
    public static boolean isNetheriteDivingHelmet(ItemStack stack){return stack.getItem() instanceof DivingHelmetItem&&isNetheriteArmor(stack);}
    public static boolean isNetheriteBacktank(ItemStack stack){return stack.is(BacktankUtil.PRESSURIZED_AIR_SOURCES)&&isNetheriteArmor(stack);}
    public static boolean isNetheriteArmor(ItemStack stack){return isFireResistant(stack)&&stack.has(DataComponents.EQUIPPABLE)&&(stack.getItem() instanceof BaseArmorItem||stack.is(ItemTags.HEAD_ARMOR)||stack.is(ItemTags.CHEST_ARMOR)||stack.is(ItemTags.LEG_ARMOR)||stack.is(ItemTags.FOOT_ARMOR));}
    public static int getDivingBits(LivingEntity entity){int bits=0;if(isNetheriteDivingHelmet(entity.getItemBySlot(EquipmentSlot.HEAD)))bits|=1;if(isNetheriteBacktank(entity.getItemBySlot(EquipmentSlot.CHEST))&&BacktankUtil.hasAirRemaining(entity.getItemBySlot(EquipmentSlot.CHEST)))bits|=2;if(isNetheriteArmor(entity.getItemBySlot(EquipmentSlot.LEGS)))bits|=4;if(isNetheriteArmor(entity.getItemBySlot(EquipmentSlot.FEET)))bits|=8;return bits;}
    public static boolean isFireImmune(LivingEntity entity){return getDivingBits(entity)==15;}
    private NetheriteDivingHandler(){}
}
