package com.simibubi.create.foundation.gui.widget;

import com.simibubi.create.content.trains.schedule.ScheduleTextures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class Indicator extends ScheduleWidget {
    public enum State { OFF, ON, RED, YELLOW, GREEN }
    public State state = State.OFF;
    public Indicator(int x, int y, Component tooltip) {
        super(x, y, ScheduleTextures.INDICATOR.getWidth(), ScheduleTextures.INDICATOR.getHeight());
        if (!tooltip.getString().isEmpty()) toolTip.add(tooltip);
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        var segment = switch (state) {
            case ON -> ScheduleTextures.INDICATOR_WHITE;
            case RED -> ScheduleTextures.INDICATOR_RED;
            case YELLOW -> ScheduleTextures.INDICATOR_YELLOW;
            case GREEN -> ScheduleTextures.INDICATOR_GREEN;
            default -> ScheduleTextures.INDICATOR;
        };
        segment.render(gui, getX(), getY());
        super.extractWidgetRenderState(gui, mouseX, mouseY, partial);
    }
}
