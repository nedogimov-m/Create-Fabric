package net.createmod.ponder.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.test.PonderCustomElementsTest;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ItemEntityRenderer.class,remap=false)
abstract class PonderCustomElementEntitySubmitMixin {
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"))
    private void createTest$ordinary(ItemEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo callback){
        PonderCustomElementsTest.ordinarySubmitted(state);
    }
}
