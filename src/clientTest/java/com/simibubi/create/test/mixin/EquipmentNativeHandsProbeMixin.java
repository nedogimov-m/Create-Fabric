package com.simibubi.create.test.mixin;
import com.simibubi.create.test.EquipmentNativeFrameProbe;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class EquipmentNativeHandsProbeMixin {
    @Inject(method="submitHandsWithItems",at=@At("HEAD")) private void createTest$hands(CallbackInfo ci){EquipmentNativeFrameProbe.nativeHands();}
}
