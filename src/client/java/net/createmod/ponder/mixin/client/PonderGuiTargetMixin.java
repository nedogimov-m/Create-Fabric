package net.createmod.ponder.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.createmod.ponder.foundation.ui.PonderGuiTargets;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** One native output lookup; ordinary renderers retain the exact original target. */
@Mixin(GuiRenderer.class)
public abstract class PonderGuiTargetMixin {
    @ModifyExpressionValue(method = "draw", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"), require = 1)
    private RenderTarget ponder$scopedNativeOutput(RenderTarget original) {
        return PonderGuiTargets.targetFor((GuiRenderer) (Object) this, original);
    }
}
