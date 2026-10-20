package com.simibubi.create.content.trains.signal;

import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Actual native mode editor; status and target come from the current server snapshot. */
public final class SignalScreen extends AbstractContainerScreen<SignalMenu> {
    private SignalType selected;
    private boolean sent;
    public SignalScreen(SignalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 236, 132); selected = menu.opening().type();
    }
    public SignalType selectedMode() { return selected; }
    @Override protected void init() {
        super.init(); addRenderableWidget(Button.builder(modeName(), button -> {
            selected = selected == SignalType.ENTRY_SIGNAL ? SignalType.CROSS_SIGNAL : SignalType.ENTRY_SIGNAL;
            button.setMessage(modeName());
        }).bounds(leftPos + 16, topPos + 29, 204, 20).build());
        addRenderableWidget(Button.builder(Component.literal("✓"), button -> onClose())
            .bounds(leftPos + 202, topPos + 106, 18, 18).build());
    }
    private Component modeName() { return Component.translatable("create.track_signal.editor.mode." + selected.getSerializedName()); }
    @Override protected void containerTick() {
        super.containerTick(); if (!menu.stillValid(minecraft.player)) { onClose(); return; }
        for (var child : children()) if (child instanceof Button button) button.active = menu.opening().bound();
    }
    private void send() {
        if (sent || !menu.opening().bound() || minecraft.player == null || !menu.stillValid(minecraft.player)) return;
        sent = true; ClientPlayNetworking.send(new SignalConfigurationPacket(menu.containerId, menu.opening().pos(), menu.opening().revision(), selected));
    }
    @Override public void onClose() { send(); super.onClose(); }
    @Override public void removed() { send(); super.removed(); }
    @Override public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        super.extractBackground(gui, mouseX, mouseY, partial);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF343D44);
        gui.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 19, 0xFF24292F);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        gui.text(font, title, 12, 6, 0xFFF4E6CD, false);
        gui.text(font, Component.translatable("create.track_signal.editor.state." + menu.opening().state().name().toLowerCase(java.util.Locale.ROOT)), 16, 61, 0xFFE4DCCB, false);
        if (menu.opening().target() != null) {
            var target = menu.opening().target().pos();
            gui.text(font, Component.literal(target.getX() + ", " + target.getY() + ", " + target.getZ()), 16, 79, 0xFFAAAAB4, false);
        }
        if (!menu.opening().bound()) gui.text(font, Component.translatable("create.track_signal.editor.unbound"), 16, 107, 0xFFEEB477, false);
    }
}
