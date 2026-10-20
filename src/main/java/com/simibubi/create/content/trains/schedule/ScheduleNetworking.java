package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.content.trains.display.GlobalTrainDisplayData;
import com.simibubi.create.foundation.recipe.ItemCopyingRecipe;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ScheduleNetworking {
    private ScheduleNetworking() {}
    public static void register() {
        ScheduleMenu.register();
        ItemCopyingRecipe.register();
        PayloadTypeRegistry.serverboundPlay().register(ScheduleEditPacket.TYPE, ScheduleEditPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScheduleEditorPacket.TYPE, ScheduleEditorPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ScheduleEditPacket.TYPE, (packet, context) -> packet.handle(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ScheduleEditorPacket.TYPE, (packet, context) -> packet.handle(context.player()));
        ScheduleItemEntityInteraction.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(GlobalTrainDisplayData::unload);
    }
}
