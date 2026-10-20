package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.CameraDistanceModifier;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
abstract class MountedCameraDistanceMixin {
    @ModifyArg(method = "alignWithEntity(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
    private float create$scaleDesiredDistance(float distance, @Local(argsOnly = true) float partialTick) {
        return distance * CameraDistanceModifier.getMultiplier(partialTick);
    }
}
