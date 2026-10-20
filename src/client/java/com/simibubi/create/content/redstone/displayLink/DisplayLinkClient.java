package com.simibubi.create.content.redstone.displayLink;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** Actual native screen and menu-scoped snapshot network receiver. */
public final class DisplayLinkClient {
    public static void register(){DisplayLinkModel.register();DisplayLinkSelection.register();net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(com.simibubi.create.AllBlockEntityTypes.DISPLAY_LINK,LinkBulbRenderer::new);MenuScreens.register(DisplayLinkMenu.TYPE,DisplayLinkScreen::new);ClientPlayNetworking.registerGlobalReceiver(DisplayLinkSnapshotPacket.TYPE,(packet,context)->{var player=context.client().player;if(player!=null&&player.containerMenu instanceof DisplayLinkMenu menu&&menu.containerId==packet.containerId()&&menu.opening().pos().equals(packet.opening().pos()))menu.updateSnapshot(packet.opening());});}
    private DisplayLinkClient(){}
}
