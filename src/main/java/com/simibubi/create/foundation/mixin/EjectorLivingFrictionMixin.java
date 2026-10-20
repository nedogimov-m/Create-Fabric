package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.depot.EjectorFriction;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class EjectorLivingFrictionMixin {
    @ModifyExpressionValue(method="travelInAir",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float create$sourceEjectorFriction(float original){return EjectorFriction.value((Entity)(Object)this,original);}
}
