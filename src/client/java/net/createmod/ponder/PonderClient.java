// Ponder1.0.82 client frontend services, MIT; native26.3 Fabric registration.
package net.createmod.ponder;

import net.createmod.ponder.enums.PonderKeybinds;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public final class PonderClient implements ClientModInitializer {
    private static boolean registered;
    @Override public void onInitializeClient() { init(); }
    public static synchronized void init() {
        if (registered) return;
        registered = true;
        Ponder.registerEngineFoundation();
        PonderViewport.register();
        PonderKeybinds.register(KeyMappingHelper::registerKeyMapping);
        ItemTooltipCallback.EVENT.register((stack, context, flags, tooltip) -> PonderTooltipHandler.addToTooltip(tooltip, stack));
        ClientTickEvents.END_CLIENT_TICK.register(client -> PonderTooltipHandler.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PonderTooltipHandler.reset();
            net.createmod.catnip.gui.ScreenOpener.clearStack();
            if (client.gui.screen() instanceof AbstractPonderScreen) client.gui.setScreen(null);
        });
    }
    public static boolean isGameActive() { var client = Minecraft.getInstance(); return client.level != null && client.player != null; }
}
