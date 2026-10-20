package com.simibubi.create.content.schematics.packet;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Native held-slot transform synchronization; source file and owner components are never client supplied. */
public record SchematicSyncPacket(int slot,boolean deployed,BlockPos anchor,Rotation rotation,Mirror mirror) implements CustomPacketPayload {
    public SchematicSyncPacket{anchor=anchor.immutable();java.util.Objects.requireNonNull(rotation);java.util.Objects.requireNonNull(mirror);}
    public SchematicSyncPacket(int slot,StructurePlaceSettings settings,BlockPos anchor,boolean deployed){this(slot,deployed,anchor,settings.getRotation(),settings.getMirror());}
    public static final Type<SchematicSyncPacket> TYPE=new Type<>(Create.asResource("sync_schematic"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SchematicSyncPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeVarInt(packet.slot);buffer.writeBoolean(packet.deployed);buffer.writeBlockPos(packet.anchor);buffer.writeEnum(packet.rotation);buffer.writeEnum(packet.mirror);},buffer->new SchematicSyncPacket(buffer.readVarInt(),buffer.readBoolean(),buffer.readBlockPos(),buffer.readEnum(Rotation.class),buffer.readEnum(Mirror.class)));
    public boolean handle(ServerPlayer player){if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player||slot< -1||slot>=player.getInventory().getContainerSize())return false;var stack=slot== -1?player.getMainHandItem():player.getInventory().getItem(slot);if(!stack.is(AllItems.SCHEMATIC))return false;stack.set(AllDataComponents.SCHEMATIC_DEPLOYED,deployed);stack.set(AllDataComponents.SCHEMATIC_ANCHOR,anchor);stack.set(AllDataComponents.SCHEMATIC_ROTATION,rotation);stack.set(AllDataComponents.SCHEMATIC_MIRROR,mirror);stack.remove(AllDataComponents.SCHEMATIC_HASH);player.getInventory().setChanged();return true;}
    @Override public Type<SchematicSyncPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));}
}
