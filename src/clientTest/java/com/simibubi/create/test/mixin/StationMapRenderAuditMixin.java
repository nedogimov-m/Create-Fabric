package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.test.StationMapRenderAudit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.MapRenderState.MapDecorationRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.saveddata.maps.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only observers forward every native submission unchanged and never replace deferred callbacks. */
@Mixin(value=MapRenderer.class,remap=false)
public abstract class StationMapRenderAuditMixin {
    @Inject(method="extractRenderState",at=@At("RETURN"),require=1)
    private void create$actualExtraction(MapId id,MapItemSavedData actual,MapRenderState state,CallbackInfo callback){StationMapRenderAudit.extracted(id,actual,state);}
    @Inject(method="render",at=@At("HEAD"),require=1)
    private void create$actualMapBase(MapRenderState state,PoseStack pose,SubmitNodeCollector collector,boolean onFrame,int light,CallbackInfo callback){StationMapRenderAudit.enter(state,pose,onFrame,light);}
    @Inject(method="render",at=@At("RETURN"),require=1)
    private void create$actualMapReturned(MapRenderState state,PoseStack pose,SubmitNodeCollector collector,boolean onFrame,int light,CallbackInfo callback){StationMapRenderAudit.returned(state);}
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",ordinal=1),require=1)
    private void create$actualIconSubmission(SubmitNodeCollector collector,PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer actual,
            Operation<Void> original,@Local MapDecorationRenderState decoration){
        var observation=StationMapRenderAudit.iconSubmission(decoration,pose);original.call(collector,pose,type,actual);StationMapRenderAudit.iconQueued(observation);
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"),require=1)
    private void create$actualLabelSubmission(OrderedSubmitNodeCollector collector,PoseStack pose,float x,float y,FormattedCharSequence text,
            boolean shadow,Font.DisplayMode mode,int light,int color,int background,int outline,Operation<Void> original,@Local MapDecorationRenderState decoration){
        var observation=StationMapRenderAudit.textSubmission(decoration,pose,x,y,text,shadow,mode,light,color,background,outline);
        original.call(collector,pose,x,y,text,shadow,mode,light,color,background,outline);StationMapRenderAudit.textQueued(observation);
    }
    @Inject(method="lambda$render$1",at=@At("HEAD"),require=1)
    private static void create$actualNativeGeometry(float z,TextureAtlasSprite sprite,int light,PoseStack.Pose pose,VertexConsumer consumer,CallbackInfo callback){StationMapRenderAudit.geometryEnter(z,sprite,light,pose);}
    @Inject(method="lambda$render$1",at=@At("RETURN"),require=1)
    private static void create$actualNativeGeometryReturned(float z,TextureAtlasSprite sprite,int light,PoseStack.Pose pose,VertexConsumer consumer,CallbackInfo callback){StationMapRenderAudit.geometryReturned(sprite,pose);}
}
