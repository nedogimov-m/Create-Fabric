// Ponder1.0.82 scene renderer, MIT; native26.3 PictureInPicture/RenderPearl adaptation.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.outliner.Outline;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.render.PonderBreakingRenderState;
import net.createmod.ponder.foundation.render.PonderFluidRenderState;
import net.createmod.ponder.foundation.render.PonderSceneFrame;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Captures actual local scene state. Native PIP owns textures, projection, render pass and disposal. */
public final class PonderViewport {
    public record Item(Vec3 position, float scale, ItemStackRenderState state) {}
    public record Platform(int size, int x, int z, boolean hidden, float finishingFlash) {}
    public record State(PonderSceneFrame frame, Matrix4fc worldTransform, CameraRenderState camera,
                        List<Item> items, List<PonderBreakingRenderState> breaking, List<PonderFluidRenderState> fluids,
                        Platform platform, List<PonderEditorGlyphs.Glyph> editorGlyphs, int viewportWidth, int viewportHeight,
                        int x0, int y0, int x1, int y1, float scale, Matrix3x2fc pose,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State {
            worldTransform = new Matrix4f(worldTransform); pose = new Matrix3x2f(pose);
            items = List.copyOf(items); breaking = List.copyOf(breaking); fluids = List.copyOf(fluids); editorGlyphs = List.copyOf(editorGlyphs);
        }
    }
    /** A second native PIP owner is required during source slide transitions: one renderer's
     * mutable target texture cannot back two deferred GUI blits in the same native GUI frame. */
    public record TransitionState(State content) implements PictureInPictureRenderState {
        @Override public int x0() { return content.x0(); }
        @Override public int y0() { return content.y0(); }
        @Override public int x1() { return content.x1(); }
        @Override public int y1() { return content.y1(); }
        @Override public float scale() { return content.scale(); }
        @Override public Matrix3x2fc pose() { return content.pose(); }
        @Override public ScreenRectangle scissorArea() { return content.scissorArea(); }
        @Override public ScreenRectangle bounds() { return content.bounds(); }
    }

    private static final class SceneCamera extends Camera {
        void configure(float pitch, float yaw) { setPosition(Vec3.ZERO); setRotation(yaw, pitch); }
    }

    public static State extract(GuiGraphicsExtractor gui, PonderScene scene, int x, int y,
                                int width, int height, double slide, float partial, float finishingFlash) {
        var transform = scene.getTransform();
        transform.updateScreenParams(width, height, slide);
        transform.updateSceneRVE(partial);
        var worldPose = transform.apply(new PoseStack(), partial);
        var worldMatrix = new Matrix4f(worldPose.last().pose());
        var camera = new SceneCamera();
        // Exact original PonderScene particle billboard orientation; camera-relative origin is ZERO.
        camera.configure(-transform.getXRotation(partial), transform.getYRotation(partial) + 180);
        // Match native PIP's pixel projection, including native negative Z and GUI scale.
        float guiScale = clientGuiScale();
        // Projection.getMatrix swaps configured far/near for native reverse depth.
        var projection = new Matrix4f().setOrtho(0, width, height, 0, 1000 / guiScale, -1000 / guiScale,
            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        var viewMatrix = new Matrix4f().scaling(1, 1, -1).mul(worldMatrix);
        var frustum = new Frustum(viewMatrix, projection);
        frustum.prepare(0, 0, 0);
        var cameraState = new CameraRenderState();
        cameraState.pos = Vec3.ZERO;
        cameraState.blockPos = BlockPos.ZERO;
        cameraState.orientation.set(camera.rotation());
        cameraState.xRot = camera.xRot(); cameraState.yRot = camera.yRot();
        cameraState.initialized = true;
        cameraState.cullFrustum = frustum;
        cameraState.projectionMatrix.set(projection);
        cameraState.viewRotationMatrix.set(viewMatrix);
        var frame = PonderSceneFrame.extract(scene, partial, camera, frustum);
        var fluids = new ArrayList<PonderFluidRenderState>();
        var breaking = new ArrayList<PonderBreakingRenderState>();
        scene.forEachVisible(WorldSectionElementImpl.class, section -> {
            fluids.add(section.extractFluids(scene.getWorld(), partial));
            breaking.add(section.extractBreaking(scene.getWorld(), partial));
        });
        var items = new ArrayList<Item>();
        var client = Minecraft.getInstance();
        for (var outline : frame.outlines()) for (var item : outline.items()) {
            var model = new ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(model, item.stack(), ItemDisplayContext.FIXED,
                scene.getWorld(), null, 0);
            items.add(new Item(item.position(), item.scale(), model));
        }
        var pose = new Matrix3x2f(gui.pose());
        var scissor = new ScreenRectangle(x, y, width, height);
        var platform = new Platform(scene.getBasePlateSize(), scene.getBasePlateOffsetX(), scene.getBasePlateOffsetZ(),
            scene.shouldHidePlatformShadow(), finishingFlash);
        var editorGlyphs = PonderIndex.editingModeActive() && PonderNavigationRenderer.extractingScreen(client.gui.screen()) instanceof PonderUI ui && !ui.isUserViewMode()
            ? PonderEditorGlyphs.extract(scene) : List.<PonderEditorGlyphs.Glyph>of();
        return new State(frame, worldMatrix, cameraState, items, breaking, fluids, platform, editorGlyphs, width, height,
            x, y, x + width, y + height, 1, pose, scissor,
            PictureInPictureRenderState.getBounds(x, y, x + width, y + height, pose, scissor));
    }

    private static float clientGuiScale() { return Minecraft.getInstance().getWindow().getGuiScale(); }

    public static void enqueue(GuiGraphicsExtractor gui, State state) {
        ((GuiGraphicsExtractorAccessor) gui).create$getRenderState().addPicturesInPictureState(state);
    }
    public static void enqueueTransition(GuiGraphicsExtractor gui, State state) {
        ((GuiGraphicsExtractorAccessor) gui).create$getRenderState().addPicturesInPictureState(new TransitionState(state));
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {
        @Override public Class<State> getRenderStateClass() { return State.class; }
        @Override protected String getTextureLabel() { return "Ponder actual local scene"; }

        @Override protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
            submitCaptured(state, pose, collector);
        }
    }
    public static final class TransitionRenderer extends PictureInPictureRenderer<TransitionState> {
        @Override public Class<TransitionState> getRenderStateClass() { return TransitionState.class; }
        @Override protected String getTextureLabel() { return "Ponder previous local scene"; }
        @Override protected void renderToTexture(TransitionState state, PoseStack pose, SubmitNodeCollector collector) {
            submitCaptured(state.content(), pose, collector);
        }
    }

    private static void submitCaptured(State state, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            // Native prepare owns an enclosing model-view push/pop across this callback AND feature
            // preparation/execution. Install the captured scene transform there so native particle
            // groups and model features use the same PIP space. No projection/target/flush is bypassed.
            // Native PIP already uses Y-down ortho. Undo its bottom-center origin;
            // SceneTransform already maps the scene into GUI pixels and flips source world Y.
            pose.translate(-state.viewportWidth / 2f, -state.viewportHeight, 0);
            pose.mulPose(state.worldTransform);
            RenderSystem.getModelViewStack().mul(pose.last().pose());
            var local = new PoseStack();
            submitPlatform(local, collector, state.platform);
            state.frame.submitWorld(local, collector, state.camera);
            for (var fluid : state.fluids) fluid.submit(local, collector);
            state.frame.particles().submit(collector, state.camera);
            for (var outline : state.frame.outlines()) net.createmod.catnip.outliner.OutlineRenderer.submit(local, collector, outline);
            for (var item : state.items) {
                local.pushPose();
                local.translate(item.position); local.scale(item.scale, item.scale, item.scale);
                item.state.submit(local, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                local.popPose();
            }
            for (var section : state.frame.sections()) if (section.selectedShape() != null) {
                local.pushPose(); local.mulPose(section.transform());
                collector.submitShapeOutline(local, section.selectedShape(), RenderTypes.lines(), 0xFFFFFFFF, 1.5f, false);
                local.popPose();
            }
            for (var breaking : state.breaking) breaking.submit(local, collector);
            for (var glyph : state.editorGlyphs) glyph.submit(local, collector);
    }

    private static void submitPlatform(PoseStack pose, SubmitNodeCollector collector, Platform platform) {
        if (platform.hidden) return;
        pose.pushPose(); pose.translate(platform.x, 0, platform.z);
        float alpha = platform.finishingFlash * .9f;
        float flash = 1 - (alpha * alpha * 2 - 1) * (alpha * alpha * 2 - 1);
        for (int side = 0; side < 4; side++) {
            pose.translate(platform.size, 0, 0);
            pose.pushPose(); pose.translate(0, 0, -1 / 1024f);
            if (flash > 0) {
                pose.pushPose(); pose.scale(1, .5f + flash * .75f, 1);
                gradient(pose, collector, 0, 1, -platform.size, 0, 0x00C6FFC9,
                    Math.clamp((int) (0xAA * alpha), 0, 255) << 24 | 0xC6FFC9);
                pose.popPose();
            }
            pose.translate(0, 0, 2 / 1024f);
            gradient(pose, collector, 0, 0, -platform.size, -4, 0x66000000, 0x00000000);
            pose.popPose();
            pose.rotateDegrees(com.mojang.math.Axis.YP, -90);
        }
        pose.popPose();
    }
    private static void gradient(PoseStack pose, SubmitNodeCollector collector, float x0, float y0, float x1, float y1, int first, int last) {
        collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (matrix, vertices) -> {
            vertices.addVertex(matrix, x0, y0, 0).setColor(first);
            vertices.addVertex(matrix, x0, y1, 0).setColor(last);
            vertices.addVertex(matrix, x1, y1, 0).setColor(last);
            vertices.addVertex(matrix, x1, y0, 0).setColor(first);
        });
    }

    private static boolean registered;
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PictureInPictureRendererRegistry.register(context -> new Renderer());
        PictureInPictureRendererRegistry.register(context -> new TransitionRenderer());
        PonderTitleRenderer.register();
        PonderNavigationRenderer.register();
    }
    private PonderViewport() {}
}
