package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.Create;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Exact double coordinate/motion updates retain the source's client prediction without float-world loss. */
public record GantryContraptionUpdatePacket(int entityId,UUID owner,double coordinate,double motion,double sequenceLimit,Vec3 stoppedPosition)implements CustomPacketPayload {
    public static final Type<GantryContraptionUpdatePacket> TYPE=new Type<>(Create.asResource("gantry_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GantryContraptionUpdatePacket> CODEC=CustomPacketPayload.codec((packet,buf)->{
        buf.writeVarInt(packet.entityId);buf.writeUUID(packet.owner);buf.writeDouble(packet.coordinate);buf.writeDouble(packet.motion);buf.writeDouble(packet.sequenceLimit);
        buf.writeBoolean(packet.stoppedPosition!=null);if(packet.stoppedPosition!=null){buf.writeDouble(packet.stoppedPosition.x);buf.writeDouble(packet.stoppedPosition.y);buf.writeDouble(packet.stoppedPosition.z);}
    },buf->{int entity=buf.readVarInt();var uuid=buf.readUUID();double coordinate=buf.readDouble(),motion=buf.readDouble(),limit=buf.readDouble();var position=buf.readBoolean()?new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()):null;return new GantryContraptionUpdatePacket(entity,uuid,coordinate,motion,limit,position);});
    public GantryContraptionUpdatePacket(int entityId,UUID owner,double coordinate,double motion,double sequenceLimit){this(entityId,owner,coordinate,motion,sequenceLimit,null);}
    public GantryContraptionUpdatePacket{if(!Double.isFinite(coordinate)||!Double.isFinite(motion)||Math.abs(motion)>.490001||!Double.isFinite(sequenceLimit)||sequenceLimit<0&&sequenceLimit!=-1||stoppedPosition!=null&&(!Double.isFinite(stoppedPosition.x)||!Double.isFinite(stoppedPosition.y)||!Double.isFinite(stoppedPosition.z)))throw new IllegalArgumentException("Invalid native gantry motion");}
    @Override public Type<GantryContraptionUpdatePacket> type(){return TYPE;}
    public static void register(){
        PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);
        EntityTrackingEvents.START_TRACKING.register((entity,player)->{if(entity instanceof GantryContraptionEntity gantry&&gantry.getContraption()!=null)ServerPlayNetworking.send(player,gantry.motionPacket());});
    }
}
