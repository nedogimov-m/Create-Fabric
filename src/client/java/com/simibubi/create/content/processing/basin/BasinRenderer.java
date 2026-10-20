package com.simibubi.create.content.processing.basin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class BasinRenderer implements BlockEntityRenderer<BasinBlockEntity, BasinRenderer.State> {
    private final ItemModelResolver items;
    public BasinRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<FluidSurface> fluids = new ArrayList<>();
        final List<OutputItem> outputs = new ArrayList<>();
        float rotation;
        float height;
        public int itemCount(){return items.size();}
        public int fluidCount(){return fluids.size();}
    }
    private record FluidSurface(Mesh mesh, boolean translucent, int light) {}
    private record OutputItem(ItemStackRenderState model, double x, double y, double z, float yaw, float pitch) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(BasinBlockEntity basin, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(basin, state, partialTick, cameraPos, overlay);
        state.items.clear(); state.fluids.clear(); state.outputs.clear();
        state.rotation = basin.getLevel().getGameTime() + partialTick;
        var occupied = basin.fluids.allTanks().stream().filter(t -> !t.isResourceBlank()).toList();
        long total = occupied.stream().mapToLong(t -> t.getAmount()).sum();
        state.height = 3f / 16 + (float) total / (net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 4) * 10f / 16;
        for (int index = 0; index < occupied.size(); index++) {
            var fluid = occupied.get(index).getResource().getFluid();
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            int color = model.tintSource() == null ? -1 : model.tintSource().colorInWorld(basin.getBlockState(),
                    (net.minecraft.client.renderer.block.BlockAndTintGetter) basin.getLevel(), basin.getBlockPos()) | 0xFF000000;
            var mesh = Renderer.get().mutableMesh();
            var emitter = mesh.emitter();
            float left = 2f / 16 + 12f / 16 * index / occupied.size();
            float right = 2f / 16 + 12f / 16 * (index + 1) / occupied.size();
            emitter.square(Direction.UP, left, 2f / 16, right, 14f / 16, 1 - state.height);
            emitter.uvUnitSquare().materialBake(model.stillMaterial(), MutableQuadView.BAKE_NORMALIZED)
                    .color(color, color, color, color).chunkLayer(model.layer());
            emitter.emit();
            boolean lava = fluid.is(net.minecraft.tags.FluidTags.LAVA);
            state.fluids.add(new FluidSurface(mesh.immutableCopy(), model.layer() == net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT,
                    lava ? net.minecraft.util.LightCoordsUtil.FULL_BRIGHT : state.lightCoords));
        }
        for (var slot : basin.allItems()) {
            if (slot.isResourceBlank()) continue;
            var item = new ItemStackRenderState();
            items.updateForTopItem(item, slot.stack(), ItemDisplayContext.FIXED, basin.getLevel(), null, 0);
            state.items.add(item);
        }
        // Exact source output trajectory extracted into detached native item states.
        Direction direction = basin.getBlockState().getValue(BasinBlock.FACING);
        if (direction == Direction.DOWN) return;
        Vec3 directionVector = Vec3.atLowerCornerOf(direction.getUnitVec3i());
        Vec3 outputVector = Vec3.atCenterOf(net.minecraft.core.BlockPos.ZERO)
            .add(directionVector.scale(.55).subtract(0, .5, 0));
        boolean outputToBasin = basin.getLevel().getBlockState(basin.getBlockPos().relative(direction))
            .getBlock() instanceof BasinBlock;
        for (var snapshot : List.copyOf(basin.visualizedOutputItems)) {
            float progress = 1 - (snapshot.getFirst() - partialTick) / BasinBlockEntity.OUTPUT_ANIMATION_TIME;
            if (!outputToBasin && progress > .35f) continue;
            Vec3 position = outputVector.add(0, Math.max(-.55f, -(progress * progress * 2)), 0)
                .add(directionVector.scale(progress * .5f));
            var item = new ItemStackRenderState();
            items.updateForTopItem(item, snapshot.getValue(), ItemDisplayContext.GROUND, basin.getLevel(), null, 0);
            state.outputs.add(new OutputItem(item, position.x, position.y, position.z,
                net.createmod.catnip.math.AngleHelper.horizontalAngle(direction), progress * 180));
        }
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (var surface : state.fluids) {
            collector.submitBlockModel(pose, ChunkSectionLayerHelper::getMovingBlockRenderType, surface.translucent(),
                    List.of(), surface.mesh(), new int[0], surface.light(), OverlayTexture.NO_OVERLAY, 0);
        }
        for (var output : state.outputs) {
            pose.pushPose();
            pose.translate(output.x(), output.y(), output.z());
            pose.rotateDegrees(Axis.YP, output.yaw());
            pose.rotateDegrees(Axis.XP, output.pitch());
            output.model().submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        for (int i = 0; i < state.items.size(); i++) {
            double angle = Math.toRadians(state.rotation + i * 360.0 / state.items.size());
            pose.pushPose();
            pose.translate(0.5 + Math.sin(angle) * 0.22, state.height + 0.04, 0.5 + Math.cos(angle) * 0.22);
            pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(angle));
            pose.rotateDegrees(Axis.XP, 90);
            pose.scale(0.3f, 0.3f, 0.3f);
            state.items.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
