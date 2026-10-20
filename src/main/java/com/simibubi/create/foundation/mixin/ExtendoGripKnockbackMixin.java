package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Native knockback supplies the true source directly; no global damage-source buffer. */
@Mixin(LivingEntity.class)
public abstract class ExtendoGripKnockbackMixin {
    @ModifyVariable(method="knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private double create$gripKnockback(double value,double strength,double x,double z,DamageSource source,float damage,boolean auxiliary){return source!=null&&source.getDirectEntity() instanceof Player player&&ExtendoGripItem.isHoldingExtendoGrip(player)?value+2:value;}
}
