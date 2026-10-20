package net.createmod.ponder.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec2;

/** Actual source screens, physical native navigation and observed scoped Vulkan GUI passes. UNRUN. */
public final class PonderZoomTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var evidence = new JsonObject(); boolean passed = false;
        try {
          try (var joined = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                PonderZoomRenderAudit.resetObservations();
                String backend = RenderSystem.getDevice().getDeviceInfo().backendName(); evidence.addProperty("actualBackend", backend);
                require(backend.toLowerCase(java.util.Locale.ROOT).contains("vulkan"), "Actual Vulkan backend required");
                require(!FabricLoader.getInstance().isModLoaded("sodium"), "Actual runtime must satisfy requested no-Sodium configuration");
            });
            var ui = context.computeOnClient(client -> {
                var subject = PonderIndex.getSceneAccess().getRegisteredEntries().stream().map(Map.Entry::getKey).distinct()
                    .filter(id -> id.getNamespace().equals("create") && BuiltInRegistries.ITEM.containsKey(id))
                    .filter(id -> !PonderIndex.getTagAccess().getTags(id).isEmpty()).findFirst().orElseThrow();
                var screen = PonderUI.of(subject); ScreenOpener.transitionTo(screen); return screen;
            });
            context.waitTicks(30);
            Vec2 click = context.computeOnClient(client -> {
                var button = ui.children().stream().filter(PonderButton.class::isInstance).map(PonderButton.class::cast)
                    .filter(value -> value.active && value.getTag() != null).findFirst().orElseThrow();
                int x = button.getX() + button.getWidth() / 2, y = button.getY() + button.getHeight() / 2;
                PonderZoomRenderAudit.expect(ui, true, x, y); return new Vec2(x, y);
            });
            float scale = context.computeOnClient(client -> (float) client.getWindow().getGuiScale());
            context.getInput().setCursorPos(click.x * scale, click.y * scale); context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
            int oldClock = context.computeOnClient(client -> ui.getActiveScene().getWorld().simulationTicks());
            var ordinaryTarget = context.computeOnClient(client -> client.gameRenderer.mainRenderTarget());
            context.waitFor(client -> PonderZoomRenderAudit.complete(true) >= 2, 200);
            context.waitTicks(4);
            context.runOnClient(client -> {
                require(ui.getActiveScene().getWorld().simulationTicks() == oldClock,
                    "Previous unpaused source UI must rerender without advancing its actual world clock");
                require(client.gameRenderer.mainRenderTarget() == ordinaryTarget,
                    "Source capture must preserve GameRenderer's actual ordinary output owner");
            });
            screenshot(context, evidence, "forward", "create-ponder-native-forward-depth-zoom-alpha");
            var tag = context.computeOnClient(client -> (PonderTagScreen) client.gui.screen());
            context.runOnClient(client -> PonderZoomRenderAudit.expect(tag, false, 0, 0));
            context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            context.waitFor(client -> client.gui.screen() == ui, 200);
            context.waitFor(client -> PonderZoomRenderAudit.complete(false) >= 2, 200);
            context.runOnClient(client -> require(client.gameRenderer.mainRenderTarget() == ordinaryTarget,
                "Backward source capture must preserve actual ordinary output owner"));
            screenshot(context, evidence, "backward", "create-ponder-native-backward-center-zoom-alpha");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null && ScreenOpener.getScreenHistory().isEmpty(), 200);
          }
          passed = true;
        } catch (RuntimeException | Error failure) {
            evidence.addProperty("failure", failure.toString()); throw failure;
        } finally {
            JsonObject report = context.computeOnClient(client -> PonderZoomRenderAudit.report());
            report.addProperty("status", passed ? "PASS" : "FAIL"); report.add("nativeEvidence", evidence);
            report.addProperty("limits", "Actual native capture/draw/blit and physical input observed. Screenshot hashes are not original-source golden pixel comparison.");
            Path output = Path.of("build/reports/create-ponder/navigation-zoom-runtime.json").toAbsolutePath();
            try { Files.createDirectories(output.getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    private static void screenshot(ClientGameTestContext context, JsonObject evidence, String key, String name) {
        Path path = context.takeScreenshot(name);
        try {
            var shot = new JsonObject(); shot.addProperty("path", path.toAbsolutePath().toString());
            shot.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))); evidence.add(key, shot);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
