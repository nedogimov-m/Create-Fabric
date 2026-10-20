package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.entity.CarriagePassengerMountGuard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ServerPlayer delegates here; NPC reparenting must obey the same actual body custody boundary. */
@Mixin(Entity.class)
public abstract class CarriagePassengerMountMixin {
  @Inject(
      method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void create$rejectUnsupportedCarriagePlayerTree(
      Entity parent, boolean force, boolean event, CallbackInfoReturnable<Boolean> cir) {
    if (!CarriagePassengerMountGuard.mayMount((Entity) (Object) this, parent))
      cir.setReturnValue(false);
  }
}
