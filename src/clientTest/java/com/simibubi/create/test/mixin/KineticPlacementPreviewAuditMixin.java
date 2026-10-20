package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.placement.KineticPlacementPreview;
import com.simibubi.create.test.KineticPlacementPreviewAudit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=KineticPlacementPreview.Frame.class,remap=false)
public abstract class KineticPlacementPreviewAuditMixin {
    @WrapOperation(method="submit",at=@At(value="INVOKE",target="Lcom/simibubi/create/foundation/model/PartialModelRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),require=1)
    private void create$actualPreviewNativeSubmission(PartialModelRenderState model,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,int outline,
        Operation<Void> original,@Local(argsOnly=true) Vec3 camera){
        original.call(model,pose,collector,light,overlay,outline);
        KineticPlacementPreviewAudit.submitted((KineticPlacementPreview.Frame)(Object)this,model,pose,camera,light,overlay,outline);
    }
}
