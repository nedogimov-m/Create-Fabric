package com.simibubi.create.content.schematics.client;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.ScheduleWidget;
import com.simibubi.create.content.trains.schedule.ScheduleTextures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Native button sharing the exact source icon and widget atlases. */
public final class SchematicIconButton extends ScheduleWidget {
    private AllIcons icon;
    private final Runnable action;
    private boolean pressed;
    public boolean selected;
    public SchematicIconButton(int x, int y, AllIcons icon, Component label, Runnable action) {
        super(x, y, 18, 18);
        this.icon = icon;
        this.action = action;
        toolTip.add(label);
        setMessage(label);
    }
    public void setIcon(AllIcons icon) { this.icon = icon; }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { pressed = true; action.run(); }
    @Override public void onRelease(MouseButtonEvent event) { pressed = false; }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!active || !visible || !event.isConfirmation()) return false;
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        action.run();
        return true;
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        var background = !active ? ScheduleTextures.BUTTON_DISABLED : isHoveredOrFocused()
                ? pressed ? ScheduleTextures.BUTTON_DOWN : ScheduleTextures.BUTTON_HOVER
                : selected ? ScheduleTextures.BUTTON_GREEN : ScheduleTextures.BUTTON;
        background.render(gui, getX(), getY());
        com.simibubi.create.foundation.gui.IconRenderer.render(icon,gui, getX() + 1, getY() + 1);
        super.extractWidgetRenderState(gui, mouseX, mouseY, partial);
    }
}
