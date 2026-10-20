package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.fluids.FluidPhysics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerFluidMixin {
    @WrapOperation(method={"aiStep","shouldStopSwimSprinting"},at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isInWater()Z"))
    private boolean create$swimInput(LocalPlayer player,Operation<Boolean> original){return original.call(player)||FluidPhysics.immersed(player);}
}
