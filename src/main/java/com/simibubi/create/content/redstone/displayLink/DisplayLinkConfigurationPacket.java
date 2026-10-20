package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Real bounded config packet, bound to the exact open native menu and actual owner identity. */
public record DisplayLinkConfigurationPacket(int containerId,BlockPos pos,CompoundTag configData,int targetLine)implements CustomPacketPayload {
    public static final Type<DisplayLinkConfigurationPacket> TYPE=new Type<>(Create.asResource("configure_data_gatherer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisplayLinkConfigurationPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeContainerId(packet.containerId);buffer.writeBlockPos(packet.pos);buffer.writeNbt(packet.configData);buffer.writeVarInt(packet.targetLine);},buffer->{int menu=buffer.readContainerId();var pos=buffer.readBlockPos();var raw=buffer.readNbt(NbtAccounter.create(8192));if(!(raw instanceof CompoundTag tag))throw new io.netty.handler.codec.DecoderException("Display link configuration must be a compound");return new DisplayLinkConfigurationPacket(menu,pos,tag,buffer.readVarInt());});
    public DisplayLinkConfigurationPacket{pos=pos.immutable();configData=configData.copy();if(configData.sizeInBytes()>8192)throw new IllegalArgumentException("Display configuration exceeds8KiB");}
    @Override public Type<DisplayLinkConfigurationPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){if(!(player.containerMenu instanceof DisplayLinkMenu menu)||menu.containerId!=containerId||!menu.opening().pos().equals(pos)||!menu.stillValid(player)||menu.owner()==null)return false;return menu.owner().configure(configData,targetLine);}
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.apply(context.player()));}
}
