package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Source target parameters bound to an actual paid placement's one-use server request. */
public record EjectorPlacementPacket(int h,int v,BlockPos pos,Direction facing,long nonce) implements CustomPacketPayload {
    public static final Type<EjectorPlacementPacket> TYPE=new Type<>(Create.asResource("place_ejector"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EjectorPlacementPacket> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeInt(p.h);b.writeInt(p.v);b.writeBlockPos(p.pos);b.writeEnum(p.facing);b.writeLong(p.nonce);
    },b->new EjectorPlacementPacket(b.readInt(),b.readInt(),b.readBlockPos(),b.readEnum(Direction.class),b.readLong()));
    public EjectorPlacementPacket{pos=pos.immutable();java.util.Objects.requireNonNull(facing);}
    @Override public Type<EjectorPlacementPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){var owner=EjectorNetworking.interactable(player,pos);return owner!=null&&owner.acceptPlacement(player,nonce,h,v,facing);}
    public record ClientBoundRequest(BlockPos pos,long nonce) implements CustomPacketPayload {
        public static final Type<ClientBoundRequest> TYPE=new Type<>(Create.asResource("s_place_ejector"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ClientBoundRequest> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeLong(p.nonce);},b->new ClientBoundRequest(b.readBlockPos(),b.readLong()));
        public ClientBoundRequest{pos=pos.immutable();}
        @Override public Type<ClientBoundRequest> type(){return TYPE;}
    }
}
