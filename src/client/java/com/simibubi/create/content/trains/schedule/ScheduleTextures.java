package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.Create;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Exact source schedule/editor texture segments extracted through native GUI pipelines. */
public enum ScheduleTextures {
    PLAYER_INVENTORY("player_inventory", 176, 108),
    SCHEDULE("schedule", 256, 226),
    SCHEDULE_CARD_DARK("schedule", 5, 233, 1, 1),
    SCHEDULE_CARD_MEDIUM("schedule", 6, 233, 1, 1),
    SCHEDULE_CARD_LIGHT("schedule", 7, 233, 1, 1),
    SCHEDULE_CARD_MOVE_UP("schedule", 51, 230, 12, 12),
    SCHEDULE_CARD_MOVE_DOWN("schedule", 65, 230, 12, 12),
    SCHEDULE_CARD_REMOVE("schedule", 51, 243, 12, 12),
    SCHEDULE_CARD_DUPLICATE("schedule", 65, 243, 12, 12),
    SCHEDULE_CARD_NEW("schedule", 79, 239, 16, 16),
    SCHEDULE_CONDITION_NEW("schedule", 96, 239, 19, 16),
    SCHEDULE_CONDITION_LEFT("schedule", 116, 239, 6, 16),
    SCHEDULE_CONDITION_LEFT_CLEAN("schedule", 147, 239, 2, 16),
    SCHEDULE_CONDITION_MIDDLE("schedule", 123, 239, 1, 16),
    SCHEDULE_CONDITION_ITEM("schedule", 125, 239, 18, 16),
    SCHEDULE_CONDITION_RIGHT("schedule", 144, 239, 2, 16),
    SCHEDULE_CONDITION_APPEND("schedule", 150, 245, 10, 10),
    SCHEDULE_SCROLL_LEFT("schedule", 161, 247, 4, 8),
    SCHEDULE_SCROLL_RIGHT("schedule", 166, 247, 4, 8),
    SCHEDULE_STRIP_DARK("schedule", 5, 235, 3, 1),
    SCHEDULE_STRIP_LIGHT("schedule", 5, 237, 3, 1),
    SCHEDULE_STRIP_WAIT("schedule", 1, 239, 11, 16),
    SCHEDULE_STRIP_TRAVEL("schedule", 12, 239, 11, 16),
    SCHEDULE_STRIP_DOTTED("schedule", 23, 239, 11, 16),
    SCHEDULE_STRIP_END("schedule", 34, 239, 11, 16),
    SCHEDULE_STRIP_ACTION("schedule", 209, 239, 11, 16),
    SCHEDULE_EDITOR("schedule_2", 256, 89),
    SCHEDULE_EDITOR_ADDITIONAL_SLOT("schedule_2", 55, 47, 32, 18),
    SCHEDULE_EDITOR_INACTIVE_SLOT("schedule_2", 0, 91, 18, 18),
    SCHEDULE_POINTER("schedule", 185, 239, 21, 16),
    SCHEDULE_POINTER_OFFSCREEN("schedule", 171, 239, 13, 16),
    BUTTON("widgets", 18, 18),
    BUTTON_HOVER("widgets", 18, 0, 18, 18),
    BUTTON_DOWN("widgets", 36, 0, 18, 18),
    BUTTON_GREEN("widgets", 72, 0, 18, 18),
    BUTTON_DISABLED("widgets", 90, 0, 18, 18),
    INDICATOR("widgets", 0, 18, 18, 6),
    INDICATOR_WHITE("widgets", 18, 18, 18, 6),
    INDICATOR_GREEN("widgets", 36, 18, 18, 6),
    INDICATOR_YELLOW("widgets", 54, 18, 18, 6),
    INDICATOR_RED("widgets", 72, 18, 18, 6);
    public final Identifier location;
    private final int width, height, startX, startY;
    ScheduleTextures(String path, int width, int height) { this(path, 0, 0, width, height); }
    ScheduleTextures(String path, int startX, int startY, int width, int height) {
        location = Create.asResource("textures/gui/" + path + ".png");
        this.startX = startX; this.startY = startY; this.width = width; this.height = height;
    }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void render(GuiGraphicsExtractor gui, int x, int y) {
        gui.blit(RenderPipelines.GUI_TEXTURED, location, x, y, startX, startY, width, height, 256, 256);
    }
    public void stretched(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.blit(RenderPipelines.GUI_TEXTURED, location, x, y, startX, startY, width, height, this.width, this.height, 256, 256);
    }
}
