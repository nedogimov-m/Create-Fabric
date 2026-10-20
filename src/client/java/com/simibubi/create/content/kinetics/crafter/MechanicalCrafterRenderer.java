package com.simibubi.create.content.kinetics.crafter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity.Phase;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** World/model queries are confined to extraction; submitted meshes and item states are detached. */
public final class MechanicalCrafterRenderer implements BlockEntityRenderer<MechanicalCrafterBlockEntity, MechanicalCrafterRenderer.State> {
    private final ItemModelResolver items;
    public MechanicalCrafterRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState cog = new PartialModelRenderState(), lid = new PartialModelRenderState(), arrow = new PartialModelRenderState(), belt = new PartialModelRenderState(), beltFrame = new PartialModelRenderState();
        public Direction facing;
        public float yaw, pointingRotation, cogAngle;
        public Vec3 itemCenter;
        public final List<Item> items = new ArrayList<>();
    }
    public record Item(ItemStackRenderState model, float x, float y, float z, float scale, float rotation) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(MechanicalCrafterBlockEntity crafter, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(crafter, state, partialTick, camera, overlay);
        var block = crafter.getBlockState(); state.facing = block.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        state.yaw = switch (state.facing) { case SOUTH -> 0; case WEST -> -90; case NORTH -> 180; default -> 90; };
        state.pointingRotation = block.getValue(MechanicalCrafterBlock.POINTING).getXRotation();
        int parity = crafter.getBlockPos().getY() + (state.facing.getAxis() == Direction.Axis.X ? crafter.getBlockPos().getZ() : crafter.getBlockPos().getX());
        state.cogAngle = crafter.getAngle(partialTick) + (parity % 2 == 0 ? 22.5f : 0);
        AllPartialModels.extract(AllPartialModels.SHAFTLESS_COGWHEEL, state.cog);
        state.lid.clear(); state.arrow.clear(); state.belt.clear(); state.beltFrame.clear();
        if ((crafter.covered || crafter.phase != Phase.IDLE) && crafter.phase != Phase.CRAFTING && crafter.phase != Phase.INSERTING) AllPartialModels.extract(AllPartialModels.MECHANICAL_CRAFTER_LID, state.lid);
        var target = RecipeGridHandler.getTargetingCrafter(crafter);
        if (target != null && Math.abs(target.getBlockState().getValue(MechanicalCrafterBlock.POINTING).getXRotation() - state.pointingRotation) != 180) {
            AllPartialModels.extract(AllPartialModels.MECHANICAL_CRAFTER_BELT, state.belt);
            AllPartialModels.extract(AllPartialModels.MECHANICAL_CRAFTER_BELT_FRAME, state.beltFrame);
            if (crafter.phase == Phase.EXPORTING) {
                var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
                var sprite = atlas.getSprite(Create.asResource("block/crafter_thingies"));
                float frame = ((int) (crafter.getCountDownSpeed() / 128f * crafter.getLevel().getGameTime()) % 4) / 4f;
                var mesh = Renderer.get().mutableMesh(); var emitter = mesh.emitter();
                state.belt.mesh.forEach(quad -> {
                    emitter.copyFrom(quad);
                    for (int i = 0; i < 4; i++) emitter.uv(i, quad.u(i) + (sprite.getU1() - sprite.getU0()) * frame, quad.v(i));
                    emitter.emit();
                }); state.belt.mesh = mesh.immutableCopy();
            }
        } else AllPartialModels.extract(AllPartialModels.MECHANICAL_CRAFTER_ARROW, state.arrow);
        state.itemCenter = new Vec3(0.5 + state.facing.getStepX() * 0.58, 0.5, 0.5 + state.facing.getStepZ() * 0.58);
        if (crafter.phase == Phase.EXPORTING) {
            float progress = Math.clamp((1000 - crafter.countDown + crafter.getCountDownSpeed() * partialTick) / 1000f, 0, 1);
            var direction = crafter.getTargetDirection(); state.itemCenter = state.itemCenter.add(direction.getStepX() * progress * 0.75, direction.getStepY() * progress * 0.75, direction.getStepZ() * progress * 0.75);
        }
        state.items.clear();
        if (crafter.phase == Phase.IDLE) { addItem(crafter, state, crafter.inventory.stack(), 0, 0, -1f / 256, 1, 0); return; }
        var grouped = crafter.phase == Phase.CRAFTING ? crafter.groupedItemsBeforeCraft : crafter.groupedItems;
        float spacing = 0.5f, scale = 1, centerX = 0, centerY = 0;
        if (crafter.phase == Phase.CRAFTING) {
            float progress = Math.clamp((2000 - crafter.countDown + crafter.getCountDownSpeed() * partialTick) / 1000f, 0, 1);
            scale = 1 - Math.clamp(progress * 2 - 1, 0, 1);
            int minX = grouped.grid.keySet().stream().mapToInt(RecipeGridHandler.Cell::x).min().orElse(0), maxX = grouped.grid.keySet().stream().mapToInt(RecipeGridHandler.Cell::x).max().orElse(0);
            int minY = grouped.grid.keySet().stream().mapToInt(RecipeGridHandler.Cell::y).min().orElse(0), maxY = grouped.grid.keySet().stream().mapToInt(RecipeGridHandler.Cell::y).max().orElse(0);
            centerX = -(minX + maxX) / 4f * Math.clamp(progress * 2, 0, 1); centerY = -(minY + maxY) / 4f * Math.clamp(progress * 2, 0, 1);
            spacing += (-4 * (progress - 0.5f) * (progress - 0.5f) + 1) * 0.25f;
        }
        for (var entry : grouped.grid.entrySet()) {
            int x = entry.getKey().x(), y = entry.getKey().y();
            if (crafter.phase == Phase.INSERTING && (x != 0 || y != 0)) continue;
            addItem(crafter, state, entry.getValue(), (centerX + x * spacing) * scale, (centerY + y * spacing) * scale, (x + y * 3) / 1024f, scale, 0);
        }
        if (crafter.phase == Phase.CRAFTING) {
            float progress = Math.clamp((1000 - crafter.countDown + crafter.getCountDownSpeed() * partialTick) / 1000f, 0, 1);
            float early = Math.clamp(progress * 2, 0, 1), late = Math.clamp(progress * 2 - 1, 0, 1);
            addItem(crafter, state, crafter.groupedItems.grid.getOrDefault(new RecipeGridHandler.Cell(0, 0), ItemStack.EMPTY), 0, 0, 0, early * 1.125f * (1 + (1 - late) * 0.125f), early * 720);
        }
    }
    private void addItem(MechanicalCrafterBlockEntity crafter, State state, ItemStack stack, float x, float y, float z, float scale, float rotation) {
        if (stack.isEmpty() || scale <= 0) return;
        var model = new ItemStackRenderState(); items.updateForTopItem(model, stack, ItemDisplayContext.FIXED, crafter.getLevel(), null, 0);
        state.items.add(new Item(model, x, y, z, scale, rotation));
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(0.5, 0.5, 0.5);
        pose.rotate((state.facing.getAxis() == Direction.Axis.X ? Axis.XP : Axis.ZP).rotationDegrees(state.cogAngle));
        if (state.facing.getAxis() == Direction.Axis.X) pose.rotate(Axis.YP.rotationDegrees(90));
        pose.rotate(Axis.XP.rotationDegrees(90)); pose.translate(-0.5, -0.5, -0.5);
        state.cog.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        pose.pushPose(); pose.translate(0.5, 0.5, 0.5); pose.rotate(Axis.YP.rotationDegrees(state.yaw + 90)); pose.rotate(Axis.XP.rotationDegrees(state.pointingRotation)); pose.translate(-0.5, -0.5, -0.5);
        for (var model : List.of(state.lid, state.arrow, state.belt, state.beltFrame)) if (!model.isEmpty()) model.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        pose.pushPose(); pose.translate(state.itemCenter.x, state.itemCenter.y, state.itemCenter.z); pose.scale(0.5f, 0.5f, 0.5f); pose.rotate(Axis.YP.rotationDegrees(state.yaw));
        for (var item : state.items) {
            pose.pushPose(); pose.translate(item.x, item.y, 0); pose.rotate(Axis.ZP.rotationDegrees(item.rotation)); pose.scale(item.scale, item.scale, item.scale);
            pose.rotate(Axis.YP.rotationDegrees(180)); pose.translate(0, 0, item.z); item.model.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        }
        pose.popPose();
    }
}
