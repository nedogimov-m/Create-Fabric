package com.simibubi.create.content.schematics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;

/** Native model/BE extraction. A submitted frame contains no live world or block entity. */
public final class SchematicRenderer {
    public record BlockEntity(BlockPos pos, BlockEntityRenderState state, BlockEntityRenderer renderer) {}
    public record Frame(List<PartialModelRenderState> blocks, List<BlockEntity> blockEntities, int blockCount) {
        public void submit(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            for (var block : blocks) block.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            for (var block : blockEntities) {
                pose.pushPose(); pose.translate(block.pos.getX(), block.pos.getY(), block.pos.getZ());
                block.renderer.submit(block.state, pose, collector, camera); pose.popPose();
            }
        }
    }
    private final SchematicRenderWorld world;
    private List<PartialModelRenderState> baked = List.of();
    private boolean changed = true;
    public SchematicRenderer(SchematicRenderWorld world) { this.world = world; }
    public void update() { changed = true; world.models.clear(); }
    public Frame extract(float partial, Vec3 localCamera) {
        world.tickClientAnimations();
        // Emit into the actual Fabric mesh once; transformed state variants preserve mirror winding.
        if (changed) {
            var solid = Renderer.get().mutableMesh(); var translucent = Renderer.get().mutableMesh(); int count = 0;
            for (var pos : world.positions()) {
                var state = world.getBlockState(pos); if (state.getRenderShape() != RenderShape.MODEL) continue;
                var model = world.models.extract(world, pos, state); var emitter = (model.translucent ? translucent : solid).emitter();
                emitter.pushTransform(quad -> { quad.translate(pos.getX(), pos.getY(), pos.getZ()); return true; });
                model.mesh.outputTo(emitter); emitter.popTransform(); count++;
            }
            var blocks = new ArrayList<PartialModelRenderState>();
            if (solid.size() != 0) { var model = new PartialModelRenderState(); model.mesh = solid.immutableCopy(); blocks.add(model); }
            if (translucent.size() != 0) { var model = new PartialModelRenderState(); model.mesh = translucent.immutableCopy(); model.translucent = true; blocks.add(model); }
            baked = List.copyOf(blocks); changed = false; blockCount = count;
        }
        var rendered = new ArrayList<BlockEntity>(); var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        for (var be : world.blockEntities()) {
            BlockEntityRenderer renderer = dispatcher.getRenderer(be); if (renderer == null || !renderer.shouldRender(be, localCamera)) continue;
            BlockEntityRenderState state = renderer.createRenderState(); renderer.extractRenderState(be, state, partial, localCamera, null);
            rendered.add(new BlockEntity(be.getBlockPos(), state, renderer));
        }
        return new Frame(baked, List.copyOf(rendered), blockCount);
    }
    private int blockCount;
}
