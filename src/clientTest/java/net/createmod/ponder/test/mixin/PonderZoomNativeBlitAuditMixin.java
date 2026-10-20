package net.createmod.ponder.test.mixin;

import net.createmod.ponder.test.PonderZoomRenderAudit;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class PonderZoomNativeBlitAuditMixin {
    @Inject(method = "addBlitToCurrentLayer", at = @At("RETURN"))
    private void create$observeActualAddedBlit(BlitRenderState state, CallbackInfo callback) { PonderZoomRenderAudit.actualBlit(state); }
}
