package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Native session guard prevents a delayed screen from configuring another held wand. */
public record ConfigureSymmetryWandPacket(InteractionHand hand,SymmetryMirror mirror,UUID session) implements CustomPacketPayload {
    public ConfigureSymmetryWandPacket{mirror=mirror.copy();}
    public ConfigureSymmetryWandPacket(InteractionHand hand,SymmetryMirror mirror){this(hand,mirror,new UUID(0,0));}
    public static final Type<ConfigureSymmetryWandPacket> TYPE=new Type<>(Create.asResource("configure_symmetry_wand"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigureSymmetryWandPacket> STREAM_CODEC=StreamCodec.of((buffer,value)->{buffer.writeBoolean(value.hand==InteractionHand.OFF_HAND);SymmetryMirror.STREAM_CODEC.encode(buffer,value.mirror);buffer.writeUUID(value.session);},buffer->new ConfigureSymmetryWandPacket(buffer.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,SymmetryMirror.STREAM_CODEC.decode(buffer),buffer.readUUID()));
    public boolean handle(ServerPlayer player){return SymmetryWandNetworking.configure(player,this);}
    @Override public Type<ConfigureSymmetryWandPacket> type(){return TYPE;}
}
