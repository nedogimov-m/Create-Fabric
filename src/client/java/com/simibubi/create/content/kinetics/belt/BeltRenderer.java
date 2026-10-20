package com.simibubi.create.content.kinetics.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Scrolls atlas UVs while extracting; submission retains no references to the live world. */
public final class BeltRenderer implements BlockEntityRenderer<BeltBlockEntity, BeltRenderer.State> {
    private final ItemModelResolver items;
    public BeltRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState top = new PartialModelRenderState();
        public final PartialModelRenderState bottom = new PartialModelRenderState();
        public final PartialModelRenderState pulley = new PartialModelRenderState();
        public final PartialModelRenderState casing = new PartialModelRenderState();
        public final PartialModelRenderState cover = new PartialModelRenderState();
        public Direction facing;
        public Direction.Axis axle;
        public BeltSlope slope;
        public float yaw, pitch, roll, pulleyAngle;
        public final List<Item> items = new ArrayList<>();
    }
    public record Item(ItemStackRenderState model, Vec3 position, float angle, float tilt, boolean upright, boolean block, boolean packageItem, int light) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(BeltBlockEntity belt, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(belt, state, partialTick, cameraPos, overlay);
        var block = belt.getBlockState();
        var part = block.getValue(BeltBlock.PART);
        state.facing = block.getValue(BeltBlock.HORIZONTAL_FACING); state.slope = block.getValue(BeltBlock.SLOPE);
        state.axle = ((BeltBlock) block.getBlock()).getRotationAxis(block);
        boolean start = part == BeltPart.START, end = part == BeltPart.END;
        boolean downward = state.slope == BeltSlope.DOWNWARD, upward = state.slope == BeltSlope.UPWARD;
        boolean sideways = state.slope == BeltSlope.SIDEWAYS, diagonal = state.slope.isDiagonal();
        if (downward || state.slope == BeltSlope.VERTICAL && state.facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) { boolean temp = start; start = end; end = temp; }
        float facingYaw = switch (state.facing) { case SOUTH -> 0; case WEST -> 270; case NORTH -> 180; default -> 90; };
        state.yaw = facingYaw + (upward ? 180 : 0) + (sideways ? 270 : 0);
        state.roll = sideways ? 90 : 0;
        state.pitch = !diagonal && state.slope != BeltSlope.HORIZONTAL ? 90 : 0;
        double tick = belt.getLevel().getGameTime() + partialTick;
        float speed = belt.getSpeed(); boolean alongX = state.facing.getAxis() == Direction.Axis.X;
        if (diagonal && (downward ^ alongX) || !sideways && !diagonal && alongX || sideways && state.facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) speed = -speed;
        double phase = speed * tick * state.facing.getAxisDirection().getStep() / (31.5 * 16);
        var topKey = diagonal ? start ? AllPartialModels.BELT_DIAGONAL_START : end ? AllPartialModels.BELT_DIAGONAL_END : AllPartialModels.BELT_DIAGONAL_MIDDLE
                : start ? AllPartialModels.BELT_START : end ? AllPartialModels.BELT_END : AllPartialModels.BELT_MIDDLE;
        extractSurface(topKey, state.top, diagonal, false, phase, belt.getColor());
        state.bottom.clear();
        if (!diagonal) extractSurface(start ? AllPartialModels.BELT_START_BOTTOM : end ? AllPartialModels.BELT_END_BOTTOM : AllPartialModels.BELT_MIDDLE_BOTTOM, state.bottom, false, true, phase + 0.5, belt.getColor());
        state.pulley.clear();
        if (belt.hasPulley()) AllPartialModels.extract(AllPartialModels.BELT_PULLEY, state.pulley);
        state.pulleyAngle = belt.getAngle(partialTick);
        state.casing.clear(); state.cover.clear();
        if (block.getValue(BeltBlock.CASING)) {
            AllPartialModels.extract(block, state.casing);
            if (belt.getCasingType() == BeltBlockEntity.CasingType.ANDESITE) {
                var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
                var source = atlas.getSprite(Create.asResource("block/belt/brass_belt_casing"));
                var target = atlas.getSprite(Create.asResource("block/belt/andesite_belt_casing"));
                remap(state.casing, source, target, 1, 0);
            }
            var above = belt.getBlockPos().above();
            var aboveState=belt.getLevel().getBlockState(above);
            var obstruction = aboveState.getCollisionShape(belt.getLevel(), above);
            if (state.slope == BeltSlope.HORIZONTAL && !obstruction.isEmpty()
                    && !aboveState.is(com.simibubi.create.AllBlocks.CRUSHING_WHEEL_CONTROLLER)
                    && !(aboveState.getBlock() instanceof com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock)
                    && !(aboveState.getBlock() instanceof com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock&&com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock.getFunnelFacing(aboveState)!=Direction.UP)) {
                var bounds = obstruction.bounds();
                if (bounds.minY <= 0 && bounds.getXsize() >= 0.5 && bounds.getZsize() >= 0.5) {
                    boolean brass = belt.getCasingType() == BeltBlockEntity.CasingType.BRASS;
                    AllPartialModels.extract(brass ? alongX ? AllPartialModels.BRASS_BELT_COVER_X : AllPartialModels.BRASS_BELT_COVER_Z
                            : alongX ? AllPartialModels.ANDESITE_BELT_COVER_X : AllPartialModels.ANDESITE_BELT_COVER_Z, state.cover);
                }
            }
        }
        state.items.clear();
        var controller = belt.getControllerBE();
        if (controller == null || !BeltBlock.canTransportObjects(block)) return;
        for (var transported : controller.getInventory().getTransportedItems()) {
            if ((int) Math.floor(transported.beltPosition) != belt.index) continue;
            float position = belt.getSpeed() == 0 ? transported.beltPosition : transported.prevBeltPosition + (transported.beltPosition - transported.prevBeltPosition) * partialTick;
            var vector = BeltHelper.getVectorForOffset(controller, position).subtract(Vec3.atLowerCornerOf(belt.getBlockPos())).add(0, 7f / 16, 0);
            var model = new ItemStackRenderState();
            items.updateForTopItem(model, transported.stack, ItemDisplayContext.FIXED, belt.getLevel(), null, transported.angle);
            boolean onSlope = diagonal && position >= 0.5 && position <= controller.beltLength - 0.5;
            boolean upright = BeltHelper.isItemUpright(transported.stack);
            float angle = transported.angle;
            if (upright) {
                var world = vector.add(Vec3.atLowerCornerOf(belt.getBlockPos()));
                angle = (float) Math.toDegrees(Math.atan2(world.x - cameraPos.x, world.z - cameraPos.z) + Math.PI);
            }
            float tilt = !onSlope || upright ? 0 : (state.slope == BeltSlope.UPWARD ? 45 : -45)
                    * state.facing.getAxisDirection().getStep() * (state.facing.getAxis() == Direction.Axis.X ? 1 : -1);
            if (onSlope) vector = vector.add(0, 1f / 8, 0);
            state.items.add(new Item(model, vector, angle, tilt, upright, model.usesBlockLight(), com.simibubi.create.content.logistics.box.PackageItem.isPackage(transported.stack),net.minecraft.util.LightCoordsUtil.getLightCoords(belt.getLevel(), belt.getBlockPos().above())));
        }
    }
    private static void extractSurface(ExtraModelKey<BlockStateModel> key, PartialModelRenderState state, boolean diagonal, boolean bottom, double phase, net.minecraft.world.item.DyeColor color) {
        AllPartialModels.extract(key, state);
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        var source = atlas.getSprite(Create.asResource(diagonal ? "block/belt_diagonal" : bottom ? "block/belt_offset" : "block/belt"));
        var target = atlas.getSprite(Create.asResource(color == null ? diagonal ? "block/belt_diagonal_scroll" : "block/belt_scroll"
                : "block/belt/" + color.getSerializedName() + (diagonal ? "_diagonal_scroll" : "_scroll")));
        float multiplier = diagonal ? 3f / 8 : 0.5f;
        float scroll = (float) (phase - Math.floor(phase)) * multiplier;
        remap(state, source, target, multiplier, scroll);
    }
    private static void remap(PartialModelRenderState state, TextureAtlasSprite source, TextureAtlasSprite target, float multiplier, float scroll) {
        var mesh = Renderer.get().mutableMesh(); var emitter = mesh.emitter();
        state.mesh.forEach(quad -> {
            emitter.copyFrom(quad);
            for (int i = 0; i < 4; i++) {
                float u = (quad.u(i) - source.getU0()) / (source.getU1() - source.getU0());
                float v = (quad.v(i) - source.getV0()) / (source.getV1() - source.getV0());
                emitter.uv(i, target.getU(u), target.getV(v * multiplier + scroll));
            }
            emitter.emit();
        });
        state.mesh = mesh.immutableCopy();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.casing.isEmpty()) state.casing.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        if (!state.cover.isEmpty()) state.cover.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.pushPose(); pose.translate(0.5, 0.5, 0.5);
        pose.rotate(Axis.YP.rotationDegrees(state.yaw)); pose.rotate(Axis.ZP.rotationDegrees(state.roll)); pose.rotate(Axis.XP.rotationDegrees(state.pitch));
        pose.translate(-0.5, -0.5, -0.5);
        state.top.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        if (!state.bottom.isEmpty()) state.bottom.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        if (!state.pulley.isEmpty()) {
            pose.pushPose(); pose.translate(0.5, 0.5, 0.5);
            pose.rotate(switch (state.axle) { case X -> Axis.XP.rotationDegrees(state.pulleyAngle); case Y -> Axis.YP.rotationDegrees(state.pulleyAngle); case Z -> Axis.ZP.rotationDegrees(state.pulleyAngle); });
            if (state.axle == Direction.Axis.X) pose.rotate(Axis.ZP.rotationDegrees(-90));
            if (state.axle == Direction.Axis.Z) pose.rotate(Axis.XP.rotationDegrees(90));
            pose.translate(-0.5, -0.5, -0.5); state.pulley.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        }
        for (var item : state.items) {
            pose.pushPose(); pose.translate(item.position.x, item.position.y, item.position.z);
            if (item.tilt != 0) pose.rotate((state.facing.getAxis() == Direction.Axis.X ? Axis.ZP : Axis.XP).rotationDegrees(item.tilt));
            pose.rotate(Axis.YP.rotationDegrees(item.angle));
            if (item.upright) pose.translate(0, 3f / 32, 1f / 16);
            else if (!item.block) { pose.translate(0, -3f / 32, 0); pose.rotate(Axis.XP.rotationDegrees(90)); }
            if(item.packageItem)pose.translate(0,4f/16,0);float scale=item.packageItem?1.5f:.5f;pose.scale(scale,scale,scale);
            item.model.submit(pose, collector, item.light, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        }
    }
}
