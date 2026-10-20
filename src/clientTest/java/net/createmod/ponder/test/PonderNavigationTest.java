package net.createmod.ponder.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.test.mixin.PonderNavigationAccessor;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

/** Genuine registry scenes and native physical inputs; no authored scene/history/state substitution. UNRUN. */
public final class PonderNavigationTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var joined = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                require(ScreenOpener.getScreenHistory().isEmpty(), "New joined world must start with empty navigation history");
                require(RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(java.util.Locale.ROOT).contains("vulkan"),
                    "Actual Vulkan backend required");
                require(!FabricLoader.getInstance().isModLoaded("sodium"), "Requested no-Sodium runtime required");
            });
            var first = openActualSubject(context);
            pauseAndSeekPhysically(context);
            int initialTime = context.computeOnClient(client -> first.getActiveScene().getCurrentTime());
            var initialScene = context.computeOnClient(client -> first.getActiveScene());
            require(initialTime > 0, "Native timeline End must execute the genuine source scene schedule");
            var tag = context.computeOnClient(client -> first.children().stream().filter(PonderButton.class::isInstance)
                .map(PonderButton.class::cast).map(PonderButton::getTag).filter(value -> value != null).findFirst().orElseThrow());
            click(context, widget -> widget instanceof PonderButton button && button.getTag() == tag);
            context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
            context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            context.waitFor(client -> client.gui.screen() == first, 200);
            context.runOnClient(client -> require(first.getActiveScene() == initialScene && first.getActiveScene().getCurrentTime() == initialTime,
                "Real Backspace must retain exact paused scene owner and playback position"));
            click(context, widget -> widget instanceof PonderButton button && button.getTag() == tag);
            context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
            click(context, PonderNavigationTest::isBackWidget);
            context.waitFor(client -> client.gui.screen() == first, 200);
            context.runOnClient(client -> require(first.getActiveScene() == initialScene && first.getActiveScene().getCurrentTime() == initialTime,
                "Real back-widget click must retain exact paused scene owner and playback position"));
            context.takeScreenshot("create-ponder-navigation-native-paused-title-and-history");

            var expectedContextScene = context.computeOnClient(client -> ((PonderNavigationAccessor) (Object) first).create$actualScenes()
                .stream().filter(scene -> scene.getTags().contains(tag)).findFirst().orElse(initialScene));
            // Returning to the same subject through its genuine tag item must reuse the previous PonderUI.
            click(context, widget -> widget instanceof PonderButton button && button.getTag() == tag);
            context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
            var subject = context.computeOnClient(client -> first.getSubject());
            findItemPage(context, subject);
            click(context, widget -> itemMatches(widget, subject));
            context.waitFor(client -> client.gui.screen() == first, 200);
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(ScreenOpener.getScreenHistory().isEmpty(), "Equivalent subject must pop its tag screen, preserving original root UI identity");
                require(first.getSubject().is(subject.getItem()), "Equivalent native subject identity lost");
                require(first.getActiveScene() == expectedContextScene,
                    "Actual source tag context must select the first relevant original scene owner when equivalent subject is reused");
                if (expectedContextScene == initialScene) require(first.getActiveScene().getCurrentTime() == initialTime,
                    "Equivalent subject must retain actual paused playback position when source tag keeps the same scene");
            });

            // Returning to a genuine tag through a subject's tag button reuses its prior tag screen and page.
            click(context, widget -> widget instanceof PonderButton button && button.getTag() == tag);
            context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
            var tagScreen = context.computeOnClient(client -> (PonderTagScreen) client.gui.screen());
            clickFirstSceneItem(context);
            context.waitFor(client -> client.gui.screen() instanceof PonderUI, 200);
            if (context.computeOnClient(client -> client.gui.screen() == first)) {
                // Source nearest-subject coalescing legitimately returned to the previous UI; reopen tag and choose a different subject.
                click(context, widget -> widget instanceof PonderButton button && button.getTag() == tag);
                context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
                tagScreen = context.computeOnClient(client -> (PonderTagScreen) client.gui.screen());
                clickDifferentSceneItem(context, subject);
                context.waitFor(client -> client.gui.screen() instanceof PonderUI && client.gui.screen() != first, 200);
            }
            final var originalTag = tagScreen;
            var originalPageItems = context.computeOnClient(client -> originalTag.children().stream().filter(PonderButton.class::isInstance)
                .map(PonderButton.class::cast).map(PonderButton::getItem).filter(stack -> !stack.isEmpty())
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())).toList());
            click(context, widget -> widget instanceof PonderButton button && button.getTag() == tag);
            context.waitFor(client -> client.gui.screen() == originalTag, 200);
            context.runOnClient(client -> require(originalTag.children().stream().filter(PonderButton.class::isInstance)
                .map(PonderButton.class::cast).map(PonderButton::getItem).filter(stack -> !stack.isEmpty())
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())).toList().equals(originalPageItems),
                "Equivalent tag must retain the exact visible source page"));
            context.takeScreenshot("create-ponder-navigation-native-tag-equivalence-and-diagnostics");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null && ScreenOpener.getScreenHistory().isEmpty(), 200);

            var current = openActualSubject(context);
            var pushed = new ArrayList<Screen>();
            for (int cycle = 0; cycle < 7; cycle++) {
                pushed.add(current);
                click(context, widget -> widget instanceof PonderButton button && button.getMessage().getString().equals(AbstractPonderScreen.text("associated").getString()));
                context.waitFor(client -> client.gui.screen() instanceof PonderTagIndexScreen, 200);
                var categories = context.computeOnClient(client -> client.gui.screen()); pushed.add(categories);
                clickListedUsableTag(context);
                context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen, 200);
                var category = context.computeOnClient(client -> client.gui.screen()); pushed.add(category);
                clickFirstSceneItem(context);
                context.waitFor(client -> client.gui.screen() instanceof PonderUI, 200);
                current = context.computeOnClient(client -> (PonderUI) client.gui.screen());
                final int count = pushed.size();
                context.runOnClient(client -> require(ScreenOpener.getScreenHistory().size() == Math.min(15, count),
                    "Genuine widget navigation must enforce the source15-entry cap after each push"));
            }
            context.runOnClient(client -> {
                var history = ScreenOpener.getScreenHistory(); require(history.size() == 15, "History must saturate at source15");
                for (int i = 0; i < 15; i++) require(history.get(i) == pushed.get(pushed.size() - 1 - i),
                    "Bounded history must retain the actual most recent native screens in source order");
            });
            click(context, widget -> widget instanceof PonderButton button && button.getMessage().getString().equals(AbstractPonderScreen.text("close").getString()));
            context.waitFor(client -> client.gui.screen() == null && ScreenOpener.getScreenHistory().isEmpty(), 200);
            openActualSubject(context);
            click(context, widget -> widget instanceof PonderButton button && button.getTag() != null);
            context.waitFor(client -> client.gui.screen() instanceof PonderTagScreen && !ScreenOpener.getScreenHistory().isEmpty(), 200);
            // Closing the actual Fabric world context below must fire the registered disconnect cleanup.
        }
        context.waitFor(client -> client.level == null && ScreenOpener.getScreenHistory().isEmpty()
            && ScreenOpener.getPreviouslyRenderedScreen() == null && !(client.gui.screen() instanceof AbstractPonderScreen), 300);
    }
    private static PonderUI openActualSubject(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            Identifier subject = PonderIndex.getSceneAccess().getRegisteredEntries().stream().map(Map.Entry::getKey).distinct()
                .filter(id -> id.getNamespace().equals("create") && BuiltInRegistries.ITEM.containsKey(id))
                .filter(id -> !PonderIndex.getTagAccess().getTags(id).isEmpty()).findFirst().orElseThrow();
            var ui = PonderUI.of(subject); ScreenOpener.transitionTo(ui); return ui;
        });
    }
    private static void pauseAndSeekPhysically(ClientGameTestContext context) {
        click(context, PonderProgressBar.class::isInstance);
        context.getInput().pressKey(InputConstants.KEY_SPACE); context.getInput().pressKey(InputConstants.KEY_END);
        int time = context.computeOnClient(client -> ((PonderUI) client.gui.screen()).getActiveScene().getCurrentTime());
        context.waitTicks(3);
        require(context.computeOnClient(client -> ((PonderUI) client.gui.screen()).getActiveScene().getCurrentTime()) == time,
            "Actual physical pause must hold the source schedule while native UI frames continue");
    }
    private static boolean isBackWidget(AbstractWidget widget) {
        return widget instanceof PonderButton && widget.getX() == 31 && widget.getWidth() == 20
            && widget.getMessage().getString().equals("<");
    }
    private static boolean itemMatches(AbstractWidget widget, ItemStack wanted) {
        return widget instanceof PonderButton button && !button.getItem().isEmpty() && button.getItem().is(wanted.getItem());
    }
    private static void findItemPage(ClientGameTestContext context, ItemStack subject) {
        for (int page = 0; page < 100; page++) {
            if (has(context, widget -> itemMatches(widget, subject))) return;
            click(context, PonderNavigationTest::isNextPage);
        }
        throw new AssertionError("Actual tagged subject absent after100native page actions");
    }
    private static boolean isNextPage(AbstractWidget widget) {
        return widget instanceof PonderButton && widget.getWidth() == 30 && widget.getMessage().getString().equals(">");
    }
    private static void clickFirstSceneItem(ClientGameTestContext context) {
        for (int page = 0; page < 100; page++) {
            if (has(context, widget -> widget instanceof PonderButton button && !button.getItem().isEmpty())) {
                click(context, widget -> widget instanceof PonderButton button && !button.getItem().isEmpty()); return;
            }
            click(context, PonderNavigationTest::isNextPage);
        }
        throw new AssertionError("No genuine tagged source scene available");
    }
    private static void clickDifferentSceneItem(ClientGameTestContext context, ItemStack subject) {
        for (int page = 0; page < 100; page++) {
            Predicate<AbstractWidget> different = widget -> widget instanceof PonderButton button && !button.getItem().isEmpty() && !button.getItem().is(subject.getItem());
            if (has(context, different)) { click(context, different); return; }
            click(context, PonderNavigationTest::isNextPage);
        }
        throw new AssertionError("Source tag lacks another actual subject for equivalence navigation");
    }
    private static void clickListedUsableTag(ClientGameTestContext context) {
        Predicate<AbstractWidget> usable = widget -> widget instanceof PonderButton button && button.getTag() != null
            && PonderIndex.getTagAccess().getItems(button.getTag()).stream().anyMatch(PonderIndex.getSceneAccess()::doScenesExistForId);
        for (int page = 0; page < 100; page++) {
            if (has(context, usable)) { click(context, usable); return; }
            click(context, PonderNavigationTest::isNextPage);
        }
        throw new AssertionError("Actual listed tags lack source scenes");
    }
    private static boolean has(ClientGameTestContext context, Predicate<AbstractWidget> selector) {
        return context.computeOnClient(client -> client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).anyMatch(widget -> widget.active && widget.visible && selector.test(widget)));
    }
    private static void click(ClientGameTestContext context, Predicate<AbstractWidget> selector) {
        context.waitTicks(4);
        Vec2 position = context.computeOnClient(client -> {
            var widget = client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(value -> value.active && value.visible && selector.test(value)).findFirst().orElseThrow();
            float scale = client.getWindow().getGuiScale();
            return new Vec2((widget.getX() + widget.getWidth() / 2f) * scale, (widget.getY() + widget.getHeight() / 2f) * scale);
        });
        context.getInput().setCursorPos(position.x, position.y); context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
