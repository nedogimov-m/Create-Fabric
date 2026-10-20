package com.simibubi.create.test.mixin;
import com.simibubi.create.content.equipment.armor.CardboardArmorStealthOverlay;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CardboardArmorStealthOverlay.class,remap=false)
abstract class ArmorBlurRenderProbeMixin {
    @Inject(method="extractOverlay",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIII)V")) private static void createTest$actualBlurBlit(CallbackInfo ci){ArmorNativeRenderProbe.blur();}
}
