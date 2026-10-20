package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Actual native menu-bound mode change, exact owner and configuration revision. */
public record SignalConfigurationPacket(int container, BlockPos pos, long revision, SignalBlock.SignalType mode) implements CustomPacketPayload {
    public static final Type<SignalConfigurationPacket> TYPE = new Type<>(Create.asResource("configure_track_signal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SignalConfigurationPacket> CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeContainerId(value.container); buffer.writeBlockPos(value.pos); buffer.writeVarLong(value.revision); buffer.writeEnum(value.mode);
    }, buffer -> new SignalConfigurationPacket(buffer.readContainerId(), buffer.readBlockPos(), buffer.readVarLong(), buffer.readEnum(SignalBlock.SignalType.class)));
    public SignalConfigurationPacket { pos = pos.immutable(); }
    @Override public Type<SignalConfigurationPacket> type() { return TYPE; }
    public boolean apply(ServerPlayer player) {
        if (!(player.containerMenu instanceof SignalMenu menu) || menu.containerId != container
            || !menu.opening().pos().equals(pos) || !menu.stillValid(player) || menu.owner() == null) return false;
        return menu.owner().configure(player, mode, revision);
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> packet.apply(context.player()));
    }
}
