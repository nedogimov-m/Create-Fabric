package com.simibubi.create.content.redstone.analogLever;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.bell.BellRenderer;
import com.simibubi.create.content.redstone.RedstoneIndicator;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

public final class AnalogLeverRenderer implements BlockEntityRenderer<AnalogLeverBlockEntity,AnalogLeverRenderer.State>{
    public static final class State extends BlockEntityRenderState{public final PartialModelRenderState handle=new PartialModelRenderState(),indicator=new PartialModelRenderState();public float yaw,pitch,handleAngle;public int color;}
    public AnalogLeverRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(AnalogLeverBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);var block=entity.getBlockState();var face=block.getValue(AnalogLeverBlock.FACE);state.yaw=BellRenderer.horizontalAngle(block.getValue(AnalogLeverBlock.FACING));state.pitch=face==AttachFace.FLOOR?0:face==AttachFace.WALL?90:180;
        float progress=entity.getClientState(partial)/15;state.handleAngle=progress*90;state.color=RedstoneIndicator.color(progress);AllPartialModels.extract(AllPartialModels.ANALOG_LEVER_HANDLE,state.handle);AllPartialModels.extract(AllPartialModels.ANALOG_LEVER_INDICATOR,state.indicator);RedstoneIndicator.tint(state.indicator,state.color);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.rotate(Axis.XP.rotationDegrees(state.pitch));pose.translate(-.5,-.5,-.5);
        pose.pushPose();pose.translate(.5,1/16f,.5);pose.rotate(Axis.XP.rotationDegrees(state.handleAngle));pose.translate(-.5,-1/16f,-.5);state.handle.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        state.indicator.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
}
