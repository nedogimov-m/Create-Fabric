package com.simibubi.create.content.trains.track;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Source curve operation, with native BEGIN/HOLD/ABORT heartbeats; client progress never grants destruction. */
public record CurvedTrackDestroyPacket(BlockPos pos,BlockPos targetPos,int segment,Action action) implements CustomPacketPayload {
    public enum Action{BEGIN,HOLD,ABORT,WRENCH}
    public static final Type<CurvedTrackDestroyPacket> TYPE=new Type<>(Create.asResource("destroy_curved_track"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CurvedTrackDestroyPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeBlockPos(packet.targetPos);buf.writeVarInt(packet.segment);buf.writeEnum(packet.action);},buf->new CurvedTrackDestroyPacket(buf.readBlockPos(),buf.readBlockPos(),buf.readVarInt(),buf.readEnum(Action.class)));
    @Override public Type<CurvedTrackDestroyPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->CurvedTrackServerInteraction.apply(context.player(),packet));PayloadTypeRegistry.clientboundPlay().register(Progress.TYPE,Progress.CODEC);CurvedTrackServerInteraction.register();}
    public record Progress(BlockPos pos,BlockPos targetPos,float progress,boolean removed) implements CustomPacketPayload {
        public static final Type<Progress> TYPE=new Type<>(Create.asResource("curved_track_break_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Progress> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeBlockPos(packet.targetPos);buf.writeFloat(packet.progress);buf.writeBoolean(packet.removed);},buf->new Progress(buf.readBlockPos(),buf.readBlockPos(),buf.readFloat(),buf.readBoolean()));
        @Override public Type<Progress> type(){return TYPE;}
    }
}
