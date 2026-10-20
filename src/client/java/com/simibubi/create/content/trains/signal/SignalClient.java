package com.simibubi.create.content.trains.signal;

import com.simibubi.create.AllBlockEntityTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class SignalClient {
    public static void register() {
        SignalGroupClient.register();
        com.simibubi.create.content.trains.graph.SignalGroupVisualizer.register();
        SignalModels.register(); BlockEntityRenderers.register(AllBlockEntityTypes.TRACK_SIGNAL, SignalRenderer::new);
        MenuScreens.register(SignalMenu.TYPE, SignalScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(SignalSnapshotPacket.TYPE, (packet, context) -> {
            var player = context.client().player;
            if (player != null && player.containerMenu instanceof SignalMenu menu && menu.containerId == packet.container()
                && menu.opening().pos().equals(packet.opening().pos())) menu.updateSnapshot(packet.opening());
        });
    }
    private SignalClient() {}
}
