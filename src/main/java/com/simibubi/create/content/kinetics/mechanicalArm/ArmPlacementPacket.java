package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Source relative target payload, bound to a real placement's one-use server nonce and placer. */
public record ArmPlacementPacket(List<ArmInteractionPoint.PointData> points,BlockPos pos,long nonce) implements CustomPacketPayload {
    public static final Type<ArmPlacementPacket> TYPE=new Type<>(Create.asResource("place_arm"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ArmPlacementPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{
        buffer.writeBlockPos(packet.pos);buffer.writeLong(packet.nonce);buffer.writeVarInt(packet.points.size());
        for(var point:packet.points){buffer.writeUtf(point.type().toString(),128);buffer.writeBlockPos(point.pos());buffer.writeBoolean(point.mode()==ArmInteractionPoint.Mode.TAKE);buffer.writeNbt(point.extra());}
    },buffer->{
        var pos=buffer.readBlockPos();long nonce=buffer.readLong();int count=buffer.readVarInt();
        // Every real row needs at least its encoded type, position, mode and NBT bytes.
        // Bound allocation by this actual native packet, not an arbitrary number of valid points.
        if(count<0||count>buffer.readableBytes()/12)throw new io.netty.handler.codec.DecoderException("Mechanical arm point count exceeds actual remaining payload");
        var points=new ArrayList<ArmInteractionPoint.PointData>(count);
        for(int i=0;i<count;i++){
            var type=Identifier.parse(buffer.readUtf(128));var relative=buffer.readBlockPos();var mode=buffer.readBoolean()?ArmInteractionPoint.Mode.TAKE:ArmInteractionPoint.Mode.DEPOSIT;
            var raw=buffer.readNbt(net.minecraft.nbt.NbtAccounter.create(8192));if(!(raw instanceof net.minecraft.nbt.CompoundTag extra))throw new io.netty.handler.codec.DecoderException("Arm target addon data must be a compound");
            points.add(new ArmInteractionPoint.PointData(type,relative,mode,extra));
        }
        return new ArmPlacementPacket(points,pos,nonce);
    });
    public ArmPlacementPacket{
        points=List.copyOf(points);pos=pos.immutable();var positions=new java.util.HashSet<BlockPos>();
        for(var point:points)if(!positions.add(point.pos()))throw new IllegalArgumentException("Duplicate mechanical arm target location");
    }
    @Override public Type<ArmPlacementPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){
        if(player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)||!player.level().mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(player.level(),pos))return false;
        return LoadedLevelAccess.blockEntity(player.level(),pos) instanceof ArmBlockEntity arm&&arm.acceptPlacement(player,nonce,points);
    }
    public record ClientBoundRequest(BlockPos pos,long nonce) implements CustomPacketPayload {
        public static final Type<ClientBoundRequest> TYPE=new Type<>(Create.asResource("s_place_arm"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ClientBoundRequest> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeBlockPos(packet.pos);buffer.writeLong(packet.nonce);},buffer->new ClientBoundRequest(buffer.readBlockPos(),buffer.readLong()));
        public ClientBoundRequest{pos=pos.immutable();}
        @Override public Type<ClientBoundRequest> type(){return TYPE;}
    }
}
