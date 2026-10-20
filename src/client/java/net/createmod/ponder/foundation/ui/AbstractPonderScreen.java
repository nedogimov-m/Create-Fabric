// Ponder1.0.82 navigation, MIT; native26.3 Screen adaptation.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;

/** Actual parent-screen navigation; each screen retains its own native widgets and scene context. */
public abstract class AbstractPonderScreen extends Screen {
    public final LerpedFloat transition = LerpedFloat.linear().startWithValue(0).chase(0, .1f, LerpedFloat.Chaser.LINEAR);
    protected int depthPointX, depthPointY;
    protected final Screen parent;
    private PonderButton backTrack;
    private final LerpedFloat arrowAnimation = LerpedFloat.linear().startWithValue(0).chase(0, .075f, LerpedFloat.Chaser.LINEAR);
    protected AbstractPonderScreen(Component title, Screen parent) {
        super(title); this.parent = parent instanceof AbstractPonderScreen ? null : parent;
        var window = Minecraft.getInstance().getWindow(); depthPointX = window.getGuiScaledWidth() / 2; depthPointY = window.getGuiScaledHeight() / 2;
    }
    protected AbstractPonderScreen(Component title) { this(title, Minecraft.getInstance().gui.screen()); }
    public static Component text(String key, Object... arguments) { return Component.translatable("ponder.ui." + key, arguments); }
    protected PonderButton button(int x, int y, int width, Component label, Runnable action) {
        return addRenderableWidget(new PonderButton(x, y, width, 20, label, action));
    }
    protected void open(Screen next) {
        centerScalingOnMouse();
        if (next instanceof AbstractPonderScreen navigable) ScreenOpener.transitionTo(navigable);
        else ScreenOpener.open(this, next);
    }
    protected void stepBack() {
        if (ScreenOpener.getBackStepScreen() != null) ScreenOpener.openPreviousScreen(this, null);
        else { ScreenOpener.clearStack(); minecraft.gui.setScreen(parent); }
    }
    public boolean isEquivalentTo(AbstractPonderScreen other) { return false; }
    public void shareContextWith(AbstractPonderScreen other) {}
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_BACKSPACE) { stepBack(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { ScreenOpener.clearStack(); minecraft.gui.setScreen(null); }
    @Override public boolean isPauseScreen() { return true; }
    @Override protected void init() {
        super.init();
        backTrack = button(31, height - 51, 20, Component.literal("<"), this::stepBack).hint(text(ScreenOpener.getBackStepScreen() instanceof AbstractPonderScreen ? "think_back" : "exit"));
    }
    public void centerScalingOn(int x, int y) { depthPointX = x; depthPointY = y; }
    public void centerScalingOnMouse() {
        var window = minecraft.getWindow();
        centerScalingOn((int) minecraft.mouseHandler.getScaledXPos(window), (int) minecraft.mouseHandler.getScaledYPos(window));
    }
    @Override public void tick() { super.tick(); arrowAnimation.tickChaser(); transition.tickChaser(); }
    protected abstract void extractPonderContent(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial);
    @Override public final void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        boolean previousCapture = PonderNavigationRenderer.isRenderingPrevious();
        if (!previousCapture) gui.fillGradient(0, 0, width, height, 0xF00E0E20, 0xF0000000);
        gui.pose().pushMatrix();
        try {
            if (transition.getChaseTarget() != 0 && !transition.settled()) {
                float value = transition.getValue(partial), amount = Math.abs(value);
                Screen previous = ScreenOpener.getPreviouslyRenderedScreen();
                if (!previousCapture && previous != null && previous != this) {
                    int x = width / 2, y = height / 2;
                    if (value > 0 && previous instanceof AbstractPonderScreen navigable) { x = navigable.depthPointX; y = navigable.depthPointY; }
                    float scale = 1 + .2f * value;
                    gui.pose().pushMatrix(); gui.pose().translate(x, y); gui.pose().scale(scale, scale); gui.pose().translate(-x, -y);
                    try { PonderNavigationRenderer.extract(gui, previous, width, height, partial, 1 - amount); }
                    finally { gui.pose().popMatrix(); }
                }
                float scale = value > 0 ? 1 - .5f * (1 - amount) : 1 + .5f * (1 - amount);
                gui.pose().translate(width / 2f, height / 2f); gui.pose().scale(scale, scale); gui.pose().translate(-width / 2f, -height / 2f);
            }
            extractPonderContent(gui, mouseX, mouseY, partial);
        } finally { gui.pose().popMatrix(); }
    }
    protected final void extractWidgets(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        if (backTrack != null) {
            int x = (int) (-9 + 30 * arrowAnimation.getValue(partial)), maxX = backTrack.getRight();
            if (x + 30 < backTrack.getX()) PonderUIRenderHelper.breadcrumb(gui, x + 30, height - 51, maxX - (x + 30), 20, 5, 0x80AA9999, 0x30AA9999);
            PonderUIRenderHelper.breadcrumb(gui, x, height - 51, 30, 20, 5, 0x80AA9999, 0x30AA9999);
            PonderUIRenderHelper.breadcrumb(gui, x - 30, height - 51, 30, 20, 5, 0x80AA9999, 0x30AA9999);
            if (backTrack.isMouseOver(mouseX, mouseY) || backTrack.isFocused()) {
                gui.centeredText(font, text(ScreenOpener.getBackStepScreen() instanceof AbstractPonderScreen ? "think_back" : "exit"), 41, height - 16, 0xFFA3A3A3);
                if (arrowAnimation.settled()) { arrowAnimation.setValue(1); arrowAnimation.setValue(1); }
            }
        }
        super.extractRenderState(gui, mouseX, mouseY, partial);
    }
    protected void background(GuiGraphicsExtractor gui) {
        gui.centeredText(font, title, width / 2, 17, 0xFFFFEEDD);
    }
}
