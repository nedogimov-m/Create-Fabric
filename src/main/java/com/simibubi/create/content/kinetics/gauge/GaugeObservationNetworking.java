package com.simibubi.create.content.kinetics.gauge;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** A real server acknowledgement permits bounded retry when native view/gear packets arrive later than the HUD. */
public final class GaugeObservationNetworking {
 public record Acknowledged(BlockPos pos,Identifier dimension,long nonce) implements CustomPacketPayload {
  public static final Type<Acknowledged> TYPE=new Type<>(Create.asResource("gauge_observed_ack"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Acknowledged> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeUtf(p.dimension.toString(),256);b.writeLong(p.nonce);},b->new Acknowledged(b.readBlockPos(),Identifier.parse(b.readUtf(256)),b.readLong()));
  public Acknowledged{pos=pos.immutable();}
  @Override public Type<Acknowledged> type(){return TYPE;}
 }
 private static boolean registered;
 public static void register(){if(registered)return;registered=true;com.simibubi.create.foundation.advancement.GaugeAdvancements.register();PayloadTypeRegistry.serverboundPlay().register(GaugeObservedPacket.TYPE,GaugeObservedPacket.STREAM_CODEC);PayloadTypeRegistry.clientboundPlay().register(Acknowledged.TYPE,Acknowledged.STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(GaugeObservedPacket.TYPE,(packet,context)->{if(packet.apply(context.player()))ServerPlayNetworking.send(context.player(),new Acknowledged(packet.pos(),context.player().level().dimension().identifier(),packet.nonce()));});}
 private GaugeObservationNetworking(){}
}
