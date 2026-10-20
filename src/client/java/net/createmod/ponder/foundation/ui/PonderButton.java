// Ponder1.0.82 button behavior/palette, MIT; native26.3 accessible widget extraction.
package net.createmod.ponder.foundation.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Objects;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.AnimationTickHolder;
import java.util.Locale;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class PonderButton extends AbstractWidget {
    private final Runnable action;
    private ScreenElement icon;
    private ItemStack item = ItemStack.EMPTY;
    private PonderTag tag;
    private KeyMapping shortcut;
    private Integer missingBorderTop, missingBorderBottom;
    private final LerpedFloat flash = LerpedFloat.linear().startWithValue(0).chase(0, .1f, LerpedFloat.Chaser.EXP);
    public PonderButton(int x, int y, int width, int height, Component label, Runnable action) {
        super(x, y, width, height, label); this.action = Objects.requireNonNull(action);
    }
    public PonderButton showing(ScreenElement icon) { this.icon = icon; return this; }
    public PonderButton showing(ItemStack item) { this.item = item.copy(); return this; }
    public PonderButton showingTag(PonderTag tag) { this.tag = tag; this.icon = tag; return this; }
    public PonderButton withShortcut(KeyMapping shortcut) { this.shortcut = shortcut; return this; }
    public PonderButton hint(Component component) { setTooltip(Tooltip.create(component)); return this; }
    public PonderButton missingScene(boolean vanilla) {
        missingBorderTop = vanilla ? 0x505000FF : 0x70984500;
        missingBorderBottom = vanilla ? 0x5028007F : 0x70692400;
        return this;
    }
    public void flash() { flash.updateChaseTarget(1); }
    public void dim() { flash.updateChaseTarget(0); }
    public void tick() { flash.tickChaser(); }
    public ItemStack getItem() { return item.copy(); }
    public PonderTag getTag() { return tag; }
    public void activate() { if (active && visible) action.run(); }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { activate(); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!active || !visible) return false;
        if (shortcut != null && shortcut.matches(event) || isFocused() && (event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
            playDownSound(Minecraft.getInstance().getSoundManager()); activate(); return true;
        }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput narration) { defaultButtonNarrationText(narration); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        int border = active ? isHoveredOrFocused() ? 0xF0C0C0FF : 0x60C0C0FF : 0x80909090;
        int bottom = active ? isHoveredOrFocused() ? 0xA0C0C0FF : 0x30C0C0FF : 0x20909090;
        if (missingBorderTop != null) { border = missingBorderTop; bottom = missingBorderBottom; }
        float flashValue = flash.getValue(partial);
        if (flashValue > .1f) {
            float sine = (.5f + .5f * (float) Math.sin((AnimationTickHolder.getTicks(true) + partial) / 10f)) * flashValue;
            int first = Math.clamp((border >>> 24) + 150, 0, 255) << 24 | 0xFFFFFF;
            int second = Math.clamp((bottom >>> 24) + 150, 0, 255) << 24 | 0x9B9B9B;
            border = PonderUIRenderHelper.mix(border, first, sine); bottom = PonderUIRenderHelper.mix(bottom, second, sine);
        }
        if (alpha < .1f) return;
        border = PonderUIRenderHelper.alpha(border, alpha); bottom = PonderUIRenderHelper.alpha(bottom, alpha);
        gui.fillGradient(getX(), getY(), getRight(), getBottom(), border, bottom);
        gui.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, PonderUIRenderHelper.alpha(0xE0000000, alpha));
        if (icon != null) icon.render(gui, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
        else if (!item.isEmpty()) gui.item(item, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
        else gui.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, PonderUIRenderHelper.alpha(active ? 0xFFFFEEDD : 0xFF909090, alpha));
        if (shortcut != null) gui.centeredText(Minecraft.getInstance().font,
            shortcut.getTranslatedKeyMessage().getString().toLowerCase(Locale.ROOT),
            getX() + width / 2 + 8, getY() + height - 6, PonderUIRenderHelper.alpha(0xFFA3A3A3, alpha));
        if (!item.isEmpty() && isMouseOver(mouseX, mouseY))
            gui.setTooltipForNextFrame(Minecraft.getInstance().font, item, mouseX, mouseY);
    }
}
