package com.simibubi.create.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.test.SymmetryWandNativeProbe;
import com.simibubi.create.test.SymmetryWandCompositionNativeProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PartialModelRenderState.class)
public abstract class SymmetryWandNativeWorldProbeMixin {
    @Inject(method="submit",at=@At("HEAD"))
    private void create$observeNativeMarkerSubmit(PoseStack pose,SubmitNodeCollector collector,int light,int overlay,int outline,CallbackInfo ci){SymmetryWandNativeProbe.world((PartialModelRenderState)(Object)this);SymmetryWandCompositionNativeProbe.world((PartialModelRenderState)(Object)this,pose);}
}
