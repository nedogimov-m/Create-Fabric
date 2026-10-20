package com.simibubi.create.content.contraptions.actors.roller;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;

public final class RollerSettingsClient {
    private static BlockPos held;
    private static int ticks;
    public static void register() {
        RollerSettings.CLIENT_START = pos -> { if (held == null) { held = pos; ticks = 0; } };
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (held == null) return;
            if (!targeted(client, held) || !client.options.keyUse.isDown() || client.gui.screen() != null) { held = null; return; }
            if (ticks++ < 5) return;
            var pos = held; held = null;
            if (client.level.getBlockEntity(pos) instanceof RollerBlockEntity roller) client.gui.setScreen(new ModeScreen(pos, roller.getMode().ordinal()));
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Create.asResource("roller_mode"), (graphics, delta) -> {
            var client = Minecraft.getInstance();
            if (client.gui.hud.isHidden() || client.gui.screen() != null || !(client.hitResult instanceof BlockHitResult hit) || !targeted(client, hit.getBlockPos())) return;
            var roller = (RollerBlockEntity) client.level.getBlockEntity(hit.getBlockPos());
            graphics.centeredText(client.font, Component.translatable("create.contraptions.roller_mode").append(": ").append(roller.getMode().label()), graphics.guiWidth() / 2, graphics.guiHeight() - 88, 0xFFFBDC7D);
            graphics.centeredText(client.font, Component.translatable("create.gui.value_settings.hold_to_edit"), graphics.guiWidth() / 2, graphics.guiHeight() - 76, 0xFFFFFFFF);
        });
    }
    private static boolean targeted(Minecraft client, BlockPos pos) {
        return client.player != null && client.level != null && client.player.mayBuild() && !client.player.isSpectator()
            && client.level.getBlockEntity(pos) instanceof RollerBlockEntity && client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(pos) && RollerBlock.testPanelHit(client.level.getBlockState(pos), pos, hit, false);
    }
    public static final class ModeScreen extends Screen {
        private final BlockPos pos;
        private int selected, left, top;
        public ModeScreen(BlockPos pos, int selected) { super(Component.translatable("create.contraptions.roller_mode")); this.pos = pos; this.selected = Math.clamp(selected, 0, 2); }
        public BlockPos position() { return pos; }
        public int selection() { return selected; }
        public Vec2 coordinate(int option) { return new Vec2(left + 12 + 24 * option, top + 12); }
        @Override protected void init() { left = width / 2 - 36; top = height / 2 - 12; warp(); }
        private void warp() {
            var p = coordinate(selected); var window = minecraft.getWindow(); double scaleX=(double)window.getScreenWidth()/window.getGuiScaledWidth();double scaleY=(double)window.getScreenHeight()/window.getGuiScaledHeight();
            InputConstants.releaseMouse(window, p.x*scaleX, p.y*scaleY); minecraft.mouseHandler.onMove(window.handle(), p.x*scaleX, p.y*scaleY, 0, 0);
        }
        @Override public void tick() { if (minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof RollerBlockEntity)) onClose(); }
        @Override public void mouseMoved(double x, double y) { selected = Math.clamp((int) ((x - left) / 24), 0, 2); }
        @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { selected = Math.clamp(selected + (int) Math.signum(dy), 0, 2); warp(); return true; }
        @Override public boolean mouseReleased(MouseButtonEvent event) { if (minecraft.options.keyUse.matchesMouse(event)) { save(); return true; } return super.mouseReleased(event); }
        @Override public boolean keyReleased(KeyEvent event) { if (minecraft.options.keyUse.matches(event)) { save(); return true; } return super.keyReleased(event); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (event.isEscape()) return super.keyPressed(event); if (event.isConfirmation()) { save(); return true; }
            if (event.isLeft() || event.isRight()) { selected = Math.clamp(selected + (event.isRight() ? 1 : -1), 0, 2); warp(); return true; } return super.keyPressed(event);
        }
        public void save() { ClientPlayNetworking.send(new RollerSettings.SetMode(pos, selected)); onClose(); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
            graphics.fillGradient(0, 0, width, height, 0x50101010, 0x50101010);
            int panelWidth = Math.max(180, Math.max(font.width(title), font.width(RollerBlockEntity.RollingMode.values()[selected].label())) + 20);
            graphics.fill(width / 2 - panelWidth / 2, top - 22, width / 2 + panelWidth / 2, top + 56, 0xEE302D36);
            graphics.centeredText(font, title, width / 2, top - 16, 0xFFEDEDED);
            for (int option = 0; option < 3; option++) {
                var p = coordinate(option); int x = (int) p.x - 11, y = (int) p.y - 11;
                graphics.fill(x, y, x + 22, y + 22, selected == option ? 0xFFDDB96D : 0xFF5B5363);
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, Create.asResource("textures/gui/icons.png"), x + 3, y + 3, 144 + option * 16, 64, 16, 16, 256, 256);
            }
            graphics.centeredText(font, RollerBlockEntity.RollingMode.values()[selected].label(), width / 2, top + 28, 0xFFFBDC7D);
            graphics.centeredText(font, Component.translatable("create.gui.value_settings.release_to_confirm", Component.keybind("key.use")), width / 2, top + 42, 0xFFEDEDED);
        }
    }
    private RollerSettingsClient() {}
}
