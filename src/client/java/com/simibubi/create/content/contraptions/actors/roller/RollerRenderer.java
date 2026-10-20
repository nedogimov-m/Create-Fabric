package com.simibubi.create.content.contraptions.actors.roller;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.*;

public final class RollerRenderer implements BlockEntityRenderer<RollerBlockEntity, RollerRenderer.State> {
    private final ItemModelResolver items;
    public RollerRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState wheel = new PartialModelRenderState(), frame = new PartialModelRenderState(), modeIcon = new PartialModelRenderState();
        public final ItemStackRenderState material = new ItemStackRenderState();
        public Direction facing;
        public Vec3 wheelOffset = new Vec3(0, -.25, 0), materialPos, modePos;
        public float angle, itemOffset;
        public boolean panels = true, materialHovered, modeHovered;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public void extractRenderState(RollerBlockEntity entity, State state, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partial, camera, overlay);
        state.facing = entity.getBlockState().getValue(RollerBlock.FACING); state.angle = (float) ((entity.getLevel().getGameTime() + partial) / 20d * entity.getAnimatedSpeed() % 360);
        state.wheelOffset = new Vec3(0, -.25, 0); state.panels = true;
        AllPartialModels.extract(AllPartialModels.ROLLER_WHEEL, state.wheel); AllPartialModels.extract(AllPartialModels.ROLLER_FRAME, state.frame);
        AllPartialModels.extract(AllPartialModels.ROLLER_MODE_ICONS.get(entity.getMode().ordinal()), state.modeIcon);
        var sample = entity.filtering.getFilter(); state.material.clear(); if(ValueBoxRenderer.isFilterInRange(entity))items.updateForTopItem(state.material, sample, ItemDisplayContext.FIXED, entity.getLevel(), null, 0); state.itemOffset = ValueBoxRenderer.customZOffset(sample.getItem());
        state.materialPos = RollerBlock.panelOffset(entity.getBlockState(), true); state.modePos = RollerBlock.panelOffset(entity.getBlockState(), false);
        var client = Minecraft.getInstance(); state.materialHovered = state.modeHovered = false;
        if (client.player != null && !client.player.isSpectator() && client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(entity.getBlockPos())) {
            state.materialHovered = RollerBlock.testPanelHit(entity.getBlockState(), entity.getBlockPos(), hit, true); state.modeHovered = RollerBlock.testPanelHit(entity.getBlockState(), entity.getBlockPos(), hit, false);
        }
    }
    public void extractActorRenderState(RollerBlockEntity entity, State state, MovementContext context, float partial, Vec3 camera) {
        extractRenderState(entity, state, partial, camera, null); state.panels = false;
        float speed = context.getAnimationSpeed() * (DrillMovementBehaviour.backwards(context, state.facing) ? -1 : 1);
        if (context.contraption.stalled) speed = 0;
        state.angle = (float) ((context.world.getGameTime() + partial) / 20d * speed % 360);
        state.wheelOffset = context.contraption.entity.reverseRotation(new Vec3(0, -.25, 0), partial);
    }
    public static void transformWheel(PoseStack pose, Direction facing, float angle, Vec3 offset) {
        pose.translate(offset.x, offset.y, offset.z); var forward = facing.getUnitVec3().scale(17 / 16d); pose.translate(forward.x, forward.y, forward.z);
        float yaw = switch (facing) { case NORTH -> 180; case EAST -> 90; case WEST -> 270; default -> 0; };
        pose.translate(.5, .5, .5); pose.rotateDegrees(Axis.YP, yaw); pose.translate(-.5, -.5, -.5); pose.rotateDegrees(Axis.XP, -angle);
        pose.translate(0, -.5, .5); pose.rotateDegrees(Axis.YP, 90);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); transformWheel(pose, state.facing, state.angle, state.wheelOffset); state.wheel.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        pose.pushPose(); pose.translate(.5, .5, .5); pose.rotateDegrees(Axis.YP, RollerBlock.yaw(state.facing)); pose.translate(-.5, -.5, -.5); state.frame.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        if (!state.panels) return;
        pose.pushPose(); pose.translate(state.modePos.x, 1.003, state.modePos.z); pose.rotateDegrees(Axis.YP, RollerBlock.yaw(state.facing)); pose.scale(.375f, .375f, .375f); pose.translate(-.5, 0, -.5); state.modeIcon.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        if (!state.material.isEmpty()) { pose.pushPose(); pose.translate(state.materialPos.x, 1.015, state.materialPos.z); pose.rotateDegrees(Axis.YP, RollerBlock.yaw(state.facing)); pose.rotateDegrees(Axis.XP, 90); pose.scale(.375f, .375f, .375f); ValueBoxRenderer.renderItemIntoValueBox(state.material, pose, collector, LightCoordsUtil.FULL_BRIGHT, state.itemOffset); pose.popPose(); }
        if (state.modeHovered) { var p = state.modePos; pose.pushPose(); pose.translate(p.x, 1.006, p.z); collector.submitShapeOutline(pose, net.minecraft.world.phys.shapes.Shapes.box(-1 / 6d, 0, -1 / 6d, 1 / 6d, .002, 1 / 6d), net.minecraft.client.renderer.rendertype.RenderTypes.lines(), 0xFFFFFFFF, 1.5f, false); pose.popPose(); }
    }
}
