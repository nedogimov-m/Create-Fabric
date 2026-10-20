package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.decoration.steamWhistle.WhistleEffects;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class WhistleSoundReloadMixin {
    @Inject(method="reload",at=@At("TAIL"))
    private void create$resetWhistleAudio(CallbackInfo callback){WhistleEffects.clearSounds();}
}
