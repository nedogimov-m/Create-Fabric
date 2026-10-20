package com.simibubi.create.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandPortrait;
import com.simibubi.create.test.SymmetryWandNativeProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SymmetryWandPortrait.Renderer.class)
public abstract class SymmetryWandNativePortraitProbeMixin {
    @Inject(method="renderToTexture(Lcom/simibubi/create/content/equipment/symmetryWand/SymmetryWandPortrait$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",at=@At("HEAD"))
    private void create$observeRealNativePortrait(SymmetryWandPortrait.State state,PoseStack pose,SubmitNodeCollector collector,CallbackInfo ci){SymmetryWandNativeProbe.portrait(state);}
}
