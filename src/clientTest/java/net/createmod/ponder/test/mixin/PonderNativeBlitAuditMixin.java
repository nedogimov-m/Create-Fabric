package net.createmod.ponder.test.mixin;

import net.createmod.ponder.foundation.ui.PonderViewport;
import net.createmod.ponder.test.PonderRegistryRenderAudit;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PictureInPictureRenderer.class, remap = false)
public abstract class PonderNativeBlitAuditMixin {
    @Inject(method = "blitTexture", at = @At("RETURN"))
    private void create$observeActualNativeBlit(PictureInPictureRenderState state,
        GuiRenderState gui, CallbackInfo callback) {
        if (state instanceof PonderViewport.State source) PonderRegistryRenderAudit.blitted(source);
    }
}
