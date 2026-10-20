package com.simibubi.create.content.contraptions.actors.contraptionControls;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.*;

/** Native detached meshes/items for both stationary and virtual contraption block entities. */
public final class ContraptionControlsRenderer implements BlockEntityRenderer<ContraptionControlsBlockEntity, ContraptionControlsRenderer.State> {
    private final ItemModelResolver items;
    public ContraptionControlsRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState button = new PartialModelRenderState(), indicator = new PartialModelRenderState();
        public final ItemStackRenderState filter = new ItemStackRenderState();
        public Direction facing;
        public Vec3 movement = Vec3.ZERO, filterPosition = Vec3.ZERO;
        public float buttonDepth, itemOffset;
        public int indicatorStage;
        public boolean filterHovered,elevatorMode,floorUnreachable;
        public net.minecraft.util.FormattedCharSequence floorLabel;
        public float floorX;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ContraptionControlsBlockEntity controls, State state, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(controls, state, partial, camera, overlay);
        var block = controls.getBlockState(); state.facing = block.getValue(ContraptionControlsBlock.FACING);
        state.buttonDepth = -controls.getButton(partial) / 24;
        state.indicatorStage = Math.floorMod((int) controls.getIndicator() / 45, 8);
        var yaw = Math.toRadians(ContraptionControlsBlock.yaw(state.facing.getOpposite()));
        state.movement = new Vec3(-.325 * Math.sin(yaw), 1, -.325 * Math.cos(yaw));
        AllPartialModels.extract(AllPartialModels.CONTRAPTION_CONTROLS_BUTTON, state.button);
        AllPartialModels.extract(AllPartialModels.CONTRAPTION_CONTROLS_INDICATOR.get(state.indicatorStage), state.indicator);
        state.elevatorMode=controls.elevatorMode;state.floorUnreachable=controls.elevatorFloorUnreachable;state.floorLabel=net.minecraft.network.chat.Component.literal(controls.elevatorFloorLabel).getVisualOrderText();state.floorX=-Minecraft.getInstance().font.width(controls.elevatorFloorLabel)/2f;
        state.filter.clear(); state.filterHovered = false;
        var sample = controls.filtering.getFilter();
        if (!state.elevatorMode&&!sample.isEmpty()) items.updateForTopItem(state.filter, sample, ItemDisplayContext.GUI, controls.getLevel(), null, 0);
        state.itemOffset = ValueBoxRenderer.customZOffset(sample.getItem());
        state.filterPosition = ContraptionControlsBlock.filterPosition(block).add(state.movement.scale(-.07 + state.buttonDepth));
        var client = Minecraft.getInstance();
        if (client.player != null && !client.player.isSpectator() && client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(controls.getBlockPos()))
            state.filterHovered = ContraptionControlsBlock.onFilter(block, controls.getBlockPos(), hit.getLocation());
    }
    private static void face(PoseStack pose, Direction facing) {
        pose.translate(.5, .5, .5); pose.rotateDegrees(Axis.YP, ContraptionControlsBlock.yaw(facing.getOpposite())); pose.translate(-.5, -.5, -.5);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); var delta = state.movement.scale(state.buttonDepth); pose.translate(delta.x, delta.y, delta.z); face(pose, state.facing);
        state.button.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        pose.pushPose(); face(pose, state.facing); state.indicator.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        if(state.elevatorMode){pose.pushPose();pose.translate(state.filterPosition.x,state.filterPosition.y,state.filterPosition.z);pose.rotateDegrees(Axis.YP,ContraptionControlsBlock.yaw(state.facing)+180);pose.rotateDegrees(Axis.XP,67.5f);pose.scale(-.0125f,-.0125f,.0125f);collector.submitText(pose,state.floorX,-4,state.floorLabel,false,net.minecraft.client.gui.Font.DisplayMode.NORMAL,state.lightCoords,state.floorUnreachable?0xFFEA5C2B:0xFFEDEDED,0,0xFF222222);pose.popPose();}
        if (!state.elevatorMode&&(!state.filter.isEmpty() || state.filterHovered)) {
            pose.pushPose(); pose.translate(state.filterPosition.x, state.filterPosition.y, state.filterPosition.z);
            pose.rotateDegrees(Axis.YP, ContraptionControlsBlock.yaw(state.facing) + 180); pose.rotateDegrees(Axis.XP, 67.5f);
            pose.scale(.508f, .508f, .508f);
            if (!state.filter.isEmpty()) ValueBoxRenderer.renderFlatItemIntoValueBox(state.filter, pose, collector, state.lightCoords);
            pose.popPose();
        }
    }
}
