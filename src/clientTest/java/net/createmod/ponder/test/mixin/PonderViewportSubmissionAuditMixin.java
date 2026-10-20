package net.createmod.ponder.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.createmod.ponder.test.PonderRegistryRenderAudit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PonderViewport.Renderer.class, remap = false)
public abstract class PonderViewportSubmissionAuditMixin {
    @Inject(method = "renderToTexture(Lnet/createmod/ponder/foundation/ui/PonderViewport$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("RETURN"))
    private void create$observeRealSubmission(PonderViewport.State state, PoseStack pose,
        SubmitNodeCollector collector, CallbackInfo callback) {
        PonderRegistryRenderAudit.submitted(state);
    }
}
