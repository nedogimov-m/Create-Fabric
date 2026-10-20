package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.infrastructure.gui.CreateMenuArtwork;
import com.simibubi.create.test.CreateMenuNativeProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateMenuArtwork.Renderer.class)
public abstract class CreateMenuArtworkProbeMixin {
    @Inject(method="renderToTexture(Lcom/simibubi/create/infrastructure/gui/CreateMenuArtwork$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",at=@At("HEAD"))
    private void createTest$frame(CreateMenuArtwork.State state,PoseStack pose,SubmitNodeCollector collector,CallbackInfo ci){CreateMenuNativeProbe.frame(state);}
    @WrapOperation(method="renderToTexture(Lcom/simibubi/create/infrastructure/gui/CreateMenuArtwork$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",at=@At(value="INVOKE",target="Lcom/simibubi/create/foundation/model/PartialModelRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void createTest$model(PartialModelRenderState model,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,int outline,Operation<Void> original){CreateMenuNativeProbe.model(model,pose);original.call(model,pose,collector,light,overlay,outline);}
    @Inject(method="renderToTexture(Lcom/simibubi/create/infrastructure/gui/CreateMenuArtwork$State;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",at=@At("RETURN"))
    private void createTest$finish(CreateMenuArtwork.State state,PoseStack pose,SubmitNodeCollector collector,CallbackInfo ci){CreateMenuNativeProbe.endFrame();}
}
