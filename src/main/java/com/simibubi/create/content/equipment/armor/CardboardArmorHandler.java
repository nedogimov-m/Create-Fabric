package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.content.equipment.EquipmentAdvancements;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CardboardArmorHandler {
    public static boolean testForStealth(Entity candidate){
        if(!(candidate instanceof LivingEntity entity)||entity.getPose()!=Pose.CROUCHING||entity instanceof Player player&&player.getAbilities().flying)return false;
        return isCardboardArmor(entity.getItemBySlot(EquipmentSlot.HEAD))&&isCardboardArmor(entity.getItemBySlot(EquipmentSlot.CHEST))&&isCardboardArmor(entity.getItemBySlot(EquipmentSlot.LEGS))&&isCardboardArmor(entity.getItemBySlot(EquipmentSlot.FEET));
    }
    public static boolean isCardboardArmor(ItemStack stack){return stack.getItem() instanceof CardboardArmorItem;}
    public static EntityDimensions stealthDimensions(LivingEntity entity){float scale=entity.getScale();if(entity instanceof ServerPlayer player&&player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player)EquipmentAdvancements.award(player,"cardboard_armor");return EntityDimensions.fixed(.6f*scale,.8f*scale).withEyeHeight(.6f*scale);}
    public static void playerChangesEquipment(LivingEntity entity){if(entity instanceof Player player&&player.getPose()==Pose.CROUCHING)player.refreshDimensions();}
    public static void mobsMayLoseTargetWhenItIsWearingCardboard(Mob mob){
        if(mob.tickCount%16!=0)return;
        if(testForStealth(mob.getTarget())){mob.setTarget(null);for(var goal:((com.simibubi.create.foundation.mixin.CardboardMobAccessor)mob).create$cardboardTargetSelector().getAvailableGoals())if(goal.isRunning()&&goal.getGoal() instanceof TargetGoal target)target.stop();}
        if(mob instanceof NeutralMob neutral&&mob.level() instanceof ServerLevel level){var id=neutral.getPersistentAngerTarget();if(id!=null&&testForStealth(id.getEntity(level,LivingEntity.class)))neutral.stopBeingAngry();}
        if(testForStealth(mob.getLastHurtByMob())){mob.setLastHurtByMob(null);mob.setLastHurtByPlayer((java.util.UUID)null,0);}
    }
    private CardboardArmorHandler(){}
}
