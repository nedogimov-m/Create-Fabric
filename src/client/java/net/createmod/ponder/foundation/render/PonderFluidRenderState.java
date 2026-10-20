// Ponder native26.3 visible-world fluid pass; actual vanilla fluid tessellator, no substitute boxes.
package net.createmod.ponder.foundation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Immutable native fluid vertices: topology, flow, native materials, biome tint and visible neighbors. */
public record PonderFluidRenderState(Matrix4fc transform, List<Layer> layers) {
    public PonderFluidRenderState { transform = new Matrix4f(transform); layers = List.copyOf(layers); }
    public record Layer(RenderType renderType, List<Vertex> vertices) { public Layer { vertices = List.copyOf(vertices); } }
    public record Vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                         float nx, float ny, float nz, boolean hasUv3, float uv3u, float uv3v,
                         boolean hasLineWidth, float lineWidth) {}

    public static PonderFluidRenderState extract(PonderLevel world, Selection selection, Matrix4fc transform) {
        var result = new ArrayList<Layer>();
        var renderer = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
        world.setMask(selection);
        try {
            for (var pos : selection) {
                var state = world.getBlockState(pos); var fluid = state.getFluidState();
                if (fluid.isEmpty()) continue;
                var captures = new LinkedHashMap<ChunkSectionLayer, Capture>();
                // Native tesselate emits section-local coordinates (actual getX/Y/Z &15 bytes).
                float originX = pos.getX() & ~15, originY = pos.getY() & ~15, originZ = pos.getZ() & ~15;
                renderer.tesselate(world, pos, layer -> captures.computeIfAbsent(layer,
                    ignored -> new Capture(originX, originY, originZ)), state, fluid);
                captures.forEach((layer, capture) -> {
                    capture.finish();
                    if (!capture.vertices.isEmpty()) result.add(new Layer(ChunkSectionLayerHelper.getMovingBlockRenderType(layer), capture.vertices));
                });
            }
        } finally { world.clearMask(); }
        return new PonderFluidRenderState(transform, result);
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose(); pose.mulPose(transform);
        try {
          for (var layer : layers) collector.submitCustomGeometry(pose, layer.renderType, (matrix, output) -> {
            for (var vertex : layer.vertices) {
                output.addVertex(matrix, vertex.x, vertex.y, vertex.z).setColor(vertex.color)
                    .setUv(vertex.u, vertex.v).setOverlay(vertex.overlay).setLight(vertex.light)
                    .setNormal(matrix, vertex.nx, vertex.ny, vertex.nz);
                if (vertex.hasUv3) output.setUv3(vertex.uv3u, vertex.uv3v);
                if (vertex.hasLineWidth) output.setLineWidth(vertex.lineWidth);
            }
          });
        } finally { pose.popPose(); }
    }

    /** Captures the complete native VertexConsumer contract used by the actual fluid tessellator. */
    private static final class Capture implements VertexConsumer {
        private final float originX, originY, originZ;
        private final List<Vertex> vertices = new ArrayList<>();
        private float x, y, z, u, v, nx, ny, nz, uv3u, uv3v, lineWidth;
        private int color, overlay, light;
        private boolean present, hasUv3, hasLineWidth;
        Capture(float x, float y, float z) { originX = x; originY = y; originZ = z; }
        void finish() {
            if (!present) return;
            vertices.add(new Vertex(x, y, z, color, u, v, overlay, light, nx, ny, nz,
                hasUv3, uv3u, uv3v, hasLineWidth, lineWidth)); present = false;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            finish(); present = true;
            this.x = x + originX; this.y = y + originY; this.z = z + originZ;
            color = 0xFFFFFFFF; overlay = OverlayTexture.NO_OVERLAY; light = LightCoordsUtil.FULL_BRIGHT;
            u = v = nx = nz = uv3u = uv3v = 0; ny = 1; lineWidth = 1; hasUv3 = hasLineWidth = false;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255; return this; }
        @Override public VertexConsumer setColor(int argb) { color = argb; return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { overlay = u & 0xFFFF | v << 16; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { light = u & 0xFFFF | v << 16; return this; }
        @Override public VertexConsumer setUv3(float u, float v) { uv3u = u; uv3v = v; hasUv3 = true; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { nx = x; ny = y; nz = z; return this; }
        @Override public VertexConsumer setLineWidth(float value) { lineWidth = value; hasLineWidth = true; return this; }
    }
}
