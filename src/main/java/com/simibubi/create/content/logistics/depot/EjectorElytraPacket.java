package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record EjectorElytraPacket(BlockPos pos,long nonce) implements CustomPacketPayload {
    public static final Type<EjectorElytraPacket> TYPE=new Type<>(Create.asResource("ejector_elytra"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EjectorElytraPacket> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeLong(p.nonce);},b->new EjectorElytraPacket(b.readBlockPos(),b.readLong()));
    public EjectorElytraPacket{pos=pos.immutable();}
    @Override public Type<EjectorElytraPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){var owner=EjectorNetworking.live(player,pos);return owner!=null&&owner.acceptElytra(player,nonce);}
}
