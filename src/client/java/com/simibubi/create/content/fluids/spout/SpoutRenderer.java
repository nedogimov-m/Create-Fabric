package com.simibubi.create.content.fluids.spout;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.fluid.FluidBoxRenderState;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class SpoutRenderer implements BlockEntityRenderer<SpoutBlockEntity,SpoutRenderer.State> {
    public SpoutRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState[] bits={new PartialModelRenderState(),new PartialModelRenderState(),new PartialModelRenderState()};
        public final FluidBoxRenderState tank=new FluidBoxRenderState(),stream=new FluidBoxRenderState();
        public float squeeze;
    }
    @Override public State createRenderState(){return new State();}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public void extractRenderState(SpoutBlockEntity spout,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(spout,state,partial,camera,overlay);state.tank.clear();state.stream.clear();
        AllPartialModels.extract(AllPartialModels.SPOUT_TOP,state.bits[0]);AllPartialModels.extract(AllPartialModels.SPOUT_MIDDLE,state.bits[1]);AllPartialModels.extract(AllPartialModels.SPOUT_BOTTOM,state.bits[2]);
        var fluid=spout.tank.getResource();float fill=spout.getFluidLevel(partial);
        if(!fluid.isBlank()&&fill>0){
            float min=2.5f/16,max=13.5f/16,height=11f/16*Math.max(fill,0.175f),bottom=FluidVariantAttributes.isLighterThanAir(fluid)?max-height:min;
            state.tank.extract(fluid,spout.getLevel(),spout.getBlockPos(),state.lightCoords,min,bottom,min,max,bottom+height,max,false);
        }
        float time=spout.processingTicks-partial,progress=Math.clamp(1-(time-5)/10,0,1),radius=-(float)(Math.pow(2*progress-1,2)-1);
        if(!fluid.isBlank()&&spout.processingTicks!=-1&&radius>0)
            state.stream.extract(fluid,spout.getLevel(),spout.getBlockPos(),state.lightCoords,0.5f-radius/32,-1.2f,0.5f-radius/32,0.5f+radius/32,0,0.5f+radius/32,true);
        state.squeeze=-radius;
        if(time<0)state.squeeze=0;else if(time<2)state.squeeze=-time/2;else if(time<10)state.squeeze=-1;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        state.tank.submit(pose,collector);state.stream.submit(pose,collector);pose.pushPose();
        for(var bit:state.bits){bit.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.translate(0,-3*state.squeeze/32,0);}pose.popPose();
    }
}
