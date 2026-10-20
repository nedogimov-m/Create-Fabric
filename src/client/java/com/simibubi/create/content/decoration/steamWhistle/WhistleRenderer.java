package com.simibubi.create.content.decoration.steamWhistle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class WhistleRenderer implements BlockEntityRenderer<WhistleBlockEntity,WhistleRenderer.State> {
    public WhistleRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState mouth=new PartialModelRenderState();
        public float yaw,offset;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(WhistleBlockEntity whistle,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(whistle,state,partial,camera,overlay);var size=whistle.getOctave();
        AllPartialModels.extract(switch(size){case SMALL->AllPartialModels.WHISTLE_MOUTH_SMALL;case MEDIUM->AllPartialModels.WHISTLE_MOUTH_MEDIUM;case LARGE->AllPartialModels.WHISTLE_MOUTH_LARGE;},state.mouth);
        state.yaw=switch(whistle.getBlockState().getValue(WhistleBlock.FACING)){case NORTH->180;case WEST->-90;case EAST->-270;default->0;};state.offset=whistle.getAnimation(partial);
        if(whistle.isSounding()&&whistle.getAnimation(1)>0.5f){float progress=(whistle.getLevel().getGameTime()+partial)/8f;state.offset-=(float)Math.sin(progress*2*Math.PI*(4-size.ordinal()))/16;}
        state.offset*=4f/16;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){pose.pushPose();pose.translate(0.5,0.5,0.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(-0.5,-0.5,-0.5);pose.translate(0,state.offset,0);state.mouth.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
}
