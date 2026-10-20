package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record EjectorTriggerPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<EjectorTriggerPacket> TYPE=new Type<>(Create.asResource("trigger_ejector"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EjectorTriggerPacket> STREAM_CODEC=StreamCodec.of((b,p)->b.writeBlockPos(p.pos),b->new EjectorTriggerPacket(b.readBlockPos()));
    public EjectorTriggerPacket{pos=pos.immutable();}
    @Override public Type<EjectorTriggerPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){var owner=EjectorNetworking.live(player,pos);if(owner==null||!owner.canPlayerTrigger(player))return false;owner.activate();owner.setChanged();owner.sync();return true;}
}
