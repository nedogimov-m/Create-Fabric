package com.simibubi.create.content.logistics.redstoneRequester;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RedstoneRequesterEffectPacket(BlockPos pos,boolean success) implements CustomPacketPayload {
    public static final Type<RedstoneRequesterEffectPacket> TYPE=new Type<>(Create.asResource("redstone_requester_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RedstoneRequesterEffectPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeBoolean(packet.success);},buf->new RedstoneRequesterEffectPacket(buf.readBlockPos(),buf.readBoolean()));
    @Override public Type<RedstoneRequesterEffectPacket> type() { return TYPE; }
}
