package net.createmod.ponder.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Checks submitted pipe-fluid materials and pixels across playback, replay, reopening and reload. */
public final class PonderPipeFluidRenderingTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var failures = new ArrayList<String>();
        int streams = 0;
        boolean comfy = context.computeOnClient(client -> PonderConfig.client().comfyReading.get());
        try (var host = context.worldBuilder().create()) {
            openSecondSlide(context, false);
            for (int time : new int[]{200, 330, 410, 450, 470, 510, 550}) {
                context.waitFor(client -> client.gui.screen() instanceof PonderUI ui
                    && ui.getActiveScene().getCurrentTime() >= time, 1400);
                togglePause(context);
                streams += inspect(context, "playing-" + time, failures);
                if (time == 410) {
                    var ui = context.computeOnClient(client -> (PonderUI) client.gui.screen());
                    var before = context.computeOnClient(client -> client.getModelManager()
                        .getFluidStateModelSet().get(Fluids.WATER.defaultFluidState()));
                    var reload = context.computeOnClient(client -> client.reloadResourcePacks());
                    context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 1200);
                    reload.join();
                    context.runOnClient(client -> {
                        if (client.gui.screen() != ui || before == client.getModelManager()
                                .getFluidStateModelSet().get(Fluids.WATER.defaultFluidState()))
                            throw new AssertionError("Native reload must preserve the scene and replace its water model");
                    });
                    if (inspect(context, "after-resource-reload", failures) == 0)
                        throw new AssertionError("Paused water flow must survive resource reload");
                    context.runOnClient(client -> client.gui.setScreen(null));
                    context.waitTicks(2);
                    context.runOnClient(client -> client.gui.setScreen(ui));
                    if (inspect(context, "reopened-existing-screen", failures) == 0)
                        throw new AssertionError("Reopening the existing scene must retain visible water flow");
                }
                if (time != 550) togglePause(context);
            }
            context.runOnClient(client -> ((PonderUI) client.gui.screen()).seekToTime(200));
            if (inspect(context, "after-backward-replay", failures) == 0)
                throw new AssertionError("Backward replay must recreate visible water flow");
            context.runOnClient(client -> client.gui.setScreen(null));
            openSecondSlide(context, true);
            context.runOnClient(client -> ((PonderUI) client.gui.screen()).seekToTime(410));
            if (inspect(context, "freshly-reopened-scene", failures) == 0)
                throw new AssertionError("A freshly compiled scene must render water after the atlas reload");
            context.runOnClient(client -> client.gui.setScreen(null));
        } finally {
            PonderRegistryRenderAudit.clear();
            context.runOnClient(client -> PonderConfig.client().comfyReading.set(comfy));
        }
        if (streams == 0) throw new AssertionError("Actual playback must submit pipe-fluid geometry");
        if (!failures.isEmpty()) throw new AssertionError("Invalid pipe-fluid material: " + failures);
        System.out.println("PONDER_PIPE_FLUID_CACHE_PASS playbackStreams=" + streams);
    }

    private static void openSecondSlide(ClientGameTestContext context, boolean paused) {
        context.runOnClient(client -> {
            client.options.framerateLimit().set(60);
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            var ui = PonderUI.of(new ItemStack(AllBlocks.FLUID_PIPE));
            client.gui.setScreen(ui);
            ui.setFocused(null);
            ui.setComfyReadingEnabled(false);
            if (paused) ui.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
            ui.keyPressed(new KeyEvent(InputConstants.KEY_D, 0, 0));
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            if (!((PonderUI) client.gui.screen()).getActiveScene().getId().getPath().equals("fluid_pipe_interaction"))
                throw new AssertionError("Actual copper-pipe slide 2/3 required");
        });
    }

    private static void togglePause(ClientGameTestContext context) {
        context.runOnClient(client -> ((PonderUI) client.gui.screen())
            .keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0)));
    }

    private static int inspect(ClientGameTestContext context, String stage, List<String> failures) {
        var sample = context.computeOnClient(client ->
            PonderRegistryRenderAudit.begin(((PonderUI) client.gui.screen()).getActiveScene()));
        context.waitFor(client -> sample.completed() != null || sample.failure() != null, 200);
        var points = new ArrayList<Vec2>();
        int[] streams = {0};
        context.runOnClient(client -> {
            if (sample.failure() != null) throw new AssertionError("Native fluid submission failed", sample.failure());
            var frame = sample.completedState();
            var scene = ((PonderUI) client.gui.screen()).getActiveScene();
            for (var section : frame.frame().sections()) for (var owner : section.blockEntities()) {
                if (!(owner.state() instanceof TransparentStraightPipeRenderer.State state)
                        || !(scene.getWorld().getBlockEntity(owner.position()) instanceof StraightPipeBlockEntity pipe))
                    continue;
                var sprites = new ArrayList<TextureAtlasSprite>();
                for (var side : Direction.values()) {
                    var flow = pipe.transport().getFlow(side);
                    if (flow == null || flow.fluid.isEmpty()) continue;
                    var model = client.getModelManager().getFluidStateModelSet()
                        .get(flow.fluid.variant().getFluid().defaultFluidState());
                    sprites.add(model.stillMaterial().sprite());
                    sprites.add(model.flowingMaterial().sprite());
                }
                for (var sprite : sprites) if (sprite.contents().name().getPath().equals("missingno"))
                    failures.add(stage + " missing native fluid sprite at " + owner.position());
                for (var stream : state.streams) {
                    streams[0]++;
                    stream.mesh().forEach(quad -> {
                        if (sprites.stream().noneMatch(sprite -> inside(quad, sprite)))
                            failures.add(stage + " pipe=" + owner.position() + " UVs do not use its current fluid atlas");
                    });
                }
                if (!state.streams.isEmpty()) {
                    var point = scene.getTransform().sceneToScreen(Vec3.atCenterOf(owner.position()), 1);
                    float scale = client.getWindow().getGuiScale();
                    points.add(new Vec2(point.x * scale, point.y * scale));
                }
            }
        });
        var screenshot = context.takeScreenshot("ponder-pipe-fluid-" + stage);
        try {
            var pixels = ImageIO.read(screenshot.toFile());
            int pink = 0;
            for (var point : points) for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                int x = Math.clamp(Math.round(point.x) + dx, 0, pixels.getWidth() - 1);
                int y = Math.clamp(Math.round(point.y) + dy, 0, pixels.getHeight() - 1);
                int color = pixels.getRGB(x, y), red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                if (red > 24 && blue > 64 && green < Math.min(red, blue) * .35f) pink++;
            }
            if (pink > 2) failures.add(stage + " contains " + pink + " missing-texture pixels inside pipe flow");
        } catch (IOException failure) { throw new AssertionError("Cannot inspect native fluid screenshot", failure); }
        PonderRegistryRenderAudit.clear();
        System.out.println("PONDER_PIPE_FLUID_FRAME " + stage + " streams=" + streams[0]);
        return streams[0];
    }

    private static boolean inside(net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView quad, TextureAtlasSprite sprite) {
        for (int vertex = 0; vertex < 4; vertex++)
            if (!Float.isFinite(quad.u(vertex)) || !Float.isFinite(quad.v(vertex))
                    || quad.u(vertex) < sprite.getU0() - 1e-6f || quad.u(vertex) > sprite.getU1() + 1e-6f
                    || quad.v(vertex) < sprite.getV0() - 1e-6f || quad.v(vertex) > sprite.getV1() + 1e-6f)
                return false;
        return true;
    }
}
