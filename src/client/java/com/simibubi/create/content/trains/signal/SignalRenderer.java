package com.simibubi.create.content.trains.signal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.OverlayState;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Actual native signal indicator and detached source straight/Bezier overlay geometry. */
public final class SignalRenderer implements BlockEntityRenderer<SignalBlockEntity, SignalRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState indicator = new PartialModelRenderState(), trackOverlay = new PartialModelRenderState();
        public Matrix4f transform = new Matrix4f();
        public boolean red, hasOverlay;
        public int indicatorLight, overlayLight;
        public OverlayState overlay;
    }
    public SignalRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(SignalBlockEntity entity, State state, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partial, camera, crumbling);
        state.red = entity.getState().isRedLight(entity.getLevel().getGameTime() + partial);
        state.indicator.clear(); state.trackOverlay.clear(); state.hasOverlay = false; state.overlay = entity.getOverlay();
        AllPartialModels.extract(state.red ? SignalModels.ON : SignalModels.OFF, state.indicator);
        state.indicatorLight = state.red ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        if (state.overlay == OverlayState.SKIP) return;
        var transform = TrackTargetingRenderer.extractTransform(entity, entity.targeting());
        if (transform == null) return;
        var pos = entity.targeting().selection().target().pos();
        var level = entity.getLevel();
        AllPartialModels.extract(state.overlay == OverlayState.DUAL ? SignalModels.DUAL : SignalModels.OVERLAY, state.trackOverlay);
        var mesh = Renderer.get().mutableMesh(); var emitter = mesh.emitter();
        state.trackOverlay.mesh.forEach(quad -> emitter.copyFrom(quad).chunkLayer(ChunkSectionLayer.CUTOUT).emit());
        state.trackOverlay.mesh = mesh.immutableCopy(); state.transform = transform;
        state.overlayLight = LightCoordsUtil.getLightCoords(level, pos); state.hasOverlay = true;
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        state.indicator.submit(pose, collector, state.indicatorLight, OverlayTexture.NO_OVERLAY, 0);
        if (!state.hasOverlay) return; pose.pushPose(); pose.mulPose(state.transform);
        state.trackOverlay.submit(pose, collector, state.overlayLight, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 96; }
}
