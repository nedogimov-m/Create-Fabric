package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SignalSnapshotPacket(int container, SignalMenu.Opening opening) implements CustomPacketPayload {
    public static final Type<SignalSnapshotPacket> TYPE = new Type<>(Create.asResource("track_signal_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SignalSnapshotPacket> CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeContainerId(value.container); SignalMenu.Opening.CODEC.encode(buffer, value.opening);
    }, buffer -> new SignalSnapshotPacket(buffer.readContainerId(), SignalMenu.Opening.CODEC.decode(buffer)));
    @Override public Type<SignalSnapshotPacket> type() { return TYPE; }
    public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
}
