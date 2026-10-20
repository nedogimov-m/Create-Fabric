// Catnip1.0.82 streak/chevron geometry, MIT; native26.3 detached GUI extraction.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

final class PonderUIRenderHelper {
    record Vertex(float x, float y, int color) {}
    private record Geometry(List<Vertex> vertices, Matrix3x2fc pose, ScreenRectangle bounds)
            implements GuiElementRenderState {
        Geometry { vertices = List.copyOf(vertices); pose = new Matrix3x2f(pose); }
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public void buildVertices(VertexConsumer consumer) {
            for (var vertex : vertices) consumer.addVertexWith2DPose(pose, vertex.x(), vertex.y()).setColor(vertex.color());
        }
    }
    static int alpha(int color, float multiplier) {
        return (Math.clamp((int) ((color >>> 24) * multiplier), 0, 255) << 24) | color & 0xFFFFFF;
    }
    static int mix(int first, int second, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8)
            result |= (int) (((first >>> shift) & 255) * (1 - amount) + ((second >>> shift) & 255) * amount) << shift;
        return result;
    }
    /** Source BoxElement's five background and four border quads, including its two-pixel inset. */
    static void box(GuiGraphicsExtractor gui, int x, int y, int width, int height, int top, int bottom) {
        int f = 2, background = 0xFF000000;
        gui.fill(x - f - 1, y - f - 2, x + f + 1 + width, y - f - 1, background);
        gui.fill(x - f - 2, y - f - 1, x - f - 1, y + f + 1 + height, background);
        gui.fill(x - f - 1, y + f + 1 + height, x + f + 1 + width, y + f + 2 + height, background);
        gui.fill(x + f + 1 + width, y - f - 1, x + f + 2 + width, y + f + 1 + height, background);
        gui.fill(x - f - 1, y - f - 1, x + f + 1 + width, y + f + 1 + height, background);
        gui.fill(x - f - 1, y - f - 1, x + f + 1 + width, y - f, top);
        gui.fillGradient(x - f - 1, y - f, x - f, y + f + height, top, bottom);
        gui.fill(x - f - 1, y + f + height, x + f + 1 + width, y + f + 1 + height, bottom);
        gui.fillGradient(x + f + width, y - f, x + f + 1 + width, y + f + height, top, bottom);
    }
    /** Original speech box placement, border palette and divot sprites; native GUI owns rendering. */
    static void speechBox(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                          boolean highlighted, Pointing pointing, boolean returnWithLocalTransform) {
        if (!returnWithLocalTransform) gui.pose().pushMatrix();
        try {
            int boxX = x, boxY = y, divotX = x, divotY = y, rotation, tipColor;
            int top = highlighted ? 0xF0C0C0FF : 0x40FFEEDD;
            int bottom = highlighted ? 0xA0C0C0FF : 0x20FFEEDD;
            switch (pointing) {
                case DOWN -> { rotation = 0; boxX -= width / 2; boxY -= height + 10; divotX -= 4; divotY -= 9; tipColor = bottom; }
                case LEFT -> { rotation = 90; boxX += 10; boxY -= height / 2; divotX += 1; divotY -= 4; tipColor = mix(top, bottom, .5f); }
                case RIGHT -> { rotation = 270; boxX -= width + 10; boxY -= height / 2; divotX -= 9; divotY -= 4; tipColor = mix(top, bottom, .5f); }
                case UP -> { rotation = 180; boxX -= width / 2; boxY += 10; divotX -= 4; divotY += 1; tipColor = top; }
                default -> throw new AssertionError(pointing);
            }
            box(gui, boxX, boxY, width, height, top, bottom);
            gui.pose().pushMatrix();
            try {
                gui.pose().translate(divotX + 4, divotY + 4);
                gui.pose().rotate((float) Math.toRadians(rotation));
                gui.pose().translate(-4, -4);
                PonderGuiTextures.SPEECH_TOOLTIP_BACKGROUND.render(gui, 0, 0);
                PonderGuiTextures.SPEECH_TOOLTIP_COLOR.render(gui, 0, 0, new Color(tipColor, true));
            } finally { gui.pose().popMatrix(); }
            if (returnWithLocalTransform) gui.pose().translate(boxX, boxY);
        } finally { if (!returnWithLocalTransform) gui.pose().popMatrix(); }
    }
    /** Exact source three-stage streak; native GUI pose owns rotation and clipping. */
    static void streak(GuiGraphicsExtractor gui, float angle, int x, int y, int breadth, int length) {
        if (PonderNavigationRenderer.isRenderingPrevious()) return;
        gui.pose().pushMatrix(); gui.pose().translate(x, y); gui.pose().rotate((float) Math.toRadians(angle - 90));
        int half = breadth / 2;
        gui.fillGradient(-half, 0, half, (int) (.5 * length), 0x9F101010, 0x7F101010);
        gui.fillGradient(-half, (int) (.5 * length), half, (int) (.75 * length), 0x7F101010, 0x0F101010);
        gui.fillGradient(-half, (int) (.75 * length), half, length, 0x0F101010, 0x00101010);
        gui.pose().popMatrix();
    }
    /** Source six triangles, emitted as degenerate native GUI quads with identical vertex colors. */
    static void breadcrumb(GuiGraphicsExtractor gui, int x, int y, int width, int height, int indent, int first, int last) {
        float x0 = x - indent, x1 = x, x2 = x - indent + width, x3 = x + width;
        float y0 = y, y1 = y + height / 2f, y2 = y + height;
        float denominator = Math.abs(width) + 2f * Math.abs(indent);
        if (denominator == 0) return;
        int c1 = first, c2 = mix(first, last, Math.abs(indent) / denominator);
        int c3 = mix(first, last, (Math.abs(indent) + Math.abs(width)) / denominator), c4 = last;
        var vertices = new ArrayList<Vertex>();
        triangle(vertices, new Vertex(x0, y1, c1), new Vertex(x1, y0, c2), new Vertex(x1, y1, c2));
        triangle(vertices, new Vertex(x0, y1, c1), new Vertex(x1, y1, c2), new Vertex(x1, y2, c2));
        triangle(vertices, new Vertex(x1, y2, c2), new Vertex(x1, y0, c2), new Vertex(x2, y0, c3));
        triangle(vertices, new Vertex(x1, y2, c2), new Vertex(x2, y0, c3), new Vertex(x2, y2, c3));
        triangle(vertices, new Vertex(x2, y1, c3), new Vertex(x2, y0, c3), new Vertex(x3, y0, c4));
        triangle(vertices, new Vertex(x2, y2, c3), new Vertex(x2, y1, c3), new Vertex(x3, y2, c4));
        float min = Math.min(Math.min(x0, x1), Math.min(x2, x3));
        float max = Math.max(Math.max(x0, x1), Math.max(x2, x3));
        var pose = new Matrix3x2f(gui.pose());
        var bounds = new ScreenRectangle((int) Math.floor(min), y, (int) Math.ceil(max - min), height).transformMaxBounds(pose);
        ((GuiGraphicsExtractorAccessor) gui).create$getRenderState().addGuiElement(new Geometry(vertices, pose, bounds));
    }
    private static void triangle(List<Vertex> target, Vertex a, Vertex b, Vertex c) {
        target.add(a); target.add(b); target.add(c); target.add(c);
    }
    private PonderUIRenderHelper() {}
}
