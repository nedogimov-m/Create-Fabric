package com.simibubi.create.content.contraptions.actors.psi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Detached native meshes with the original local Y extension and six-way orientation. */
public final class PortableStorageInterfaceRenderer implements BlockEntityRenderer<PortableStorageInterfaceBlockEntity, PortableStorageInterfaceRenderer.State> {
    public PortableStorageInterfaceRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState middle = new PartialModelRenderState(), top = new PartialModelRenderState();
        public Direction facing;
        public float extension;
        public boolean lit, fluids;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(PortableStorageInterfaceBlockEntity entity, State state, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partial, camera, overlay);
        state.facing = entity.getBlockState().getValue(PortableStorageInterfaceBlock.FACING);
        state.fluids = entity.getBlockState().is(com.simibubi.create.AllBlocks.PORTABLE_FLUID_INTERFACE);
        state.extension = entity.getExtensionDistance(partial); state.lit = entity.isConnected();
        extractModels(state);
    }
    public void extractActorRenderState(PortableStorageInterfaceBlockEntity entity, State state, MovementContext context, float partial, Vec3 camera) {
        extractRenderState(entity, state, partial, camera, null);
        var animation = PortableStorageInterfaceMovement.getAnimation(context);
        state.extension = animation.getValue(partial); state.lit = animation.settled();
        extractModels(state);
    }
    private static void extractModels(State state) {
        AllPartialModels.extract(state.fluids ? (state.lit ? AllPartialModels.PORTABLE_FLUID_INTERFACE_MIDDLE_POWERED : AllPartialModels.PORTABLE_FLUID_INTERFACE_MIDDLE)
            : (state.lit ? AllPartialModels.PORTABLE_STORAGE_INTERFACE_MIDDLE_POWERED : AllPartialModels.PORTABLE_STORAGE_INTERFACE_MIDDLE), state.middle);
        AllPartialModels.extract(state.fluids ? AllPartialModels.PORTABLE_FLUID_INTERFACE_TOP : AllPartialModels.PORTABLE_STORAGE_INTERFACE_TOP, state.top);
    }
    public static void transform(PoseStack pose, Direction facing, float offset) {
        float yaw = switch (facing) { case NORTH -> 180; case EAST -> 90; case WEST -> 270; default -> 0; };
        pose.translate(.5, .5, .5); pose.rotateDegrees(Axis.YP, yaw);
        pose.rotateDegrees(Axis.XP, facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90);
        pose.translate(-.5, -.5, -.5); pose.translate(0, offset, 0);
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); transform(pose, state.facing, state.extension * .5f + .375f);
        state.middle.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        pose.pushPose(); transform(pose, state.facing, state.extension);
        state.top.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
    }
}
