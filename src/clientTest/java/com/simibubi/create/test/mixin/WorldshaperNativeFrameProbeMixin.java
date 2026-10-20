package com.simibubi.create.test.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperRenderHandler;
import com.simibubi.create.test.WorldshaperNativeProbe;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=WorldshaperRenderHandler.Frame.class,remap=false)
abstract class WorldshaperNativeFrameProbeMixin {
    @Inject(method="submit",at=@At("HEAD"))private void createTest$frame(PoseStack pose,SubmitNodeCollector collector,CallbackInfo ci){WorldshaperNativeProbe.frame((WorldshaperRenderHandler.Frame)(Object)this);}
    @WrapOperation(method="submit",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"))
    private void createTest$geometry(SubmitNodeCollector collector,PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer renderer,Operation<Void> original){original.call(collector,pose,type,WorldshaperNativeProbe.geometry(renderer));}
}
