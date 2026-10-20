package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SuperGlueSelectionPacket(BlockPos from,BlockPos to) implements CustomPacketPayload {
    public static final Type<SuperGlueSelectionPacket> TYPE=new Type<>(Create.asResource("glue_in_area"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SuperGlueSelectionPacket> STREAM_CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,SuperGlueSelectionPacket::from,BlockPos.STREAM_CODEC,SuperGlueSelectionPacket::to,SuperGlueSelectionPacket::new);
    @Override public Type<SuperGlueSelectionPacket> type(){return TYPE;}
}
