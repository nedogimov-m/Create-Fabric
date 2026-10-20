package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Reconciles the actual native player's source launch without granting arbitrary velocity authority. */
public record EjectorLaunchPacket(BlockPos pos,long nonce,int h,int v,Direction facing,boolean glider) implements CustomPacketPayload {
    public static final Type<EjectorLaunchPacket> TYPE=new Type<>(Create.asResource("ejector_launch"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EjectorLaunchPacket> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeBlockPos(p.pos);b.writeLong(p.nonce);b.writeInt(p.h);b.writeInt(p.v);b.writeEnum(p.facing);b.writeBoolean(p.glider);
    },b->new EjectorLaunchPacket(b.readBlockPos(),b.readLong(),b.readInt(),b.readInt(),b.readEnum(Direction.class),b.readBoolean()));
    public EjectorLaunchPacket{pos=pos.immutable();}
    @Override public Type<EjectorLaunchPacket> type(){return TYPE;}
}
