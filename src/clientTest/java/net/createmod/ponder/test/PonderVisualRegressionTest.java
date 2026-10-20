package net.createmod.ponder.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.pulley.PulleyRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real screenshot occlusion and observed native screen playback, including tick boundaries. */
public final class PonderVisualRegressionTest implements FabricClientGameTest {
    private record Frame(long nanos, int tick, BlockPos pos, float partial, float angle, float speed) {}
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static volatile boolean recording, active;
    private static PonderViewport.State latest;

    public static void observe(PonderScene scene, PonderViewport.State state) {
        if (!active) return;
        latest = state;
        if (!recording || FRAMES.size() > 1800) return;
        var kinetic = state.frame().sections().stream().flatMap(section -> section.blockEntities().stream())
            .map(owner -> scene.getWorld().getBlockEntity(owner.position()))
            .filter(owner -> owner instanceof KineticBlockEntity)
            .map(owner -> (KineticBlockEntity) owner).findFirst().orElse(null);
        if (kinetic != null) FRAMES.add(new Frame(System.nanoTime(), scene.getCurrentTime(), kinetic.getBlockPos(),
            scene.getWorld().scenePartialTicks(), kinetic.getAngle(scene.getWorld().scenePartialTicks()), kinetic.getSpeed()));
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.framerateLimit().set(60);
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
            });
            active = true;
            verifyOpaqueTop(context);
            verifyDepthOcclusion(context);
            context.runOnClient(client -> {
                client.gui.setScreen(PonderUI.of(new ItemStack(AllBlocks.ROPE_PULLEY)));
                FRAMES.clear(); recording = true;
            });
            context.waitFor(client -> client.gui.screen() instanceof PonderUI ui && ui.getActiveScene().getCurrentTime() >= 45, 400);
            context.takeScreenshot("ponder-pulley-playing-45");
            context.waitFor(client -> client.gui.screen() instanceof PonderUI ui && ui.getActiveScene().getCurrentTime() >= 90, 400);
            context.runOnClient(client -> {
                var scene = ((PonderUI) client.gui.screen()).getActiveScene();
                require(scene.getWorld().getGameTime() == scene.getWorld().simulationTicks(), "Native renderers must read the scene clock while the host is paused");
                var pulleys = latest.frame().sections().stream().flatMap(section -> section.blockEntities().stream())
                    .map(owner -> owner.state()).filter(state -> state instanceof PulleyRenderer.State)
                    .map(state -> (PulleyRenderer.State) state).toList();
                require(!pulleys.isEmpty() && pulleys.stream().allMatch(state -> state.offset > 1.9f && state.running && state.sections.size() >= 2),
                    "Actual teaching scene must render the extended rope and magnet without a real assembled contraption");
            });
            context.takeScreenshot("ponder-pulley-playing-90");
            context.waitFor(client -> client.gui.screen() instanceof PonderUI ui && ui.getActiveScene().getCurrentTime() >= 180, 600);
            context.takeScreenshot("ponder-pulley-playing-180");
            context.runOnClient(client -> {
                recording = false;
                int boundaryChecks = 0, interpolated = 0;
                var times = new ArrayList<Long>();
                for (int i = 1; i < FRAMES.size(); i++) {
                    var a = FRAMES.get(i - 1); var b = FRAMES.get(i);
                    times.add(b.nanos - a.nanos);
                    if (!a.pos.equals(b.pos) || a.speed != b.speed || a.speed == 0 || b.tick - a.tick > 1) continue;
                    if (a.tick == b.tick && a.partial != b.partial) interpolated++;
                    if (b.tick == a.tick + 1) {
                        float expected = (1 + b.partial - a.partial) * a.speed * .3f;
                        float actual = Mth.wrapDegrees(b.angle - a.angle);
                        require(Math.abs(Mth.wrapDegrees(actual - expected)) < .01f, "Rotating model must not snap back when the next scene tick starts");
                        boundaryChecks++;
                    }
                }
                require(boundaryChecks > 40 && interpolated > 80, "Actual playback must cover many rendered subframes and tick boundaries");
                times.sort(Long::compare);
                System.out.println("PONDER_PLAYBACK_PASS frames=" + FRAMES.size() + " boundaries=" + boundaryChecks + " interpolated=" + interpolated
                    + " medianMs=" + times.get(times.size()/2)/1e6 + " p95Ms=" + times.get(times.size()*95/100)/1e6);
            });
            // Exercise the same viewport with distinct source programs and animated model types.
            for (var item : List.of(AllBlocks.MECHANICAL_MIXER.asItem(), AllBlocks.MECHANICAL_PRESS.asItem(), AllItems.BELT_CONNECTOR,
                    AllBlocks.FLUID_TANK.asItem(), AllBlocks.MECHANICAL_BEARING.asItem(), AllBlocks.DEPLOYER.asItem())) {
                context.runOnClient(client -> {
                    var ui = PonderUI.of(new ItemStack(item));
                    client.gui.setScreen(ui); ui.seekToTime(120);
                });
                context.waitTicks(5);
                context.runOnClient(client -> {
                    require(latest != null && !latest.frame().sections().isEmpty(), "Registered scene must produce native world geometry");
                    for (var section : latest.frame().sections()) for (var owner : section.blockEntities())
                        require(!owner.failure().hasFailed(), "Registered scene renderer failed: " + owner.state().getClass());
                });
                context.takeScreenshot("ponder-opaque-" + BuiltInRegistries.ITEM.getKey(item).getPath());
            }
            context.runOnClient(client -> client.gui.setScreen(null));
        } finally { active = recording = false; latest = null; FRAMES.clear(); }
    }

    private static void verifyOpaqueTop(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var level = new PonderLevel(BlockPos.ZERO, client.level);
            level.setBlock(new BlockPos(1, 0, 1), Blocks.GRASS_BLOCK.defaultBlockState(), 0);
            level.createBackup();
            var scene = new PonderScene(level, new PonderLocalization(), "create_test",
                BuiltInRegistries.ITEM.getKey(AllBlocks.SHAFT.asItem()), List.of(), List.of());
            var builder = scene.builder();
            builder.title("opaque_top", "Opaque grass top"); builder.configureBasePlate(0, 0, 3);
            builder.scaleSceneView(1.5f); builder.showBasePlate(); builder.idle(1000);
            var ui = new PonderUI(List.of(scene), null); client.gui.setScreen(ui); ui.seekToTime(20);
        });
        context.waitTicks(5);
        var point = context.computeOnClient(client -> {
            var ui = (PonderUI) client.gui.screen();
            var at = ui.getActiveScene().getTransform().sceneToScreen(new Vec3(1.5, 1, 1.5), 1);
            float scale = client.getWindow().getGuiScale();
            return new net.minecraft.world.phys.Vec2(at.x * scale, at.y * scale);
        });
        var screenshot = context.takeScreenshot("ponder-opaque-grass-top");
        try {
            var image = ImageIO.read(screenshot.toFile()); int green = 0, sampled = 0;
            for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
                int color = image.getRGB(Math.round(point.x) + dx, Math.round(point.y) + dy);
                int red = color >> 16 & 255, g = color >> 8 & 255, blue = color & 255;
                if (g > red * 1.06 && g > blue * 1.2) green++;
                sampled++;
            }
            require(green > sampled * .8, "Opaque grass top must cover its projected pixels; culled fronts must not expose dirt bottoms or background (green=" + green + "/" + sampled + ")");
            System.out.println("PONDER_OPAQUE_PIXELS_PASS green=" + green + "/" + sampled);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static void verifyDepthOcclusion(ClientGameTestContext context) {
        var back = new java.util.concurrent.atomic.AtomicReference<net.createmod.ponder.foundation.element.WorldSectionElementImpl>();
        var direction = new java.util.concurrent.atomic.AtomicReference<Vec3>();
        var center = new Vec3(1.5, .5, 1.5);
        var originalBack = new Vec3(5.5, .5, 1.5);
        context.runOnClient(client -> {
            var world = new PonderLevel(BlockPos.ZERO, client.level);
            var red = new BlockPos(1, 0, 1); var blue = new BlockPos(5, 0, 1);
            world.setBlock(red, Blocks.CONCRETE.red().defaultBlockState(), 0);
            world.setBlock(blue, Blocks.CONCRETE.blue().defaultBlockState(), 0); world.createBackup();
            var scene = new PonderScene(world, new PonderLocalization(), "create_test",
                BuiltInRegistries.ITEM.getKey(AllBlocks.SHAFT.asItem()), List.of(), List.of());
            var builder = scene.builder(); builder.title("opaque_depth", "Opaque depth");
            builder.configureBasePlate(0, 0, 3); builder.scaleSceneView(1.5f); builder.idle(1000);
            var ui = new PonderUI(List.of(scene), null); client.gui.setScreen(ui);
            var util = scene.getSceneBuildingUtil();
            var near = new net.createmod.ponder.foundation.element.WorldSectionElementImpl(util.select().position(red));
            var far = new net.createmod.ponder.foundation.element.WorldSectionElementImpl(util.select().position(blue));
            near.forceApplyFade(1); far.forceApplyFade(1); scene.addElement(near); scene.addElement(far);
            var transform = scene.getTransform();
            var toward = transform.screenToScene(0, 0, 1000, 1).subtract(transform.screenToScene(0, 0, 0, 1)).normalize();
            direction.set(toward); far.setAnimatedOffset(center.subtract(toward.scale(3)).subtract(originalBack), true);
            back.set(far);
        });
        context.waitTicks(5);
        var point = context.computeOnClient(client -> {
            var ui = (PonderUI) client.gui.screen(); var at = ui.getActiveScene().getTransform().sceneToScreen(center, 1);
            float scale = client.getWindow().getGuiScale();
            return new net.minecraft.world.phys.Vec2(at.x * scale, at.y * scale);
        });
        inspectDepthPixel(context.takeScreenshot("ponder-opaque-red-in-front"), point, false);
        context.runOnClient(client -> back.get().setAnimatedOffset(center.add(direction.get().scale(3)).subtract(originalBack), true));
        context.waitTicks(5);
        inspectDepthPixel(context.takeScreenshot("ponder-opaque-blue-in-front"), point, true);
    }
    private static void inspectDepthPixel(java.nio.file.Path screenshot, net.minecraft.world.phys.Vec2 point, boolean blueExpected) {
        try {
            var image = ImageIO.read(screenshot.toFile()); int matching = 0, total = 0;
            for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
                int color = image.getRGB(Math.round(point.x) + dx, Math.round(point.y) + dy);
                int red = color >> 16 & 255, blue = color & 255;
                if (blueExpected ? blue > red * 1.5 : red > blue * 1.5) matching++;
                total++;
            }
            require(matching > total * .9, "Near opaque cube must hide far cube, expected " + (blueExpected ? "blue" : "red") + ": " + matching + "/" + total);
            System.out.println("PONDER_DEPTH_PIXELS_PASS " + (blueExpected ? "blue" : "red") + "=" + matching + "/" + total);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
