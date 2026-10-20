// Ponder/Catnip 1.0.82 ScreenOpener, MIT; native 26.3 screen ownership adaptation.
package net.createmod.catnip.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderNavigationRenderer;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Source bounded history and equivalent-screen backtracking; screens retain their real scene context. */
public final class ScreenOpener {
    private static final Deque<Screen> backStack = new ArrayDeque<>();
    private static Screen backSteppedFrom;
    private ScreenOpener() {}

    public static void open(Screen screen) { open(Minecraft.getInstance().gui.screen(), screen); }
    public static void open(Screen current, Screen toOpen) {
        backSteppedFrom = null;
        if (current == null) backStack.clear();
        else {
            if (backStack.size() >= 15) backStack.pollLast();
            backStack.push(current);
        }
        openScreen(toOpen);
    }
    public static void openPreviousScreen(Screen current, AbstractPonderScreen screenWithContext) {
        if (backStack.isEmpty()) return;
        backSteppedFrom = current;
        Screen previous = backStack.pop();
        if (previous instanceof AbstractPonderScreen navigable) {
            if (screenWithContext != null) screenWithContext.shareContextWith(navigable);
            navigable.transition.startWithValue(-.001).chase(-1, .3f, LerpedFloat.Chaser.EXP);
        }
        openScreen(previous);
    }
    public static void transitionTo(AbstractPonderScreen screen) {
        Screen previous = backStack.peek();
        if (previous instanceof AbstractPonderScreen navigable && screen.isEquivalentTo(navigable)) {
            openPreviousScreen(Minecraft.getInstance().gui.screen(), screen);
            return;
        }
        screen.transition.startWithValue(.001).chase(1, .3f, LerpedFloat.Chaser.EXP);
        open(screen);
    }
    private static void openScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
        Screen previous = getPreviouslyRenderedScreen();
        if (screen instanceof AbstractPonderScreen && previous != null && previous != screen) previous.init(screen.width, screen.height);
    }
    public static void clearStack() { backStack.clear(); backSteppedFrom = null; PonderNavigationRenderer.clearCapture(); }
    public static List<Screen> getScreenHistory() { return new ArrayList<>(backStack); }
    public static Screen getBackStepScreen() { return backStack.peek(); }
    public static Screen getPreviouslyRenderedScreen() { return backSteppedFrom != null ? backSteppedFrom : backStack.peek(); }
}
