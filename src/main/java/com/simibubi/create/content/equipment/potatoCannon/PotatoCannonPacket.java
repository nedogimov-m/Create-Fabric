package com.simibubi.create.content.equipment.potatoCannon;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.zapper.ShootGadgetPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class PotatoCannonPacket extends ShootGadgetPacket {
    public static final Type<PotatoCannonPacket> TYPE=new Type<>(Create.asResource("potato_cannon"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PotatoCannonPacket> STREAM_CODEC=StreamCodec.of((buffer,value)->{Vec3.STREAM_CODEC.encode(buffer,value.location);Vec3.STREAM_CODEC.encode(buffer,value.motion);ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,value.item);buffer.writeEnum(value.hand);buffer.writeFloat(value.pitch);buffer.writeBoolean(value.self);},buffer->new PotatoCannonPacket(Vec3.STREAM_CODEC.decode(buffer),Vec3.STREAM_CODEC.decode(buffer),ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),buffer.readEnum(InteractionHand.class),buffer.readFloat(),buffer.readBoolean()));
    public final Vec3 motion;
    public final ItemStack item;
    public final float pitch;
    public PotatoCannonPacket(Vec3 location,Vec3 motion,ItemStack item,InteractionHand hand,float pitch,boolean self){super(location,hand,self);this.motion=motion;this.item=item.copy();this.pitch=pitch;}
    @Override public Type<PotatoCannonPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,STREAM_CODEC);}
}
