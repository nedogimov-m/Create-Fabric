// Ponder1.0.82 source title flip, MIT; native26.3 captured text/PictureInPicture submission.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Captures only immutable wrapped text and source angles, never a live scene owner. */
final class PonderTitleRenderer extends PictureInPictureRenderer<PonderTitleRenderer.State> {
    record State(List<FormattedCharSequence> current, List<FormattedCharSequence> previous,
                 float indexDifference, float fade, int contentWidth, int contentHeight,
                 int x0, int y0, int x1, int y1, float scale, Matrix3x2fc pose,
                 ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        State { current = List.copyOf(current); previous = List.copyOf(previous); pose = new Matrix3x2f(pose); }
    }
    static void extract(GuiGraphicsExtractor gui, Font font, String title, String previous, float difference, float fade) {
        var currentLines = font.split(Component.literal(title), 180);
        var previousLines = font.split(Component.literal(previous), 180);
        int titleHeight = Math.max(currentLines.size(), previousLines.size()) * 9 + 24;
        int x = 59, y = 39, width = 188;
        var pose = new Matrix3x2f(gui.pose());
        var scissor = new ScreenRectangle(x, y, width, titleHeight);
        var state = new State(currentLines, previousLines, difference, fade, width, titleHeight,
            x, y, x + width, y + titleHeight, 1, pose, scissor,
            PictureInPictureRenderState.getBounds(x, y, x + width, y + titleHeight, pose, scissor));
        ((GuiGraphicsExtractorAccessor) gui).create$getRenderState().addPicturesInPictureState(state);
    }
    @Override public Class<State> getRenderStateClass() { return State.class; }
    @Override protected String getTextureLabel() { return "Ponder source title flip"; }
    @Override protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
        pose.translate(-state.contentWidth() / 2f, -state.contentHeight(), 0);
        float difference = state.indexDifference(), amount = Math.abs(difference);
        if (amount < .01f) { text(pose, collector, state.current(), state.fade()); return; }
        pose.translate(0, 6, 0);
        pose.pushPose();
        pose.rotateDegrees(Axis.XN, difference * -90 + Math.signum(difference) * 90);
        pose.translate(0, -6, 5); text(pose, collector, state.previous(), amount);
        pose.popPose();
        pose.rotateDegrees(Axis.XN, difference * -90);
        pose.translate(0, -6, 5); text(pose, collector, state.current(), 1 - amount);
    }
    private static void text(PoseStack pose, SubmitNodeCollector collector, List<FormattedCharSequence> lines, float fade) {
        if (fade <= 0) return;
        int color = Math.max(4, Math.clamp((int) (255 * fade), 0, 255)) << 24 | 0xEEEEEE;
        for (int i = 0; i < lines.size(); i++) collector.submitText(pose, 0, i * 9, lines.get(i), false,
            Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
    }
    static void register() { PictureInPictureRendererRegistry.register(context -> new PonderTitleRenderer()); }
}
