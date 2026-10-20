package com.simibubi.create.infrastructure.gui;

import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Source menu-row matching and offsets, using the actual Fabric native widget list. */
public final class OpenCreateMenuButton extends Button {
    private static boolean registered;

    public OpenCreateMenuButton(int x, int y) {
        super(x, y, 20, 20, Component.translatable("create.menu.configure"),
            OpenCreateMenuButton::click, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.translatable("create.menu.configure")));
    }

    @Override protected void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.blit(RenderPipelines.GUI_TEXTURED, Create.asResource("textures/item/goggles.png"),
            getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
    }

    public static void click(Button button) {
        var client = Minecraft.getInstance();
        client.gui.setScreen(new CreateMainMenuScreen(client.gui.screen()));
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> addTo(screen));
    }

    public record SingleMenuRow(String leftTextKey, String rightTextKey) {
        public SingleMenuRow(String centerTextKey) { this(centerTextKey, centerTextKey); }
    }

    public static final class MenuRows {
        public static final MenuRows MAIN_MENU = new MenuRows(List.of(
            new SingleMenuRow("menu.singleplayer"),
            new SingleMenuRow("menu.multiplayer"),
            new SingleMenuRow("fml.menu.mods", "menu.online"),
            new SingleMenuRow("narrator.button.language", "narrator.button.accessibility")));
        public static final MenuRows INGAME_MENU = new MenuRows(List.of(
            new SingleMenuRow("menu.returnToGame"),
            new SingleMenuRow("gui.advancements", "gui.stats"),
            new SingleMenuRow("menu.sendFeedback", "menu.reportBugs"),
            new SingleMenuRow("menu.options", "menu.shareToLan"),
            new SingleMenuRow("menu.returnToMenu")));
        protected final List<String> leftTextKeys, rightTextKeys;
        public MenuRows(List<SingleMenuRow> rows) {
            leftTextKeys = rows.stream().map(SingleMenuRow::leftTextKey).toList();
            rightTextKeys = rows.stream().map(SingleMenuRow::rightTextKey).toList();
        }
    }

    private static void addTo(Screen screen) {
        MenuRows rows;
        int row, offset;
        if (screen instanceof TitleScreen) {
            rows = MenuRows.MAIN_MENU;
            row = AllConfigs.client().mainMenuConfigButtonRow.get();
            offset = AllConfigs.client().mainMenuConfigButtonOffsetX.get();
        } else if (screen instanceof PauseScreen) {
            rows = MenuRows.INGAME_MENU;
            row = AllConfigs.client().ingameMenuConfigButtonRow.get();
            offset = AllConfigs.client().ingameMenuConfigButtonOffsetX.get();
        } else return;
        var widgets = Screens.getWidgets(screen);
        widgets.removeIf(OpenCreateMenuButton.class::isInstance);
        if (row == 0) return;
        boolean onLeft = offset < 0;
        var labels = onLeft ? rows.leftTextKeys : rows.rightTextKeys;
        if (row < 1 || row > labels.size()) return;
        var targetMessage = I18n.get(labels.get(row - 1));
        var target = widgets.stream().filter(widget -> widget.getMessage().getString().equals(targetMessage))
            .findFirst().orElse(null);
        if (target != null)
            widgets.add(new OpenCreateMenuButton(target.getX() + offset + (onLeft ? -20 : target.getWidth()), target.getY()));
    }
}
