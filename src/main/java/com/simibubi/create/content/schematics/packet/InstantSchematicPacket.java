package com.simibubi.create.content.schematics.packet;

import com.simibubi.create.Create;
import com.simibubi.create.content.schematics.ServerSchematicLoader;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Source quill export in the actual server world, replacing its exact original held quill. */
public record InstantSchematicPacket(String name,BlockPos origin,BlockPos bounds) implements CustomPacketPayload {
    public InstantSchematicPacket{java.util.Objects.requireNonNull(name);origin=origin.immutable();bounds=bounds.immutable();if(name.length()>128||bounds.getX()<1||bounds.getY()<1||bounds.getZ()<1)throw new IllegalArgumentException("Invalid source quill export request");}
    public static final Type<InstantSchematicPacket> TYPE=new Type<>(Create.asResource("instant_schematic"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InstantSchematicPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeUtf(packet.name,128);buffer.writeBlockPos(packet.origin);buffer.writeBlockPos(packet.bounds);},buffer->new InstantSchematicPacket(buffer.readUtf(128),buffer.readBlockPos(),buffer.readBlockPos()));
    public boolean handle(ServerPlayer player){return ServerSchematicLoader.get(player.level().getServer()).handleInstantSchematic(player,name,origin,bounds);}
    @Override public Type<InstantSchematicPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));}
}
