// Catnip1.0.82 previous-screen rerender/zoom, MIT; native26.3 GUI/PIP extraction and output scope.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.List;
import net.createmod.ponder.mixin.client.PonderNativePipTargetAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Actual previous screen extracts anew each frame without ticking, exactly as source navigation does. */
public final class PonderNavigationRenderer extends PictureInPictureRenderer<PonderNavigationRenderer.State> {
    private static final GuiRenderState PREVIOUS_GUI = new GuiRenderState();
    private static final ThreadLocal<Screen> EXTRACTING_PREVIOUS = new ThreadLocal<>();
    private GuiRenderer guiRenderer;
    public record State(GuiRenderState content, float alpha, int x0, int y0, int x1, int y1,
                        float scale, Matrix3x2fc pose, ScreenRectangle scissorArea,
                        ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State { pose = new Matrix3x2f(pose); }
    }
    public static boolean isRenderingPrevious() { return EXTRACTING_PREVIOUS.get() != null; }
    public static Screen extractingScreen(Screen ordinary) { return EXTRACTING_PREVIOUS.get() == null ? ordinary : EXTRACTING_PREVIOUS.get(); }
    public static void clearCapture() { PREVIOUS_GUI.reset(); }
    public static State extract(GuiGraphicsExtractor outer, Screen previous, int width, int height, float partial, float alpha) {
        if (EXTRACTING_PREVIOUS.get() != null) throw new IllegalStateException("Ponder previous-screen extraction cannot recurse");
        PREVIOUS_GUI.reset();
        var gui = new GuiGraphicsExtractor(Minecraft.getInstance(), PREVIOUS_GUI, 0, 0);
        EXTRACTING_PREVIOUS.set(previous);
        try { previous.extractRenderState(gui, 0, 0, partial); }
        finally { EXTRACTING_PREVIOUS.remove(); }
        var pose = new Matrix3x2f(outer.pose()); var scissor = new ScreenRectangle(0, 0, width, height);
        var state = new State(PREVIOUS_GUI, alpha, 0, 0, width, height, 1, pose, scissor,
            PictureInPictureRenderState.getBounds(0, 0, width, height, pose, scissor));
        ((GuiGraphicsExtractorAccessor) outer).create$getRenderState().addPicturesInPictureState(state);
        return state;
    }
    @Override public Class<State> getRenderStateClass() { return State.class; }
    @Override protected String getTextureLabel() { return "Ponder previous native screen"; }

    /** Metadata view only; all four actual resources remain owned by native PictureInPictureRenderer. */
    private static final class BorrowedTarget extends RenderTarget {
        BorrowedTarget(PonderNativePipTargetAccessor owner) {
            super("Ponder previous native GUI output", owner.ponder$colorTexture().getFormat(), owner.ponder$depthTexture().getFormat());
            colorTexture = owner.ponder$colorTexture(); colorTextureView = owner.ponder$colorView();
            depthTexture = owner.ponder$depthTexture(); depthTextureView = owner.ponder$depthView();
            width = colorTexture.getWidth(0); height = colorTexture.getHeight(0);
        }
    }
    @Override protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
        if (state.content() != PREVIOUS_GUI) throw new IllegalStateException("Ponder previous GUI capture ownership changed");
        var client = Minecraft.getInstance();
        if (guiRenderer == null) guiRenderer = new GuiRenderer(PREVIOUS_GUI, client.gameRenderer.featureRenderDispatcher(),
            List.of(new GuiEntityRenderer(client.getEntityRenderDispatcher()), new GuiSkinRenderer(), new GuiBookModelRenderer(),
                new GuiBannerResultRenderer(client.getAtlasManager()), new GuiProfilerChartRenderer(),
                new PonderViewport.Renderer(), new PonderViewport.TransitionRenderer(), new PonderTitleRenderer()));
        var target = new BorrowedTarget((PonderNativePipTargetAccessor) (Object) this);
        var originalProjection = RenderSystem.getProjectionMatrixBuffer();
        var originalProjectionType = RenderSystem.getProjectionType();
        try (var output = PonderGuiTargets.enter(guiRenderer, target)) {
            // Native PIP already allocated and cleared this exact target before its callback.
            // Native GUI owns prepare/items/text/PIP/geometry/upload/draw/reset in the usual order.
            guiRenderer.render();
        } finally {
            try { guiRenderer.endFrame(); }
            finally { PREVIOUS_GUI.reset(); RenderSystem.setProjectionMatrix(originalProjection, originalProjectionType); }
        }
        // No custom feature submits: native PIP's following empty feature pass does not clear the GUI output.
    }
    @Override protected void blitTexture(State state, GuiRenderState output) {
        int channel = Math.clamp((int) (255 * state.alpha()), 0, 255);
        int premultipliedTint = channel << 24 | channel << 16 | channel << 8 | channel;
        var view = ((PonderNativePipTargetAccessor) (Object) this).ponder$colorView();
        output.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)), state.pose(),
            state.x0(), state.y0(), state.x1(), state.y1(), 0, 1, 1, 0, premultipliedTint, state.scissorArea(), state.bounds()));
    }
    @Override public void close() {
        try { if (guiRenderer != null) { guiRenderer.close(); guiRenderer = null; } }
        finally { super.close(); }
    }
    static void register() { PictureInPictureRendererRegistry.register(context -> new PonderNavigationRenderer()); }
}
