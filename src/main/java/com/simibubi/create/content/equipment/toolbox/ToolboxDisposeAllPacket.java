package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ToolboxDisposeAllPacket(BlockPos toolboxPos,UUID owner) implements CustomPacketPayload {
    public static final Type<ToolboxDisposeAllPacket> TYPE=new Type<>(Create.asResource("toolbox_dispose_all"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ToolboxDisposeAllPacket> STREAM_CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,ToolboxDisposeAllPacket::toolboxPos,UUIDUtil.STREAM_CODEC,ToolboxDisposeAllPacket::owner,ToolboxDisposeAllPacket::new);
    @Override public Type<ToolboxDisposeAllPacket> type(){return TYPE;}
    public void handle(ServerPlayer player){ToolboxHandler.disposeAll(player,toolboxPos,owner);}
}
