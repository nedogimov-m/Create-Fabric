package net.createmod.ponder.test.mixin;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.createmod.ponder.test.PonderRegistryRenderAudit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PonderViewport.class, remap = false)
public abstract class PonderViewportExtractionAuditMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private static void create$observeRealExtraction(GuiGraphicsExtractor gui, PonderScene scene,
        int x, int y, int width, int height, double slide, float partial, float flash,
        CallbackInfoReturnable<PonderViewport.State> callback) {
        PonderRegistryRenderAudit.extracted(scene, callback.getReturnValue());
        net.createmod.ponder.test.PonderVisualRegressionTest.observe(scene,callback.getReturnValue());
    }
}
