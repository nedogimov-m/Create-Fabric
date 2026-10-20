package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.fluids.FluidPhysics;
import net.minecraft.tags.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntityFluidMixin {
    @WrapOperation(method={"travelInFluid","travelFlying(Lnet/minecraft/world/phys/Vec3;FFF)V","aiStep"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean create$fluidMovement(LivingEntity entity,Operation<Boolean> original){return original.call(entity)||FluidPhysics.immersed(entity);}

    @WrapOperation(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double create$jumpHeight(LivingEntity entity,TagKey<Fluid> tag,Operation<Double> original){
        double height=original.call(entity,tag);return tag==FluidTags.WATER?Math.max(height,entity.getFluidHeight(FluidPhysics.PLACEABLE)):height;
    }
    @WrapOperation(method="baseTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean create$drowning(LivingEntity entity,TagKey<Fluid> tag,Operation<Boolean> original){return original.call(entity,tag)||(tag==FluidTags.WATER&&FluidPhysics.eyesImmersed(entity));}
}
