package com.simibubi.create.test.mixin;
import com.simibubi.create.test.EquipmentNativeFrameProbe;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AvatarRenderer.class)
abstract class EquipmentNativeArmProbeMixin {
    @Inject(method={"renderRightHand","renderLeftHand"},at=@At("HEAD")) private void createTest$arm(CallbackInfo ci){EquipmentNativeFrameProbe.arm();}
}
