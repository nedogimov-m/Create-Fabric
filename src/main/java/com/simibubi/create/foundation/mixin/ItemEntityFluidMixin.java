package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.fluids.FluidPhysics;
import net.minecraft.tags.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntity.class)
abstract class ItemEntityFluidMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/item/ItemEntity;isInWater()Z"))
    private boolean create$float(ItemEntity entity,Operation<Boolean> original){return original.call(entity)||FluidPhysics.immersed(entity);}
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/item/ItemEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double create$floatHeight(ItemEntity entity,TagKey<Fluid> tag,Operation<Double> original){
        double height=original.call(entity,tag);return tag==FluidTags.WATER?Math.max(height,entity.getFluidHeight(FluidPhysics.PLACEABLE)):height;
    }
}
