package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.test.OutlinerNativeGeometryAudit;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.OutlineRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual production callbacks/vertices only; every native operation forwards original arguments once. */
@Mixin(value=OutlineRenderer.class,remap=false)
public abstract class OutlinerNativeGeometryAuditMixin {
    @Inject(method="submitLine",at=@At("HEAD"),require=1)
    private static void create$actualLineBase(PoseStack pose,SubmitNodeCollector collector,Outline.Geometry geometry,Outline.Segment line,CallbackInfo callback){OutlinerNativeGeometryAudit.lineBase(pose,geometry,line);}
    @Inject(method="submitLine",at=@At("RETURN"),require=1)
    private static void create$actualLineBaseReturned(PoseStack pose,SubmitNodeCollector collector,Outline.Geometry geometry,Outline.Segment line,CallbackInfo callback){OutlinerNativeGeometryAudit.lineBaseReturned();}
    @WrapOperation(method="submitFace",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"),require=1)
    private static void create$actualFaceQueue(SubmitNodeCollector collector,PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer actual,Operation<Void> original,
        @Local(argsOnly=true) Outline.Geometry geometry,@Local(argsOnly=true) Outline.Face face){
        var token=OutlinerNativeGeometryAudit.queued(pose,geometry,face,null,type);original.call(collector,pose,type,actual);OutlinerNativeGeometryAudit.queuedReturned(token);
    }
    @WrapOperation(method="submitLine",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"),require=1)
    private static void create$actualLineQueue(SubmitNodeCollector collector,PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer actual,Operation<Void> original,
        @Local(argsOnly=true) Outline.Geometry geometry,@Local(argsOnly=true) Outline.Segment line){
        var token=OutlinerNativeGeometryAudit.queued(pose,geometry,null,line,type);original.call(collector,pose,type,actual);OutlinerNativeGeometryAudit.queuedReturned(token);
    }
    @Inject(method="emitCuboid",at=@At("HEAD"),require=1)
    private static void create$actualCuboid(PoseStack.Pose pose,VertexConsumer vertices,Outline.Geometry geometry,Outline.Segment line,Vec3 min,Vec3 max,CallbackInfo callback){OutlinerNativeGeometryAudit.cuboid(pose,geometry,line,min,max);}
    @Inject(method="emitCuboid",at=@At("RETURN"),require=1)
    private static void create$actualCuboidReturned(PoseStack.Pose pose,VertexConsumer vertices,Outline.Geometry geometry,Outline.Segment line,Vec3 min,Vec3 max,CallbackInfo callback){OutlinerNativeGeometryAudit.returned(geometry,null,line);}
    @Inject(method="emitFace",at=@At("HEAD"),require=1)
    private static void create$actualFace(PoseStack.Pose pose,VertexConsumer vertices,Outline.Geometry geometry,Outline.Face face,CallbackInfo callback){OutlinerNativeGeometryAudit.face(pose,geometry,face);}
    @Inject(method="emitFace",at=@At("RETURN"),require=1)
    private static void create$actualFaceReturned(PoseStack.Pose pose,VertexConsumer vertices,Outline.Geometry geometry,Outline.Face face,CallbackInfo callback){OutlinerNativeGeometryAudit.returned(geometry,face,null);}
    @WrapOperation(method="emitQuad",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1)
    private static VertexConsumer create$actualVertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float z,Operation<VertexConsumer> original){var result=original.call(vertices,pose,x,y,z);OutlinerNativeGeometryAudit.vertex(x,y,z);return result;}
    @WrapOperation(method="emitQuad",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1)
    private static VertexConsumer create$actualColor(VertexConsumer vertices,int color,Operation<VertexConsumer> original){var result=original.call(vertices,color);OutlinerNativeGeometryAudit.color(color);return result;}
    @WrapOperation(method="emitQuad",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;setUv(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1)
    private static VertexConsumer create$actualUV(VertexConsumer vertices,float u,float v,Operation<VertexConsumer> original){var result=original.call(vertices,u,v);OutlinerNativeGeometryAudit.uv(u,v);return result;}
    @WrapOperation(method="emitQuad",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;setLight(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1)
    private static VertexConsumer create$actualLight(VertexConsumer vertices,int light,Operation<VertexConsumer> original){var result=original.call(vertices,light);OutlinerNativeGeometryAudit.light(light);return result;}
    @WrapOperation(method="emitQuad",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;setOverlay(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1)
    private static VertexConsumer create$actualOverlay(VertexConsumer vertices,int overlay,Operation<VertexConsumer> original){var result=original.call(vertices,overlay);OutlinerNativeGeometryAudit.overlay(overlay);return result;}
    @WrapOperation(method="emitQuad",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;setNormal(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),require=1)
    private static VertexConsumer create$actualNormal(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float z,Operation<VertexConsumer> original){var result=original.call(vertices,pose,x,y,z);OutlinerNativeGeometryAudit.normal(x,y,z);return result;}
}
