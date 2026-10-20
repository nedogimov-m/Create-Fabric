package com.simibubi.create.content.schematics.packet;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Actual authoritative upload acceptance/completion, correlated to its native menu session. */
public record SchematicUploadStatusPacket(UUID token,Stage stage,String file) implements CustomPacketPayload {
    public enum Stage {ACCEPTED,COMPLETED,CANCELLED}
    public static final Type<SchematicUploadStatusPacket> TYPE=new Type<>(Create.asResource("schematic_upload_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SchematicUploadStatusPacket> CODEC=StreamCodec.of((buffer,value)->{buffer.writeUUID(value.token);buffer.writeEnum(value.stage);buffer.writeUtf(value.file,128);},buffer->new SchematicUploadStatusPacket(buffer.readUUID(),buffer.readEnum(Stage.class),buffer.readUtf(128)));
    @Override public Type<SchematicUploadStatusPacket> type(){return TYPE;}
}
