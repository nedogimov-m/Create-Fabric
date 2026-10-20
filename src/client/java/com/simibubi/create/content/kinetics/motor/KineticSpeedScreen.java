package com.simibubi.create.content.kinetics.motor;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.foundation.gui.widget.ScheduleEditBox;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

/** A bounded native value board with exact integer input throughout the configured RPM range. */
public final class KineticSpeedScreen extends Screen {
    private final BlockPos pos;
    private final com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour sourceSettings;
    private com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard sourceBoard;
    private final net.minecraft.core.Direction sourceSide;
    private int left, top, panelWidth, barLeft, barWidth, row;
    private long column;
    private boolean shift, manualMode, syncingInput;
    private ScheduleEditBox exactInput;
    private Button apply;

    public KineticSpeedScreen(BlockPos pos, int value) {
        super(titleFor(pos));
        this.pos = pos.immutable();
        var client=net.minecraft.client.Minecraft.getInstance();
        var hit=client.hitResult instanceof net.minecraft.world.phys.BlockHitResult actual && actual.getBlockPos().equals(pos)?actual:null;
        sourceSide=hit==null?net.minecraft.core.Direction.UP:hit.getDirection();
        sourceSettings=client.level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor?motor.generatedSpeed:null;
        sourceBoard=sourceSettings==null?null:sourceSettings.createBoard(client.player,hit);
        row = value < 0 ? 0 : 1;
        // Widen before abs: even a corrupt MIN_VALUE must not wrap into a valid positive selection.
        column = Math.max(1L, Math.abs((long) value));
    }

    private static Component titleFor(BlockPos pos){
        var client=net.minecraft.client.Minecraft.getInstance();
        return client.level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor?motor.generatedSpeed.label
            :GoggleTooltip.translate("kinetics.speed_controller.rotation_speed");
    }
    private int limit(){return sourceBoard==null?maximum():sourceBoard.maxValue();}
    private void preview(){if(sourceSettings!=null)sourceSettings.newSettingHovered(new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings(row,(int)Math.min(column,Integer.MAX_VALUE)));}

    public BlockPos position() { return pos; }
    private static int maximum() { return AllConfigs.server().kinetics.maxRotationSpeed.get(); }

    @Override protected void init() {
        panelWidth = Math.max(120, Math.min(320, width - 24));
        left = (width - panelWidth) / 2;
        top = (height - 130) / 2;
        barLeft = left + 28;
        barWidth = panelWidth - 40;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> stepManually(-1))
            .bounds(left + 8, top + 72, 22, 20).build());
        exactInput = new ScheduleEditBox(font, left + 34, top + 72, panelWidth - 116, 20,
            GoggleTooltip.translate("gui.kinetic_speed.exact"));
        exactInput.setMaxLength(10);
        exactInput.setFilter(value -> value.length() <= 10 && value.chars().allMatch(c -> c >= '0' && c <= '9'));
        exactInput.setValue(Long.toString(column));
        exactInput.setResponder(this::inputChanged);
        addRenderableWidget(exactInput);
        addRenderableWidget(Button.builder(Component.literal("+"), button -> stepManually(1))
            .bounds(left + panelWidth - 78, top + 72, 22, 20).build());
        apply = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> confirm())
            .bounds(left + panelWidth - 52, top + 72, 44, 20).build());
        updateInputValidity();
        // Do not warp and read a rounded cursor coordinate back into an exact integer value.
    }

    @Override protected void setInitialFocus() {}

    private void inputChanged(String value) {
        if (syncingInput) return;
        manualMode = true;
        var parsed = parseMagnitude(value, limit());
        if (parsed.isPresent()) { column = parsed.getAsInt(); preview(); }
        updateInputValidity();
    }

    static OptionalInt parseMagnitude(String value, int maximum) {
        if (value.isEmpty() || value.length() > 10) return OptionalInt.empty();
        for (int i = 0; i < value.length(); i++)
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return OptionalInt.empty();
        long parsed = Long.parseLong(value);
        return parsed >= 1 && parsed <= maximum ? OptionalInt.of((int) parsed) : OptionalInt.empty();
    }

    private void updateInputValidity() {
        if (exactInput == null || apply == null) return;
        boolean valid = parseMagnitude(exactInput.getValue(), limit()).isPresent();
        apply.active = valid;
        exactInput.setTextColor(valid ? 0xFFE0E0E0 : 0xFFFF5555);
    }

    private void syncInput() {
        if (exactInput == null) return;
        syncingInput = true;
        try { exactInput.setValue(Long.toString(column)); }
        finally { syncingInput = false; }
        updateInputValidity();preview();
    }

    static int steppedMagnitude(long value, int direction, boolean coarse, int maximum) {
        return (int) Math.clamp(value + (long) Integer.signum(direction) * (coarse ? 32 : 1), 1L, (long) maximum);
    }

    private void stepManually(int direction) {
        manualMode = true;
        step(direction, minecraft.hasShiftDown());
    }

    private void step(int direction, boolean coarse) {
        column = steppedMagnitude(column, direction, coarse, limit());
        syncInput();
    }

    static int magnitudeAt(double fraction, boolean milestones, int maximum) {
        fraction = Double.isNaN(fraction) ? 0 : Math.clamp(fraction, 0, 1);
        long value = 1 + Math.round(fraction * (maximum - 1L));
        if (milestones && value != maximum) value = Math.round(value / 32d) * 32L;
        return (int) Math.clamp(value, 1L, (long) maximum);
    }

    public Vec2 getCoordinateOfValue(int row, int column) {
        double fraction = (Math.clamp((long) column, 1L, (long) limit()) - 1d) / Math.max(1d,limit() - 1d);
        return new Vec2((float) (barLeft + fraction * barWidth), top + 29 + row * 12);
    }

    public int closestValue(double x, double y, boolean milestones) {
        row = y <= top + 35 ? 0 : 1;
        column = magnitudeAt((x - barLeft) / barWidth, milestones, limit());
        syncInput();
        return row == 0 ? -(int) column : (int) column;
    }

    private boolean overBoard(double x, double y) {
        return x >= left + 8 && x <= left + panelWidth - 8 && y >= top + 23 && y < top + 47;
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null
            || !KineticSpeedSettings.supports(minecraft.level.getBlockState(pos))) {
            onClose();
            return;
        }
        if(sourceSettings!=null){
            if(!(minecraft.level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor)||motor.generatedSpeed!=sourceSettings||motor.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE)!=sourceSettings){onClose();return;}
            sourceBoard=sourceSettings.createBoard(minecraft.player,null);
        }
        updateInputValidity();
    }

    @Override public void mouseMoved(double x, double y) {
        if (!manualMode && overBoard(x, y)) closestValue(x, y, minecraft.hasShiftDown());
        super.mouseMoved(x, y);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overBoard(event.x(), event.y())) {
            manualMode = true;
            setFocused(null);
            if (event.x() < barLeft) {row = event.y() <= top + 35 ? 0 : 1;preview();}
            else closestValue(event.x(), event.y(), minecraft.hasShiftDown());
            return true;
        }
        if (exactInput.isMouseOver(event.x(), event.y())) manualMode = true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (!manualMode && minecraft.options.keyUse.matchesMouse(event)) {
            confirm();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        shift = event.hasShiftDown();
        if (!manualMode && minecraft.options.keyUse.matches(event)) {
            confirm();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        shift = event.hasShiftDown();
        if (event.isEscape()) return super.keyPressed(event);
        if (event.isConfirmation()) { confirm(); return true; }
        if (event.isUp() || event.isDown()) {
            row = event.isUp() ? 0 : 1;
            preview();return true;
        }
        if (!exactInput.isFocused() && (event.isLeft() || event.isRight())) {
            step(event.isRight() ? 1 : -1, shift);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        step((int) Math.signum(vertical), minecraft.hasShiftDown());
        return true;
    }

    public void saveAndClose(double x, double y) { confirm(); }

    private void confirm() {
        // Validate the current exact text against the latest synced limit, without resampling pixels.
        var value = parseMagnitude(exactInput.getValue(), limit());
        if (value.isEmpty()) {
            manualMode = true;
            setFocused(exactInput);
            updateInputValidity();
            return;
        }
        int magnitude = value.getAsInt();
        if(sourceSettings!=null)ClientPlayNetworking.send(new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket(pos,row,magnitude,null,null,sourceSide,minecraft.hasControlDown(),sourceSettings.netId()));
        else ClientPlayNetworking.send(new KineticSpeedSettings.SetSpeed(pos, row == 0 ? -magnitude : magnitude));
        onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        graphics.fillGradient(0, 0, width, height, 0x50101010, 0x50101010);
        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + 132, 0xFF442000);
        graphics.fill(left, top, left + panelWidth, top + 130, 0xFFE0C39B);
        graphics.centeredText(font, title, left + panelWidth / 2, top + 7, 0xFF442000);
        for (int r = 0; r < 2; r++) {
            int y = top + 24 + r * 12;
            graphics.fill(left + 8, y, left + panelWidth - 8, y + 10, r == row ? 0xFFFBDC7D : 0xFFBA986D);
            graphics.text(font, sourceBoard==null?Component.literal(r==0?"\u27f3":"\u27f2"):sourceBoard.rows().get(r), left + 12, y + 1, 0xFF442000, false);
            // Nine screen-space ticks, regardless of whether the limit is 64 or 2,147,483,647.
            for (int tick = 0; tick <= 8; tick++) {
                int x = barLeft + (int) ((long) barWidth * tick / 8);
                graphics.fill(x, y + 2, x + 1, y + 8, 0xFF795330);
            }
        }
        int cursor = (int) getCoordinateOfValue(row, (int) Math.min(column, Integer.MAX_VALUE)).x;
        graphics.fill(cursor - 1, top + 23 + row * 12, cursor + 2, top + 35 + row * 12, 0xFF442000);
        graphics.text(font, "1", barLeft, top + 50, 0xFF442000, false);
        String maximumText = Integer.toString(limit());
        graphics.text(font, maximumText, barLeft + barWidth - font.width(maximumText), top + 50, 0xFF442000, false);
        graphics.text(font, GoggleTooltip.translate("gui.kinetic_speed.exact"), left + 8, top + 61, 0xFF442000, false);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        var hint = manualMode ? GoggleTooltip.translate("gui.kinetic_speed.edit_hint")
            : GoggleTooltip.translate("gui.value_settings.release_to_confirm", Component.keybind("key.use"));
        graphics.centeredText(font, hint, left + panelWidth / 2, top + 99, 0xFF442000);
        graphics.centeredText(font, GoggleTooltip.translate("gui.kinetic_speed.steps"), left + panelWidth / 2, top + 113, 0xFF442000);
        if (!apply.active && exactInput.isMouseOver(mouseX, mouseY))
            graphics.setTooltipForNextFrame(font, GoggleTooltip.translate("gui.kinetic_speed.invalid", maximumText), mouseX, mouseY);
    }
}
