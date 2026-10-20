package com.simibubi.create.test.mixin;
import com.simibubi.create.content.equipment.armor.RemainingAirOverlay;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=RemainingAirOverlay.class,remap=false)
abstract class ArmorAirHudRenderProbeMixin {
    @Inject(method="extractOverlay",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V")) private void createTest$actualAirText(CallbackInfo ci){ArmorNativeRenderProbe.air();}
}
