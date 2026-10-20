package net.createmod.ponder.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.ui.PonderNavigationRenderer;
import net.createmod.ponder.test.PonderZoomRenderAudit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PonderNavigationRenderer.class)
public abstract class PonderZoomRendererAuditMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private static void create$observeNativeExtraction(GuiGraphicsExtractor gui, Screen previous, int width, int height, float partial, float alpha,
            CallbackInfoReturnable<PonderNavigationRenderer.State> callback) {
        PonderZoomRenderAudit.extracted(previous, partial, callback.getReturnValue());
    }
    @Inject(method = "renderToTexture(Lnet/createmod/ponder/foundation/ui/PonderNavigationRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("HEAD"))
    private void create$nativeCallbackBegin(PonderNavigationRenderer.State state, PoseStack pose, SubmitNodeCollector collector, CallbackInfo callback) {
        PonderZoomRenderAudit.begin((PonderNavigationRenderer) (Object) this, state);
    }
    @Inject(method = "renderToTexture(Lnet/createmod/ponder/foundation/ui/PonderNavigationRenderer$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("RETURN"))
    private void create$nativeCallbackEnd(PonderNavigationRenderer.State state, PoseStack pose, SubmitNodeCollector collector, CallbackInfo callback) {
        PonderZoomRenderAudit.end(state);
    }
    @Inject(method = "blitTexture(Lnet/createmod/ponder/foundation/ui/PonderNavigationRenderer$State;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;)V", at = @At("HEAD"))
    private void create$nativeBlitBegin(PonderNavigationRenderer.State state, GuiRenderState output, CallbackInfo callback) { PonderZoomRenderAudit.beginBlit(state); }
    @Inject(method = "blitTexture(Lnet/createmod/ponder/foundation/ui/PonderNavigationRenderer$State;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;)V", at = @At("RETURN"))
    private void create$nativeBlitEnd(PonderNavigationRenderer.State state, GuiRenderState output, CallbackInfo callback) { PonderZoomRenderAudit.endBlit(state); }
}
