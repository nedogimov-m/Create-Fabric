package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.trains.CameraDistanceModifier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
abstract class MountedCameraPlayerMixin {
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("RETURN"))
    private void create$observeActualMount(Entity vehicle, boolean force, boolean sendEvent,
        CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) CameraDistanceModifier.onMountChanged((LocalPlayer) (Object) this);
    }

    @Inject(method = "removeVehicle", at = @At("TAIL"))
    private void create$observeActualDismount(CallbackInfo callback) {
        CameraDistanceModifier.onMountChanged((LocalPlayer) (Object) this);
    }
}
