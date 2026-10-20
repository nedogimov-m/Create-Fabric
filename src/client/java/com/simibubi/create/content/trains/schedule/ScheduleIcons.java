package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.Create;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Actual source atlas coordinates, not replacement text icons. */
public enum ScheduleIcons {
    I_TRASH(1, 0), I_PRIORITY_LOW(5, 0), I_PRIORITY_VERY_HIGH(7, 0), I_CONFIRM(0, 1), I_REFRESH(3, 1);
    private final int x, y;
    ScheduleIcons(int x, int y) { this.x = x * 16; this.y = y * 16; }
    public void render(GuiGraphicsExtractor gui, int x, int y) {
        gui.blit(RenderPipelines.GUI_TEXTURED, Create.asResource("textures/gui/icons.png"), x, y, this.x, this.y, 16, 16, 256, 256);
    }
}
