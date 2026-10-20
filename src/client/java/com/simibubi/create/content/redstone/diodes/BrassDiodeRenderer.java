package com.simibubi.create.content.redstone.diodes;

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
import net.minecraft.world.phys.Vec3;

public final class BrassDiodeRenderer implements BlockEntityRenderer<BrassDiodeBlockEntity,BrassDiodeRenderer.State>{
    public static final class State extends BlockEntityRenderState{public final PartialModelRenderState indicator=new PartialModelRenderState();public float yaw,progress;public int color;}
    public BrassDiodeRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(BrassDiodeBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.yaw=BellRenderer.horizontalAngle(entity.getBlockState().getValue(BrassDiodeBlock.FACING));state.progress=entity.getProgress();state.color=RedstoneIndicator.color(state.progress);
        AllPartialModels.extract(AllPartialModels.FLEXPEATER_INDICATOR,state.indicator);RedstoneIndicator.tint(state.indicator,state.color);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.translate(-.5,-.5,-.5);state.indicator.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
}
