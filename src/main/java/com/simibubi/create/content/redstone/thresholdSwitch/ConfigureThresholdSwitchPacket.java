package com.simibubi.create.content.redstone.thresholdSwitch;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Exact native menu/BE identity, revision, range and build permission checked on the server. */
public record ConfigureThresholdSwitchPacket(int containerId,BlockPos pos,int revision,int upper,int lower,boolean stacks,boolean inverted)implements CustomPacketPayload {
    public ConfigureThresholdSwitchPacket{pos=pos.immutable();}
    public static final Type<ConfigureThresholdSwitchPacket> TYPE=new Type<>(Create.asResource("configure_threshold_switch"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigureThresholdSwitchPacket> CODEC=StreamCodec.of((buffer,value)->{buffer.writeContainerId(value.containerId);buffer.writeBlockPos(value.pos);buffer.writeVarInt(value.revision);buffer.writeInt(value.upper);buffer.writeInt(value.lower);buffer.writeBoolean(value.stacks);buffer.writeBoolean(value.inverted);},buffer->new ConfigureThresholdSwitchPacket(buffer.readContainerId(),buffer.readBlockPos(),buffer.readVarInt(),buffer.readInt(),buffer.readInt(),buffer.readBoolean(),buffer.readBoolean()));
    @Override public Type<ConfigureThresholdSwitchPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){return player.containerMenu instanceof ThresholdSwitchMenu menu&&menu.containerId==containerId&&menu.opening().pos().equals(pos)&&menu.configure(player,this);}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.apply(context.player()));}
}
