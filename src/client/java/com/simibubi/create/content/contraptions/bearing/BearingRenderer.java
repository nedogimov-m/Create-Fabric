package com.simibubi.create.content.contraptions.bearing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Shared mechanical/windmill/clockwork native snapshots. No live-world access during submit. */
public final class BearingRenderer<T extends KineticBlockEntity & IBearingBlockEntity> implements BlockEntityRenderer<T, BearingRenderer.State> {
    public BearingRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState top = new PartialModelRenderState(), shaft = new PartialModelRenderState();
        public Direction facing; public float angle, shaftAngle; public boolean wooden;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(T bearing, State state, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(bearing, state, partial, camera, overlay);
        state.facing = bearing.getBlockState().getValue(BearingBlock.FACING); state.angle = bearing.getInterpolatedAngle(partial); state.shaftAngle = bearing.getAngle(partial); state.wooden = bearing.isWoodenTop();
        AllPartialModels.extract(state.wooden ? AllPartialModels.BEARING_TOP_WOODEN : AllPartialModels.BEARING_TOP, state.top); AllPartialModels.extract(AllPartialModels.SHAFT_HALF, state.shaft);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(.5, .5, .5); pose.rotateDegrees(switch (state.facing.getAxis()) { case X -> Axis.XP; case Y -> Axis.YP; case Z -> Axis.ZP; }, state.angle);
        pose.rotate(new Quaternionf().rotationTo(0, 1, 0, state.facing.getStepX(), state.facing.getStepY(), state.facing.getStepZ())); pose.translate(-.5, -.5, -.5);
        state.top.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        pose.pushPose(); pose.translate(.5, .5, .5); pose.rotateDegrees(switch (state.facing.getAxis()) { case X -> Axis.XP; case Y -> Axis.YP; case Z -> Axis.ZP; }, state.shaftAngle);
        var back = state.facing.getOpposite(); pose.rotate(new Quaternionf().rotationTo(0, 0, 1, back.getStepX(), back.getStepY(), back.getStepZ())); pose.translate(-.5, -.5, -.5);
        state.shaft.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
    }
}
