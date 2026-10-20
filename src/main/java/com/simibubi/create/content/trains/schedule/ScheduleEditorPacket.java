package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.Create;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Source editor/ghost synchronization; these samples never enter any physical inventory. */
public record ScheduleEditorPacket(int menu, boolean active, int slots, ItemStack first, ItemStack second) implements CustomPacketPayload {
    public static final Type<ScheduleEditorPacket> TYPE = new Type<>(Create.asResource("schedule_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleEditorPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScheduleEditorPacket::menu, ByteBufCodecs.BOOL, ScheduleEditorPacket::active,
            ByteBufCodecs.VAR_INT, ScheduleEditorPacket::slots, ItemStack.OPTIONAL_STREAM_CODEC, ScheduleEditorPacket::first,
            ItemStack.OPTIONAL_STREAM_CODEC, ScheduleEditorPacket::second, ScheduleEditorPacket::new);
    @Override public Type<ScheduleEditorPacket> type() { return TYPE; }
    public void handle(ServerPlayer player) {
        if (slots < 0 || slots > 2 || !(player.containerMenu instanceof ScheduleMenu current)
                || current.containerId != menu || !current.stillValid(player)) return;
        current.slotsActive = active;
        current.targetSlotsActive = slots;
        current.ghostInventory.setItem(0, first.copyWithCount(1));
        current.ghostInventory.setItem(1, second.copyWithCount(1));
        current.broadcastChanges();
    }
}
