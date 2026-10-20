package com.simibubi.create.content.contraptions.sync;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record LimbSwingUpdatePacket(int entityId,UUID uuid,Vec3 position,float limbSwing) implements CustomPacketPayload {
    public static final Type<LimbSwingUpdatePacket> TYPE=new Type<>(Create.asResource("contraption_limb_swing"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LimbSwingUpdatePacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.entityId);buf.writeUUID(packet.uuid);buf.writeDouble(packet.position.x);buf.writeDouble(packet.position.y);buf.writeDouble(packet.position.z);buf.writeFloat(packet.limbSwing);},buf->new LimbSwingUpdatePacket(buf.readVarInt(),buf.readUUID(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readFloat()));
    @Override public Type<LimbSwingUpdatePacket> type(){return TYPE;}
}
