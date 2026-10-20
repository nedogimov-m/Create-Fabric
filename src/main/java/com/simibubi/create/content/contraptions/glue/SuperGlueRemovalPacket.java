package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SuperGlueRemovalPacket(int entityId,BlockPos soundSource) implements CustomPacketPayload {
    public static final Type<SuperGlueRemovalPacket> TYPE=new Type<>(Create.asResource("glue_removed"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SuperGlueRemovalPacket> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.INT,SuperGlueRemovalPacket::entityId,BlockPos.STREAM_CODEC,SuperGlueRemovalPacket::soundSource,SuperGlueRemovalPacket::new);
    @Override public Type<SuperGlueRemovalPacket> type(){return TYPE;}
}
