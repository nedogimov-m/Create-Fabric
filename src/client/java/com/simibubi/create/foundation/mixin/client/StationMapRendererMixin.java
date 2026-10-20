package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.map.StationMapDecorationRenderer;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState.MapDecorationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep actual native atlas/custom geometry/text submissions; adjust only the station's source pose. */
@Mixin(MapRenderer.class)
public abstract class StationMapRendererMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",ordinal=0))
    private void create$iconScale(PoseStack pose,float x,float y,float z,Operation<Void> original,@Local MapDecorationRenderState decoration){
        if(StationMapDecorationRenderer.isStation(decoration))original.call(pose,4.5f,4.5f,3f);else original.call(pose,x,y,z);
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",ordinal=1))
    private void create$iconOffset(PoseStack pose,float x,float y,float z,Operation<Void> original,@Local MapDecorationRenderState decoration){
        if(StationMapDecorationRenderer.isStation(decoration))original.call(pose,1f/9f,0f,0f);else original.call(pose,x,y,z);
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",ordinal=2))
    private void create$labelPosition(PoseStack pose,float x,float y,float z,Operation<Void> original,@Local MapDecorationRenderState decoration){
        if(StationMapDecorationRenderer.isStation(decoration))original.call(pose,StationMapDecorationRenderer.labelX(decoration),decoration.y/2f+70,-.025f);else original.call(pose,x,y,z);
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",ordinal=1))
    private void create$labelScale(PoseStack pose,float x,float y,float z,Operation<Void> original,@Local MapDecorationRenderState decoration){
        // Native26.3 font puts its background at local z=-.01; keep native Z flip so glyphs stay in front.
        if(StationMapDecorationRenderer.isStation(decoration))original.call(pose,.8f,.8f,-1f);else original.call(pose,x,y,z);
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",ordinal=3))
    private void create$labelDepth(PoseStack pose,float x,float y,float z,Operation<Void> original,@Local MapDecorationRenderState decoration){
        if(StationMapDecorationRenderer.isStation(decoration))original.call(pose,0f,0f,0f);else original.call(pose,x,y,z);
    }
}
