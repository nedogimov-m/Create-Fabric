package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exact pinned Player.aiStep passenger-expression seam; no alternate pickup/payment path. */
@Mixin(value=Player.class,priority=1500)
abstract class ContraptionPickupConfigMixin {
 @ModifyExpressionValue(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;isPassenger()Z"))
 private boolean create$configuredPickupBounds(boolean passenger){
  if(passenger&&!AllConfigs.server().kinetics.syncPlayerPickupHitboxWithContraptionHitbox.get()&&((Player)(Object)this).getVehicle() instanceof AbstractContraptionEntity)return false;
  return passenger;
 }
}
