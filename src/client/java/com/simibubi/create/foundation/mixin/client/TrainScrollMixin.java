package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.trains.TrainHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only an actual acknowledged train-control session consumes wheel input for source throttle. */
@Mixin(MouseHandler.class)
public abstract class TrainScrollMixin {
  @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
  private void create$trainThrottle(
      long window, double horizontal, double vertical, CallbackInfo callback) {
    if (window == Minecraft.getInstance().getWindow().handle() && TrainHUD.onScroll(vertical))
      callback.cancel();
  }
}
