package com.simibubi.create.test.mixin;
import com.simibubi.create.content.equipment.armor.NetheriteBacktankFirstPersonRenderer;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=NetheriteBacktankFirstPersonRenderer.class,remap=false)
abstract class ArmorSleeveRenderProbeMixin {
    @Inject(method="submitHand",at=@At("RETURN")) private static void createTest$sleeve(CallbackInfoReturnable<Boolean> cir){ArmorNativeRenderProbe.sleeve(cir.getReturnValue());}
}
