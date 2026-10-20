package com.simibubi.create.content.equipment.zapper;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
public class ZapperBeamPacket extends ShootGadgetPacket {
    public static final Type<ZapperBeamPacket> TYPE=new Type<>(Create.asResource("beam_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ZapperBeamPacket> STREAM_CODEC=StreamCodec.of((b,p)->{Vec3.STREAM_CODEC.encode(b,p.location);b.writeEnum(p.hand);b.writeBoolean(p.self);Vec3.STREAM_CODEC.encode(b,p.target);},b->new ZapperBeamPacket(Vec3.STREAM_CODEC.decode(b),b.readEnum(InteractionHand.class),b.readBoolean(),Vec3.STREAM_CODEC.decode(b)));
    public final Vec3 target;
    public ZapperBeamPacket(Vec3 start,InteractionHand hand,boolean self,Vec3 target){super(start,hand,self);this.target=target;}
    @Override public Type<ZapperBeamPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,STREAM_CODEC);}
}
