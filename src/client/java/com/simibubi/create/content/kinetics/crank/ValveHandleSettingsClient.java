package com.simibubi.create.content.kinetics.crank;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class ValveHandleSettingsClient {
    private static BlockPos heldPos;
    private static InteractionHand heldHand;
    private static int heldTicks;
    public static void register() {
        ValveHandleSettings.CLIENT_START = (pos,hand) -> { if (heldPos == null) { heldPos = pos; heldHand = hand; heldTicks = 0; } };
        ClientTickEvents.END_CLIENT_TICK.register(ValveHandleSettingsClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("valve_handle_settings"),(graphics,delta) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || client.level == null || client.gui.screen() != null || client.gui.hud.isHidden()
                    || !client.player.mayBuild() || client.player.isSpectator() || client.player.isShiftKeyDown()
                    || !(client.hitResult instanceof BlockHitResult hit) || !ValveHandleSettings.testHit(client.level.getBlockState(hit.getBlockPos()),hit)
                    || !(client.level.getBlockEntity(hit.getBlockPos()) instanceof ValveHandleBlockEntity valve) || !valve.showValue()) return;
            int y = graphics.guiHeight()-88;
            graphics.centeredText(client.font,GoggleTooltip.number(Math.abs(valve.getAngleInput())).append(GoggleTooltip.translate("generic.unit.degrees")),graphics.guiWidth()/2,y,0xFFFBDC7D);
            graphics.centeredText(client.font,GoggleTooltip.translate("gui.value_settings.hold_to_edit"),graphics.guiWidth()/2,y+12,0xFFFFFFFF);
        });
    }
    private static void tick(Minecraft client) {
        if (heldPos == null) return;
        if (client.player == null || client.level == null || client.gui.screen() != null || !client.player.mayBuild()
                || client.player.isSpectator() || client.player.isShiftKeyDown()
                || !(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(heldPos)
                || !ValveHandleSettings.testHit(client.level.getBlockState(heldPos),hit)
                || !(client.level.getBlockEntity(heldPos) instanceof ValveHandleBlockEntity valve) || !valve.showValue()) { heldPos = null; return; }
        if (!client.options.keyUse.isDown()) {
            ClientPlayNetworking.send(new ValveHandleSettings.Activate(heldPos,heldHand)); heldPos = null; return;
        }
        if (++heldTicks < 5) return;
        var pos = heldPos; heldPos = null; client.gui.setScreen(new ValveHandleScreen(pos,valve.getAngleInput()));
    }
    private ValveHandleSettingsClient() {}
}
