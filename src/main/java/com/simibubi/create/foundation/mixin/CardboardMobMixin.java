package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.armor.CardboardArmorHandler;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
abstract class CardboardMobMixin {
    @Inject(method="tick",at=@At("HEAD")) private void create$sourceCardboardTargetLoss(CallbackInfo ci){CardboardArmorHandler.mobsMayLoseTargetWhenItIsWearingCardboard((Mob)(Object)this);}
}
