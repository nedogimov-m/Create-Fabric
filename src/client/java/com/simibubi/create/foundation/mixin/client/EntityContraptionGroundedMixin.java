package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionPassengerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The source native move boundary distinguishes supported riders from new train impacts. */
@Mixin(Entity.class)
abstract class EntityContraptionGroundedMixin {
  @Inject(method = "move", at = @At("TAIL"))
  private void create$contraptionGrounded(MoverType type, Vec3 motion, CallbackInfo callback) {
    var self = (Entity) (Object) this;
    if (!self.level().isClientSide() || self.onGround() || self.isPassenger()) return;
    var worldPos = self.position().add(0, -.2, 0);
    for (var owner : ContraptionHandler.collidingWith(self)) {
      var pos = BlockPos.containing(owner.toLocalVector(worldPos, 1));
      if (owner.getContraption().isHiddenInPortal(pos)
          || !owner.getContraption().getBlocks().containsKey(pos)) continue;
      owner.registerColliding(self);
      self.setOnGround(true);
      ContraptionPassengerData.markGrounded(self);
      return;
    }
  }
}
