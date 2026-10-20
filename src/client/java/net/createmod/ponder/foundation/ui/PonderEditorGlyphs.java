// Ponder1.0.82 editor-axis source transforms, MIT; native26.3 captured glyph submission.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;

final class PonderEditorGlyphs {
    record Glyph(Matrix4f transform, FormattedCharSequence text, int color) {
        Glyph { transform = new Matrix4f(transform); }
        @Override public Matrix4f transform() { return new Matrix4f(transform); }
        void submit(PoseStack pose, SubmitNodeCollector collector) {
            pose.pushPose(); pose.mulPose(transform);
            collector.submitText(pose, 0, 0, text, false, Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
            pose.popPose();
        }
    }
    static List<Glyph> extract(PonderScene scene) {
        int xSpan = scene.getBounds().getXSpan(), zSpan = scene.getBounds().getZSpan();
        var result = new ArrayList<Glyph>(); var pose = new PoseStack();
        pose.scale(-1, -1, 1); pose.scale(1 / 16f, 1 / 16f, 1 / 16f); pose.translate(1, -8, -1 / 64f);
        pose.pushPose(); pose.translate(4, -3, 0); pose.translate(0, 0, -2 / 1024f);
        for (int x = 0; x <= xSpan; x++) {
            pose.translate(-16, 0, 0); add(result, pose, x == xSpan ? "x" : "" + x, 0, 0, 0xFFFFFFFF);
        }
        pose.popPose(); pose.pushPose();
        pose.scale(-1, 1, 1); pose.translate(0, -3, -4); pose.rotateDegrees(Axis.YP, -90); pose.translate(-8, -2, 2 / 64f);
        for (int z = 0; z <= zSpan; z++) {
            pose.translate(16, 0, 0); add(result, pose, z == zSpan ? "z" : "" + z, 0, 0, 0xFFFFFFFF);
        }
        pose.popPose(); pose.pushPose(); pose.translate(xSpan * -8, 0, zSpan * 8); pose.rotateDegrees(Axis.YP, -90);
        // Exact Iterate.horizontalDirections source order.
        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            String direction = Direction.from2DDataValue(directionIndex).name().substring(0, 1);
            pose.rotateDegrees(Axis.YP, 90); pose.pushPose(); pose.translate(0, 0, zSpan * 16); pose.rotateDegrees(Axis.XP, -90);
            add(result, pose, direction, 0, 0, 0x66FFFFFF);
            add(result, pose, "|", 2, 10, 0x44FFFFFF); add(result, pose, ".", 2, 14, 0x22FFFFFF);
            pose.popPose();
        }
        pose.popPose(); return List.copyOf(result);
    }
    private static void add(List<Glyph> result, PoseStack pose, String text, int x, int y, int color) {
        pose.pushPose(); pose.translate(x, y, 0);
        result.add(new Glyph(pose.last().pose(), Component.literal(text).getVisualOrderText(), color)); pose.popPose();
    }
    private PonderEditorGlyphs() {}
}
