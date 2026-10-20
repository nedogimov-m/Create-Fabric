package com.simibubi.create.content.kinetics.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import com.simibubi.create.content.kinetics.saw.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.*;

/** Backend-independent extraction and submission for drill heads, saw blades and travelling items. */
public final class CuttingToolRenderer implements BlockEntityRenderer<KineticBlockEntity, CuttingToolRenderer.State> {
    private final ItemModelResolver items;
    public CuttingToolRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState tool = new PartialModelRenderState(), shaft = new PartialModelRenderState();
        public final List<ItemStackRenderState> items = new ArrayList<>();
        public final ItemStackRenderState filter = new ItemStackRenderState();
        Vec3 filterPosition;
        float filterRotation;
        Direction facing, movement;
        Direction.Axis axis;
        boolean drill, rotateBlade, halfShaft;
        public float angle, progress;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(KineticBlockEntity entity, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, overlay);
        var block = entity.getBlockState();
        state.drill = block.getBlock() instanceof DrillBlock;
        state.facing = block.getValue(DrillBlock.FACING);
        state.axis = ((IRotate) block.getBlock()).getRotationAxis(block);
        state.angle = entity.getAngle(partialTick) + KineticBlockEntityRenderer.rotationOffset(block, state.axis, entity.getBlockPos());
        state.shaft.clear(); state.items.clear(); state.filter.clear(); state.rotateBlade = false;
        if (state.drill) { AllPartialModels.extract(AllPartialModels.DRILL_HEAD, state.tool); return; }
        float speed = entity.getSpeed();
        boolean horizontal = state.facing.getAxis().isHorizontal();
        var key = horizontal ? speed > 0 ? AllPartialModels.SAW_HORIZONTAL_ACTIVE : speed < 0 ? AllPartialModels.SAW_HORIZONTAL_REVERSED : AllPartialModels.SAW_HORIZONTAL_INACTIVE
                : speed > 0 ? AllPartialModels.SAW_VERTICAL_ACTIVE : speed < 0 ? AllPartialModels.SAW_VERTICAL_REVERSED : AllPartialModels.SAW_VERTICAL_INACTIVE;
        AllPartialModels.extract(key, state.tool);
        state.halfShaft = horizontal;
        if (horizontal) AllPartialModels.extract(AllPartialModels.SHAFT_HALF, state.shaft);
        else AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, state.axis), state.shaft);
        state.rotateBlade = !horizontal && block.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE);
        if (!(entity instanceof SawBlockEntity saw) || !saw.canProcess()) return;
        if (com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer.isFilterInRange(saw) && !saw.getFilter().isEmpty()) {
            boolean first = block.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE), flipped = block.getValue(SawBlock.FLIPPED);
            double offset = flipped ? -3.0 / 16 : 3.0 / 16;
            state.filterPosition = new Vec3(0.5 + (first ? offset : 0), 12.55 / 16, 0.5 + (first ? 0 : offset));
            state.filterRotation = (first ? 90 : 0) + (flipped ? 0 : 180);
            items.updateForTopItem(state.filter, saw.getFilter(), ItemDisplayContext.FIXED, entity.getLevel(), null, 0);
        }
        state.movement = saw.getItemMovementFacing();
        float duration = saw.inventory.recipeDuration;
        float remaining = saw.inventory.remainingTime - partialTick * Math.clamp(Math.abs(speed) / 24, 1, 128);
        float ratio = duration == 0 ? 1 : Math.clamp(remaining / duration, 0.125f, 1);
        state.progress = speed == 0 ? 0.5f : saw.inventory.appliedRecipe ? 1 - ratio / 2 : (1 - ratio) / 2;
        var stacks = new ArrayList<>(saw.inventory.outputs());
        if (!saw.inventory.input().isEmpty()) stacks.addFirst(saw.inventory.input());
        int seed = 0;
        for (var stack : stacks) {
            var item = new ItemStackRenderState(); items.updateForTopItem(item, stack, ItemDisplayContext.FIXED, entity.getLevel(), null, seed++);
            state.items.add(item);
        }
    }
    private static void face(PoseStack pose, Direction facing) { pose.rotate(new Quaternionf().rotationTo(0, 0, 1, facing.getStepX(), facing.getStepY(), facing.getStepZ())); }
    public void extractActorRenderState(KineticBlockEntity entity,State state,com.simibubi.create.content.contraptions.behaviour.MovementContext context,float partial,Vec3 camera){
        extractRenderState(entity,state,partial,camera,null);state.items.clear();state.filter.clear();state.shaft.clear();
        boolean backwards=com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour.backwards(context,state.facing);
        float speed=context.contraption.stalled||!backwards?context.getAnimationSpeed():0;
        state.angle=(float)(((context.world.getGameTime()+partial)/20d*speed)%360)*(state.facing.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:-1);
        if(state.drill)return;
        var facing=context.rotation.apply(state.facing.getUnitVec3());boolean horizontal=Direction.getApproximateNearest(facing).getAxis().isHorizontal();
        boolean animate=context.contraption.stalled&&horizontal||!context.contraption.stalled&&!backwards&&context.getAnimationSpeed()!=0;
        var key=state.facing.getAxis().isHorizontal()?animate?AllPartialModels.SAW_HORIZONTAL_ACTIVE:AllPartialModels.SAW_HORIZONTAL_INACTIVE:animate?AllPartialModels.SAW_VERTICAL_ACTIVE:AllPartialModels.SAW_VERTICAL_INACTIVE;
        AllPartialModels.extract(key,state.tool);
    }
    private static void spin(PoseStack pose, Direction.Axis axis, float angle) { pose.rotate(switch (axis) { case X -> Axis.XP.rotationDegrees(angle); case Y -> Axis.YP.rotationDegrees(angle); case Z -> Axis.ZP.rotationDegrees(angle); }); }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(0.5, 0.5, 0.5);
        if (state.drill) spin(pose, state.axis, state.angle);
        if (state.rotateBlade) pose.rotateDegrees(Axis.YP, 90);
        face(pose, state.facing); pose.translate(-0.5, -0.5, -0.5);
        state.tool.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        if (!state.shaft.isEmpty()) {
            pose.pushPose(); pose.translate(0.5, 0.5, 0.5); spin(pose, state.axis, state.angle);
            if (state.halfShaft) face(pose, state.facing.getOpposite());
            pose.translate(-0.5, -0.5, -0.5);
            state.shaft.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        }
        for (int i = 0; i < state.items.size(); i++) {
            var item = state.items.get(i); float spread = state.items.size() < 2 ? 0 : -0.25f + 0.5f * i / (state.items.size() - 1);
            pose.pushPose();
            pose.translate(0.5 + state.movement.getStepX() * (state.progress - 0.5) + state.movement.getStepZ() * spread,
                    item.usesBlockLight() ? 0.925 : 13.0 / 16, 0.5 + state.movement.getStepZ() * (state.progress - 0.5) - state.movement.getStepX() * spread);
            pose.scale(0.5f, 0.5f, 0.5f); pose.rotateDegrees(Axis.XP, 90);
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, i); pose.popPose();
        }
        if (!state.filter.isEmpty()) {
            pose.pushPose(); pose.translate(state.filterPosition.x, state.filterPosition.y, state.filterPosition.z);
            pose.rotateDegrees(Axis.YP, state.filterRotation); pose.rotateDegrees(Axis.XP, 90); pose.scale(0.25f, 0.25f, 0.25f);
            state.filter.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        }
    }
}
