package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.fan.processing.HauntingHorseConversion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class HauntingHorseArmorEjectionMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void create$retryActualArmorEjection(CallbackInfo callback) {
        var actual = (Mob) (Object) this;
        if (actual.level() instanceof ServerLevel level) HauntingHorseConversion.retryBodyArmor(level, actual);
    }
}
