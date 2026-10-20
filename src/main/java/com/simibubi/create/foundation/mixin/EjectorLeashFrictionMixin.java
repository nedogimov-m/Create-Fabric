package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.depot.EjectorFriction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Leashable.class)
public interface EjectorLeashFrictionMixin {
    @ModifyExpressionValue(method="angularFriction",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private static float create$sourceEjectorFriction(float original,@Local(argsOnly=true) Entity entity){return EjectorFriction.value(entity,original);}
}
