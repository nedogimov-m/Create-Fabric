// Ponder1.0.82 breaking pass, MIT; actual native26.3 meshes and breaking collector.
package net.createmod.ponder.foundation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Immutable actual meshes captured before the section mask is released. */
public record PonderBreakingRenderState(Matrix4fc transform, List<Block> blocks) {
    public PonderBreakingRenderState { transform = new Matrix4f(transform); blocks = List.copyOf(blocks); }
    public record Block(BlockPos position, Mesh mesh, int progress) {
        public Block { position = position.immutable(); }
    }

    public static PonderBreakingRenderState extract(PonderLevel world, Selection selection, Matrix4fc transform) {
        var captured = new ArrayList<Block>();
        var models = new PonderBlockModels();
        world.setMask(selection);
        try {
            world.getBlockBreakingProgressions().forEach((pos, progress) -> {
                if (!selection.test(pos) || progress < 0) return;
                var state = world.getBlockState(pos);
                if (state.getRenderShape() != RenderShape.MODEL) return;
                var model = models.extract(world, pos, state);
                captured.add(new Block(pos, model.mesh, Math.clamp(progress, 0, 9)));
            });
        } finally { world.clearMask(); }
        return new PonderBreakingRenderState(transform, captured);
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose(); pose.mulPose(transform);
        for (var block : blocks) {
            pose.pushPose(); pose.translate(block.position.getX(), block.position.getY(), block.position.getZ());
            collector.submitBreakingBlockModel(pose, List.of(), block.mesh, block.progress, false);
            pose.popPose();
        }
        pose.popPose();
    }
}
