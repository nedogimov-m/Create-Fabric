package com.simibubi.create.content.redstone.thresholdSwitch;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ThresholdSwitchSnapshotPacket(int containerId,ThresholdSwitchMenu.Opening opening)implements CustomPacketPayload {
    public static final Type<ThresholdSwitchSnapshotPacket> TYPE=new Type<>(Create.asResource("threshold_switch_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ThresholdSwitchSnapshotPacket> CODEC=StreamCodec.of((buffer,value)->{buffer.writeContainerId(value.containerId);ThresholdSwitchMenu.Opening.CODEC.encode(buffer,value.opening);},buffer->new ThresholdSwitchSnapshotPacket(buffer.readContainerId(),ThresholdSwitchMenu.Opening.CODEC.decode(buffer)));
    @Override public Type<ThresholdSwitchSnapshotPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
