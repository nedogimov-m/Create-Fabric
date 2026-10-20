package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.depot.EjectorFriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBoat.class)
public abstract class EjectorBoatFrictionMixin {
    @ModifyExpressionValue(method="getGroundFriction",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float create$sourceEjectorFriction(float original,@Local BlockPos.MutableBlockPos sampled){
        return EjectorFriction.value(((AbstractBoat)(Object)this).level(),sampled,original);
    }
}
