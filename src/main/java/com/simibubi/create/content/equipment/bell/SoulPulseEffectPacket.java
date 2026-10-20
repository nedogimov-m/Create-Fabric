package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SoulPulseEffectPacket(BlockPos pos,int distance,boolean canOverlap) implements CustomPacketPayload {
    public static final Type<SoulPulseEffectPacket> TYPE=new Type<>(Create.asResource("soul_pulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SoulPulseEffectPacket> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,SoulPulseEffectPacket::pos,ByteBufCodecs.INT,SoulPulseEffectPacket::distance,ByteBufCodecs.BOOL,SoulPulseEffectPacket::canOverlap,SoulPulseEffectPacket::new);
    public SoulPulseEffectPacket { if(distance<1||distance>SoulPulseEffect.MAX_DISTANCE)throw new IllegalArgumentException("Invalid soul pulse distance");pos=pos.immutable(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
