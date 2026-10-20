package com.simibubi.create.content.equipment.bell;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class BellRenderer<T extends AbstractBellBlockEntity> implements BlockEntityRenderer<T,BellRenderer.State> {
    public BellRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState { public final PartialModelRenderState bell=new PartialModelRenderState();public float swing,yaw;public Direction swingAxis=Direction.WEST; }
    @Override public State createRenderState() { return new State(); }
    public static float horizontalAngle(Direction facing) { return switch(facing){case NORTH->180;case EAST->90;case WEST->270;default->0;}; }
    public static float getSwingAngle(float time) { float t=time/1.5f;return 1.2f*(float)Math.sin(t/Math.PI)/(2.5f+t/3); }
    @Override public void extractRenderState(T bell,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(bell,state,partial,camera,overlay);
        AllPartialModels.extract(bell instanceof HauntedBellBlockEntity?AllPartialModels.HAUNTED_BELL:AllPartialModels.PECULIAR_BELL,state.bell);
        state.swing=bell.isRinging?getSwingAngle(bell.ringingTicks+partial):0;state.swingAxis=bell.ringDirection.getCounterClockWise();
        var block=bell.getBlockState();var attachment=block.getValue(BellBlock.ATTACHMENT);state.yaw=horizontalAngle(block.getValue(BellBlock.FACING))+(attachment==BellAttachType.SINGLE_WALL||attachment==BellAttachType.DOUBLE_WALL?90:0);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose();pose.translate(.5,.5,.5);var normal=state.swingAxis.getUnitVec3();pose.rotate(new Quaternionf().rotationAxis(state.swing,(float)normal.x,(float)normal.y,(float)normal.z));pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.translate(-.5,-.5,-.5);state.bell.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
}
