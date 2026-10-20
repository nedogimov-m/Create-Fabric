package com.simibubi.create.content.contraptions.sync;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Source client-owned player motion, accepted only for a real recent server-side support contact. */
public record ClientMotionPacket(int contraptionId,UUID contraptionUuid,Vec3 motion,boolean onGround,float limbSwing) implements CustomPacketPayload {
    public static final Type<ClientMotionPacket> TYPE=new Type<>(Create.asResource("contraption_player_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ClientMotionPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.contraptionId);buf.writeUUID(packet.contraptionUuid);buf.writeDouble(packet.motion.x);buf.writeDouble(packet.motion.y);buf.writeDouble(packet.motion.z);buf.writeBoolean(packet.onGround);buf.writeFloat(packet.limbSwing);},buf->new ClientMotionPacket(buf.readVarInt(),buf.readUUID(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readBoolean(),buf.readFloat()));
    @Override public Type<ClientMotionPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){
        if(!Double.isFinite(motion.x)||!Double.isFinite(motion.y)||!Double.isFinite(motion.z)||motion.lengthSqr()>400||!Float.isFinite(limbSwing)||limbSwing<0||limbSwing>1||player.isSpectator()||player.isPassenger())return false;
        if(!(player.level().getEntity(contraptionId) instanceof AbstractContraptionEntity owner)||!owner.getUUID().equals(contraptionUuid)||!owner.isAlive())return false;
        var contact=owner.collidingEntities.get(player);if(contact==null||contact.intValue()>3||!owner.getBoundingBox().inflate(2).intersects(player.getBoundingBox()))return false;
        player.setDeltaMovement(motion);player.setOnGround(onGround);if(onGround){player.causeFallDamage(player.fallDistance,1,player.damageSources().fall());player.resetFallDistance();if(player.connection!=null)player.connection.resetFlyingTicks();}
        if(player.connection!=null){var update=new LimbSwingUpdatePacket(player.getId(),player.getUUID(),player.position(),limbSwing);for(var watcher:net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player))net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(watcher,update);}
        return true;
    }
}
