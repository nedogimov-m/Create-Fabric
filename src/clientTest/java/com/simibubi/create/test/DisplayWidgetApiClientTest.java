package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySourceConfigurationWidgets;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkMenu;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkScreen;
import com.simibubi.create.foundation.gui.widget.Label;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.Vec2;

/** One actual client/menu/input/wire/authoritative-schema scenario. Authored source-only, UNRUN. */
public final class DisplayWidgetApiClientTest implements FabricClientGameTest {
    private static final BlockPos LINK = new BlockPos(32, 5, 32), SOURCE = LINK.below(), TARGET = LINK.east(3);
    private static final String LONG_CAPTION = "abcdefghijkl", ACCEPTED_CAPTION = "abcdefgh";
    private static final String SCREENSHOT = "create-vulkan-custom-display-source-native-widgets";
    private static final Path REPORT = Path.of(System.getProperty("create.test.displayWidgetReport",
        "build/reports/create-display-widget-api/native-client-runtime.json")).toAbsolutePath();
    private static final AtomicInteger FACTORY_CALLS = new AtomicInteger();
    private static final AtomicReference<Widgets> ACTUAL = new AtomicReference<>();

    private record Widgets(DisplaySourceConfigurationWidgets.Context context, EditBox number,
                           EditBox caption, Label label, Tooltip tooltip) {}

    private static final class Report {
        String status = "RUNNING", backend, failure, screenshot = SCREENSHOT;
        boolean sodiumLoaded, registeredNativeSource, realScreenChildren, realDraftIdentity,
            nativeTooltip, nativeTooltipExtractionObserved, customLabelUpdated, authoritativeBoundsAndTruncation,
            authoritativeUnknownKeyRejected, actualSignText;
        int factoryCalls, rawClientNumber, authoritativeNumber;
        String rawClientCaption, authoritativeCaption;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new Report();
        FACTORY_CALLS.set(0);
        ACTUAL.set(null);
        context.runOnClient(client -> {
            report.backend = RenderSystem.getDevice().getDeviceInfo().backendName();
            report.sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
            require(report.backend.toLowerCase(Locale.ROOT).contains("vulkan") && !report.sodiumLoaded,
                "The focused widget test must use actual native Vulkan without Sodium");
            report.registeredNativeSource = CreateBuiltInRegistries.DISPLAY_SOURCE.getValue(DisplayWidgetApiTestFixtures.ID)
                == DisplayWidgetApiTestFixtures.SOURCE;
            require(report.registeredNativeSource, "Common fixture must register the genuine source before registry freeze");
            DisplaySourceConfigurationWidgets.REGISTRY.register(DisplayWidgetApiTestFixtures.SOURCE,
                DisplayWidgetApiClientTest::createWidgets);
        });
        try (var world = context.worldBuilder().adjustSettings(settings ->
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
            var server = world.getServer();
            server.runCommand("tp @p 32.5 5 35.5");
            server.runCommand("fill 30 4 30 38 4 37 minecraft:smooth_quartz");
            context.waitFor(client -> client.level != null && client.level.isLoaded(LINK)
                && client.level.isLoaded(TARGET) && client.player.position().distanceToSqr(
                    new net.minecraft.world.phys.Vec3(32.5, 5, 35.5)) < 1, 400);
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                var player = level.getServer().getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                level.setBlockAndUpdate(SOURCE, Blocks.DIAMOND_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(TARGET.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(TARGET, Blocks.OAK_SIGN.defaultBlockState());
                level.setBlockAndUpdate(LINK, AllBlocks.DISPLAY_LINK.defaultBlockState()
                    .setValue(DisplayLinkBlock.FACING, Direction.UP));
                var link = link(level);
                link.target(TARGET);
                var initial = new net.minecraft.nbt.CompoundTag();
                initial.putString("Id", DisplayWidgetApiTestFixtures.ID.toString());
                require(DisplaySource.getAll(level, SOURCE).contains(DisplayWidgetApiTestFixtures.SOURCE)
                    && link.configure(initial, 0), "Actual source association and loaded sign target must configure");
                require(sign(level).equals("seed:3"), "Actual source callback must populate real sign before menu opens");
                DisplayLinkMenu.open(player, link);
            });
            context.waitFor(client -> client.gui.screen() instanceof DisplayLinkScreen && ACTUAL.get() != null, 300);
            context.runOnClient(client -> {
                var screen = (DisplayLinkScreen) client.gui.screen();
                var widgets = ACTUAL.get();
                report.factoryCalls = FACTORY_CALLS.get();
                report.realScreenChildren = screen.children().contains(widgets.number())
                    && screen.children().contains(widgets.caption()) && screen.children().contains(widgets.label());
                report.realDraftIdentity = widgets.context().draft() == screen.draft()
                    && widgets.context().opening() == screen.getMenu().opening()
                    && widgets.context().source().id().equals(screen.selectedSource());
                report.nativeTooltip = tooltipValue(widgets.number(), "tooltip") == widgets.tooltip();
                require(report.factoryCalls > 0 && report.realScreenChildren && report.realDraftIdentity && report.nativeTooltip,
                    "Actual screen must invoke the registered factory/add real children using its native menu/draft");
                require(widgets.context().width() == 135 && widgets.number().getX() == widgets.context().x() + 7
                    && widgets.number().getY() == widgets.context().y() && widgets.number().getWidth() == 54
                    && widgets.caption().getWidth() == 112, "Custom factory geometry must reach actual native widgets");
                require(screen.children().stream().filter(EditBox.class::isInstance).count() == 2,
                    "Registered factory replaces the default source-option widgets exactly once per init");
            });
            type(context, true, "999");
            type(context, false, LONG_CAPTION);
            context.runOnClient(client -> {
                var screen = (DisplayLinkScreen) client.gui.screen();
                var widgets = ACTUAL.get();
                report.rawClientNumber = screen.draft().getIntOr(DisplayWidgetApiTestFixtures.NUMBER, 0);
                report.rawClientCaption = screen.draft().getStringOr(DisplayWidgetApiTestFixtures.CAPTION, "");
                report.customLabelUpdated = widgets.label().text.getString().equals("custom=999");
                require(report.rawClientNumber == 999 && LONG_CAPTION.equals(report.rawClientCaption)
                    && report.customLabelUpdated, "Actual mouse/key input must drive the custom integer/text widgets and label");
                screen.draft().putString("UnknownWidgetKey", "must not reach authoritative source config");
            });
            hoverNumber(context);
            context.waitTicks(25);
            context.runOnClient(client -> {
                report.nativeTooltipExtractionObserved = Boolean.TRUE.equals(tooltipValue(ACTUAL.get().number(), "wasDisplayed"));
                require(report.nativeTooltipExtractionObserved,
                    "Actual native widget extraction must observe the real pointer/focus tooltip; screenshot records its visible output");
            });
            context.takeScreenshot(SCREENSHOT);
            context.clickScreenButton("✓");
            context.waitFor(client -> client.gui.screen() == null, 200);
            await(context, world, level -> link(level).getSourceConfig().getIntOr(DisplayWidgetApiTestFixtures.NUMBER, 0) == 8
                && ACCEPTED_CAPTION.equals(link(level).getSourceConfig().getStringOr(DisplayWidgetApiTestFixtures.CAPTION, "")),
                "Native editor confirmation/C2S must apply existing authoritative scalar range and string limit");
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                var link = link(level); var data = link.getSourceConfig();
                report.authoritativeNumber = data.getIntOr(DisplayWidgetApiTestFixtures.NUMBER, 0);
                report.authoritativeCaption = data.getStringOr(DisplayWidgetApiTestFixtures.CAPTION, "");
                report.authoritativeBoundsAndTruncation = report.authoritativeNumber == 8
                    && ACCEPTED_CAPTION.equals(report.authoritativeCaption);
                report.authoritativeUnknownKeyRejected = !data.contains("UnknownWidgetKey");
                report.actualSignText = sign(level).equals("abcdefgh:8");
                require(report.authoritativeBoundsAndTruncation && report.authoritativeUnknownKeyRejected
                    && report.actualSignText && link.activeSource == DisplayWidgetApiTestFixtures.SOURCE,
                    "Server must validate custom widget draft and deliver accepted values through the real source/target");
            });
            report.status = "PASS";
        } catch (Throwable failure) {
            report.status = "FAIL";
            var trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); report.failure = trace.toString();
            if (failure instanceof AssertionError assertion) throw assertion;
            throw new AssertionError(failure);
        } finally {
            write(report);
        }
    }

    private static void createWidgets(DisplaySourceConfigurationWidgets.Context context) {
        require(context.source().id().equals(DisplayWidgetApiTestFixtures.ID)
            && context.opening().sources().stream().anyMatch(entry -> entry == context.source()),
            "Factory must receive the actual selected server-snapshotted source entry");
        var label = new Label(context.x() + 68, context.y() + 4, Component.empty()).withShadow();
        label.setTextAndTrim(Component.literal("custom=" + context.draft().getIntOr(DisplayWidgetApiTestFixtures.NUMBER, 3)), false, 80);
        var tooltip = Tooltip.create(Component.literal("Custom integer text input supplied by the public source widget API"));
        var number = new EditBox(context.font(), context.x() + 7, context.y(), 54, 18,
            Component.literal("Custom source threshold")) {
            @Override public void insertText(String text) {
                // Native26.3 removed EditBox.setFilter; this custom widget accepts digit input.
                if (text.matches("[0-9]+")) super.insertText(text);
            }
        };
        number.setMaxLength(5);
        number.setValue(Integer.toString(context.draft().getIntOr(DisplayWidgetApiTestFixtures.NUMBER, 3)));
        number.setTooltip(tooltip);
        number.setTooltipDelay(java.time.Duration.ZERO);
        number.setResponder(value -> {
            if (!value.isEmpty()) {
                int parsed = Integer.parseInt(value);
                context.draft().putInt(DisplayWidgetApiTestFixtures.NUMBER, parsed);
                label.setTextAndTrim(Component.literal("custom=" + parsed), false, 80);
            }
        });
        var caption = new EditBox(context.font(), context.x() + 7, context.y() + 21, 112, 18,
            Component.literal("Custom source caption"));
        caption.setMaxLength(64); // Deliberately wider than authoritative descriptor limit8.
        caption.setValue(context.draft().getStringOr(DisplayWidgetApiTestFixtures.CAPTION, ""));
        caption.setResponder(value -> context.draft().putString(DisplayWidgetApiTestFixtures.CAPTION, value));
        context.add(number); context.add(label); context.add(caption);
        ACTUAL.set(new Widgets(context, number, caption, label, tooltip));
        FACTORY_CALLS.incrementAndGet();
    }

    private static void type(ClientGameTestContext context, boolean number, String value) {
        var point = context.computeOnClient(client -> {
            var field = number ? ACTUAL.get().number() : ACTUAL.get().caption();
            field.setValue("");
            float scaleX = (float) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
            float scaleY = (float) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
            return new Vec2((field.getX() + field.getWidth() / 2f) * scaleX, (field.getY() + 9) * scaleY);
        });
        context.getInput().setCursorPos(point.x, point.y);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().typeChars(value);
        context.runOnClient(client -> require((number ? ACTUAL.get().number() : ACTUAL.get().caption()).getValue().equals(value),
            "Actual native input did not reach the source widget"));
    }

    private static void hoverNumber(ClientGameTestContext context) {
        var point = context.computeOnClient(client -> {
            var field = ACTUAL.get().number();
            float scaleX = (float) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
            float scaleY = (float) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
            return new Vec2((field.getX() + field.getWidth() / 2f) * scaleX, (field.getY() + 9) * scaleY);
        });
        context.getInput().setCursorPos(point.x, point.y);
    }

    private static DisplayLinkBlockEntity link(ServerLevel level) { return (DisplayLinkBlockEntity) level.getBlockEntity(LINK); }
    /** Read actual native holder state;26.3 exposes setTooltip but no getTooltip. No native fields are mutated. */
    private static Object tooltipValue(AbstractWidget widget, String name) {
        try {
            var holderField = AbstractWidget.class.getDeclaredField("tooltip"); holderField.setAccessible(true);
            var holder = holderField.get(widget);
            var field = holder.getClass().getDeclaredField(name); field.setAccessible(true);
            return field.get(holder);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect actual native tooltip holder", failure); }
    }
    private static String sign(ServerLevel level) {
        return ((SignBlockEntity) level.getBlockEntity(TARGET)).getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString();
    }
    private static void await(ClientGameTestContext context, TestSingleplayerContext world,
                              java.util.function.Predicate<ServerLevel> condition, String failure) {
        for (int i = 0; i < 250; i++) {
            if (world.getServer().computeOnServer(ignored -> condition.test(world.getConnection().getServerLevel()))) return;
            context.waitTicks(1);
        }
        throw new AssertionError(failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void write(Report report) {
        try { Files.createDirectories(REPORT.getParent()); Files.writeString(REPORT, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new AssertionError("Cannot write focused display widget receipt", failure); }
    }
}
