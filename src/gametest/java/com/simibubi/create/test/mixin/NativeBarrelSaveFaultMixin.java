package com.simibubi.create.test.mixin;

import com.simibubi.create.test.NativeBarrelSaveFaultFixtures;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only native serialization callback; production data and native result stay intact. */
@Mixin(BarrelBlockEntity.class)
public abstract class NativeBarrelSaveFaultMixin {
  @Inject(
      method = "saveAdditional(Lnet/minecraft/world/level/storage/ValueOutput;)V",
      at = @At("RETURN"))
  private void create$afterActualNativeBarrelSave(ValueOutput output, CallbackInfo ci) {
    NativeBarrelSaveFaultFixtures.afterNativeSave((BarrelBlockEntity) (Object) this);
  }
}
