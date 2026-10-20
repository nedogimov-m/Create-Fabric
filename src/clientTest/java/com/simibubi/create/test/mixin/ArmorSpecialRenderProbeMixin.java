package com.simibubi.create.test.mixin;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandlerClient;
import com.simibubi.create.test.ArmorNativeRenderProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=CardboardArmorHandlerClient.class,remap=false)
abstract class ArmorSpecialRenderProbeMixin {
    @Inject(method="submit",at=@At("RETURN")) private static void createTest$box(CallbackInfoReturnable<Boolean> cir){ArmorNativeRenderProbe.box(cir.getReturnValue());}
}
