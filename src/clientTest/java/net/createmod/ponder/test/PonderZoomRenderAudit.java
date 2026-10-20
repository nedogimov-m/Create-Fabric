package net.createmod.ponder.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.IdentityHashMap;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderGuiTargets;
import net.createmod.ponder.foundation.ui.PonderNavigationRenderer;
import net.createmod.ponder.mixin.client.PonderNativePipTargetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;

/** Test-only observations of actual installed extraction/native GUI draw/native blit/main draw. */
public final class PonderZoomRenderAudit {
    private static final class Sample {
        final PonderNavigationRenderer.State state;
        final boolean forward;
        final float transitionValue;
        GpuTextureView colorView, depthView;
        GuiRenderer nestedRenderer;
        int nativeDrawRanges, realTextStates, realItemStates, realPipStates;
        boolean nativeCallbackReturned, actualBlitAdded, nativeBlitReturned, mainGuiDrawReturned;
        Sample(PonderNavigationRenderer.State state, boolean forward, float transitionValue) {
            this.state = state; this.forward = forward; this.transitionValue = transitionValue;
        }
    }
    private static final IdentityHashMap<PonderNavigationRenderer.State, Sample> SAMPLES = new IdentityHashMap<>();
    private static final ThreadLocal<Sample> CAPTURE = new ThreadLocal<>(), BLIT = new ThreadLocal<>();
    private static Screen expectedPrevious;
    private static boolean expectedForward;
    private static int expectedX, expectedY;
    private static int previousPhases;
    public static synchronized void resetObservations() {
        require(CAPTURE.get() == null && BLIT.get() == null, "Cannot reset an active actual native callback");
        SAMPLES.clear(); expectedPrevious = null; previousPhases = 0;
    }
    public static synchronized void expect(Screen previous, boolean forward, int x, int y) {
        require(CAPTURE.get() == null && BLIT.get() == null, "Cannot steer an active real native callback");
        expectedPrevious = previous; expectedForward = forward; expectedX = x; expectedY = y; previousPhases++;
    }
    public static synchronized void extracted(Screen previous, float partial, PonderNavigationRenderer.State state) {
        if (previous != expectedPrevious) return; // a prior transition can finish before the next physical input is delivered
        var screen = (AbstractPonderScreen) Minecraft.getInstance().gui.screen();
        float value = screen.transition.getValue(partial);
        require((screen.transition.getChaseTarget() > 0) == expectedForward, "Real navigation transition direction mismatch");
        float scale = 1 + .2f * value;
        int x = expectedForward ? expectedX : screen.width / 2, y = expectedForward ? expectedY : screen.height / 2;
        var expectedPose = new Matrix3x2f().translate(x, y).scale(scale, scale).translate(-x, -y);
        require(new Matrix3x2f(state.pose()).equals(expectedPose, 1e-4f), "Actual source previous-screen depth-point zoom pose mismatch");
        require(Math.abs(state.alpha() - (1 - Math.abs(value))) < 1e-5f, "Actual source previous-screen alpha mismatch");
        var sample = new Sample(state, expectedForward, value);
        state.content().forEachText(ignored -> sample.realTextStates++);
        state.content().forEachItem(ignored -> sample.realItemStates++);
        state.content().forEachPictureInPicture(ignored -> sample.realPipStates++);
        require(sample.realTextStates > 0 && sample.realItemStates + sample.realPipStates > 0,
            "Actual previous source UI must extract genuine native text/item/scene state");
        SAMPLES.put(state, sample);
    }
    public static synchronized void begin(PonderNavigationRenderer owner, PonderNavigationRenderer.State state) {
        var sample = SAMPLES.get(state); if (sample == null) return;
        require(CAPTURE.get() == null, "Actual whole-screen capture cannot recurse");
        var target = (PonderNativePipTargetAccessor) (Object) owner;
        sample.colorView = target.ponder$colorView(); sample.depthView = target.ponder$depthView();
        require(sample.colorView != Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(),
            "Native PIP must own a separate GUI output image");
        CAPTURE.set(sample);
    }
    public static synchronized void guiDraw(GuiRenderer renderer, RenderTarget target, int first, int last) {
        var sample = CAPTURE.get();
        if (sample != null) {
            require(PonderGuiTargets.targetFor(renderer, null) == target, "Native executeDrawRange must receive the exact scoped renderer target");
            require(target.getColorTextureView() == sample.colorView && target.getDepthTextureView() == sample.depthView,
                "Actual native GUI render pass target must borrow exactly its native PIP owner's color/depth views");
            require(target != Minecraft.getInstance().gameRenderer.mainRenderTarget() && first < last,
                "Actual previous UI draw must render genuine draw ranges into its own image");
            sample.nestedRenderer = renderer; sample.nativeDrawRanges++;
        } else if (target == Minecraft.getInstance().gameRenderer.mainRenderTarget() && first < last) {
            require(PonderGuiTargets.targetFor(renderer, target) == target, "Ordinary native main GUI target must remain unchanged");
            for (var observed : SAMPLES.values()) if (observed.actualBlitAdded && observed.nativeBlitReturned) observed.mainGuiDrawReturned = true;
        }
    }
    public static synchronized void end(PonderNavigationRenderer.State state) {
        var sample = SAMPLES.get(state); if (sample == null) return;
        require(CAPTURE.get() == sample && sample.nativeDrawRanges > 0, "Actual native previous-screen GUI draw is required");
        require(PonderGuiTargets.targetFor(sample.nestedRenderer, null) == null, "Exact renderer output scope must be removed after native callback");
        sample.nativeCallbackReturned = true; CAPTURE.remove();
    }
    public static synchronized void beginBlit(PonderNavigationRenderer.State state) {
        var sample = SAMPLES.get(state); if (sample != null) BLIT.set(sample);
    }
    public static synchronized void actualBlit(BlitRenderState state) {
        var sample = BLIT.get(); if (sample == null) return;
        int channel = Math.clamp((int) (255 * sample.state.alpha()), 0, 255);
        int tint = channel << 24 | channel << 16 | channel << 8 | channel;
        require(state.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
            && state.textureSetup().texure0() == sample.colorView && state.color() == tint,
            "Actual added native GUI blit must use its real PIP image and correct premultiplied source alpha");
        require(new Matrix3x2f(state.pose()).equals(sample.state.pose(), 1e-5f), "Native added blit lost actual source zoom pose");
        sample.actualBlitAdded = true;
    }
    public static synchronized void endBlit(PonderNavigationRenderer.State state) {
        var sample = SAMPLES.get(state); if (sample == null) return;
        require(BLIT.get() == sample && sample.actualBlitAdded, "Actual native GuiRenderState must receive the real blit");
        sample.nativeBlitReturned = true; BLIT.remove();
    }
    public static synchronized long complete(boolean forward) {
        return SAMPLES.values().stream().filter(sample -> sample.forward == forward && sample.state.alpha() > 0 && sample.state.alpha() < 1
            && sample.nativeCallbackReturned && sample.actualBlitAdded && sample.nativeBlitReturned && sample.mainGuiDrawReturned).count();
    }
    public static synchronized JsonObject report() {
        var report = new JsonObject(); report.addProperty("testRequestedNavigationPhases", previousPhases);
        report.addProperty("nativeForwardFrames", complete(true)); report.addProperty("nativeBackwardFrames", complete(false));
        var frames = new JsonArray();
        for (var sample : SAMPLES.values()) {
            var frame = new JsonObject(); frame.addProperty("direction", sample.forward ? "FORWARD" : "BACKWARD");
            frame.addProperty("transitionValue", sample.transitionValue); frame.addProperty("sourceAlpha", sample.state.alpha());
            frame.addProperty("realTextStates", sample.realTextStates); frame.addProperty("realItemStates", sample.realItemStates);
            frame.addProperty("realNativePipStates", sample.realPipStates); frame.addProperty("nativeNestedGuiDrawRanges", sample.nativeDrawRanges);
            frame.addProperty("nativeCallbackReturned", sample.nativeCallbackReturned); frame.addProperty("actualBlitAdded", sample.actualBlitAdded);
            frame.addProperty("nativeBlitReturned", sample.nativeBlitReturned); frame.addProperty("nativeMainGuiDrawReturned", sample.mainGuiDrawReturned);
            frame.addProperty("actualColorViewIdentity", sample.colorView == null ? null : Integer.toHexString(System.identityHashCode(sample.colorView)));
            frame.addProperty("actualDepthViewIdentity", sample.depthView == null ? null : Integer.toHexString(System.identityHashCode(sample.depthView)));
            frames.add(frame);
        }
        report.add("observedFrames", frames); return report;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private PonderZoomRenderAudit() {}
}
