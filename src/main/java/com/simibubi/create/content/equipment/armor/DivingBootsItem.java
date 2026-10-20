package com.simibubi.create.content.equipment.armor;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.*;
import net.minecraft.world.phys.Vec3;

public class DivingBootsItem extends BaseArmorItem {
    public static final EquipmentSlot SLOT=EquipmentSlot.FEET;
    public static final ArmorType TYPE=ArmorType.BOOTS;
    public DivingBootsItem(ArmorMaterial material,Properties properties,Identifier textureLoc){super(material,TYPE,properties,textureLoc);}
    public static boolean isWornBy(Entity entity){return !getWornItem(entity).isEmpty();}
    public static ItemStack getWornItem(Entity entity){if(!(entity instanceof LivingEntity living))return ItemStack.EMPTY;var stack=living.getItemBySlot(SLOT);return stack.getItem() instanceof DivingBootsItem?stack:ItemStack.EMPTY;}
    public static void accelerateDescentUnderwater(LivingEntity entity){
        if(!affects(entity))return;var motion=entity.getDeltaMovement();boolean jumping=((ArmorLivingAccess)entity).create$isArmorJumping();entity.setOnGround(entity.onGround()||entity.verticalCollision);
        if(jumping&&entity.onGround()){motion=motion.add(0,.5,0);entity.setOnGround(false);}else motion=motion.add(0,-.05,0);
        if(motion.multiply(1,0,1).length()<.145&&(entity.zza>0||entity.xxa!=0)&&!entity.isShiftKeyDown())motion=motion.multiply(1.3,1,1.3);
        entity.setDeltaMovement(motion);
    }
    protected static boolean affects(LivingEntity entity){return isWornBy(entity)&&entity.isInWater()&&entity.getPose()!=Pose.SWIMMING&&!(entity instanceof Player player&&player.getAbilities().flying);}
    public static Vec3 getMovementMultiplier(LivingEntity entity){
        var state=(ArmorLivingAccess)entity;double y=entity.getDeltaMovement().y;double vertical=y<0?Math.max(0,2.5-Math.abs(y)*2):1;
        if(!entity.onGround()){
            if(state.create$isArmorJumping()&&state.create$wasLavaGrounded())vertical=y==0?0:(entity.isEyeInFluid(FluidTags.LAVA)?1:.5)/y;else if(y>0)vertical=1.3;
            state.create$setLavaGrounded(false);return new Vec3(1.75,vertical,1.75);
        }state.create$setLavaGrounded(true);double horizontal=entity.isSprinting()?1.85:1.75;return new Vec3(horizontal,vertical,horizontal);
    }
}
