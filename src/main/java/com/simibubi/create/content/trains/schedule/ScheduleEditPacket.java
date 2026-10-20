package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.Create;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Actual source schedule edit, bound to its real native menu and selected item identity. */
public record ScheduleEditPacket(int menu, Schedule schedule) implements CustomPacketPayload {
    public static final Type<ScheduleEditPacket> TYPE = new Type<>(Create.asResource("configure_schedule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleEditPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScheduleEditPacket::menu, Schedule.STREAM_CODEC, ScheduleEditPacket::schedule, ScheduleEditPacket::new);
    @Override public Type<ScheduleEditPacket> type() { return TYPE; }
    public void handle(ServerPlayer sender) {
        if (!(sender.containerMenu instanceof ScheduleMenu current) || current.containerId != menu || !current.stillValid(sender)) return;
        var item = current.contentHolder();
        if (schedule.entries.isEmpty()) item.remove(AllDataComponents.TRAIN_SCHEDULE);
        else {
            schedule.savedProgress = Math.clamp(schedule.savedProgress, 0, schedule.entries.size() - 1);
            item.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(sender.registryAccess()));
        }
        sender.getInventory().setChanged();
        sender.getCooldowns().addCooldown(item, 5);
        current.broadcastChanges();
    }
}
