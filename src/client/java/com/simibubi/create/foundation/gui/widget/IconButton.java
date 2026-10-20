package com.simibubi.create.foundation.gui.widget;

import com.simibubi.create.content.trains.schedule.ScheduleIcons;
import com.simibubi.create.content.trains.schedule.ScheduleTextures;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Actual source atlas button using native click dispatch, narration and GUI extraction. */
public final class IconButton extends ScheduleWidget {
    private ScheduleIcons icon;
    private Runnable callback;
    private boolean pressed;
    public boolean green;
    public IconButton(int x, int y, ScheduleIcons icon) { super(x, y, 18, 18); this.icon = icon; }
    public IconButton withCallback(Runnable callback) { this.callback = callback; return this; }
    public void setToolTip(Component text) { toolTip.clear(); toolTip.add(text); setMessage(text); }
    public List<Component> getToolTip() { return toolTip; }
    public void setIcon(ScheduleIcons icon) { this.icon = icon; }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { pressed = true; if (callback != null) callback.run(); }
    @Override public void onRelease(MouseButtonEvent event) { pressed = false; }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!active || !visible || !event.isConfirmation()) return false;
        playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        if (callback != null) callback.run();
        return true;
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        ScheduleTextures background = !active ? ScheduleTextures.BUTTON_DISABLED : isHoveredOrFocused()
                ? pressed ? ScheduleTextures.BUTTON_DOWN : ScheduleTextures.BUTTON_HOVER : green ? ScheduleTextures.BUTTON_GREEN : ScheduleTextures.BUTTON;
        background.render(gui, getX(), getY());
        icon.render(gui, getX() + 1, getY() + 1);
        super.extractWidgetRenderState(gui, mouseX, mouseY, partial);
    }
}
