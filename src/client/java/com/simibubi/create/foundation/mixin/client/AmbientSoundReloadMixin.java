package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.foundation.sound.SoundScapes;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class AmbientSoundReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void create$stopAmbientBeforeReload(CallbackInfo callback) {
        SoundScapes.invalidateAll();
    }
}
