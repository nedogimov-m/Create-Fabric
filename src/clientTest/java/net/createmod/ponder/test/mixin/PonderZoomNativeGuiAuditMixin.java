package net.createmod.ponder.test.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.util.function.Supplier;
import net.createmod.ponder.test.PonderZoomRenderAudit;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class PonderZoomNativeGuiAuditMixin {
    @Inject(method = "executeDrawRange", at = @At("RETURN"))
    private void create$observeActualNativePass(Supplier<String> label, RenderTarget target, GpuBufferSlice uniform, int first, int last, CallbackInfo callback) {
        PonderZoomRenderAudit.guiDraw((GuiRenderer) (Object) this, target, first, last);
    }
}
