package com.simibubi.create.test.mixin;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=BacktankRenderer.class,remap=false)
abstract class ArmorWorldRenderProbeMixin {
    @Inject(method="submit(Lcom/simibubi/create/content/equipment/armor/BacktankRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD")) private void createTest$world(CallbackInfo ci){ArmorNativeRenderProbe.world();}
}
