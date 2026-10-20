package com.simibubi.create.content.redstone.deskBell;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.bell.BellRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class DeskBellRenderer implements BlockEntityRenderer<DeskBellBlockEntity,DeskBellRenderer.State> {
    public DeskBellRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState { public final PartialModelRenderState plunger=new PartialModelRenderState(),bell=new PartialModelRenderState();public boolean draw;public float yaw,pitch,dip,wobbleX,wobbleZ; }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(DeskBellBlockEntity bell,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(bell,state,partial,camera,overlay);
        float p=bell.getAnimation(partial);state.draw=p>=.004||bell.getBlockState().getValue(DeskBellBlock.POWERED);if(!state.draw){state.bell.clear();state.plunger.clear();return;}
        var facing=bell.getBlockState().getValue(DeskBellBlock.FACING);state.yaw=BellRenderer.horizontalAngle(facing);state.pitch=facing==Direction.UP?0:facing==Direction.DOWN?180:90;
        float f=(float)(1-4*Math.pow(Math.max(p-.5,0)-.5,2)),f2=(float)Math.pow(p,1.25);state.dip=f*-.75f/16;state.wobbleX=f2*8*(float)Math.sin(p*Math.PI*4+bell.getAnimationOffset());state.wobbleZ=f2*8*(float)Math.cos(p*Math.PI*4+bell.getAnimationOffset());
        AllPartialModels.extract(AllPartialModels.DESK_BELL_PLUNGER,state.plunger);AllPartialModels.extract(AllPartialModels.DESK_BELL_BELL,state.bell);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.draw)return;
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.rotate(Axis.XP.rotationDegrees(state.pitch));pose.translate(-.5,-.5,-.5);pose.translate(0,state.dip,0);state.plunger.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.rotate(Axis.XP.rotationDegrees(state.pitch));pose.translate(0,-1/16d,0);pose.rotate(Axis.XP.rotationDegrees(state.wobbleX));pose.rotate(Axis.ZP.rotationDegrees(state.wobbleZ));pose.translate(0,1/16d,0);pose.scale(.995f,.995f,.995f);pose.translate(-.5,-.5,-.5);state.bell.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
}
