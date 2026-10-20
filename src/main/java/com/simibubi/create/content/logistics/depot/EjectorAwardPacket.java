package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Only a genuine recent launch can fire the original ejector_maxed criterion. */
public record EjectorAwardPacket(BlockPos pos,long nonce) implements CustomPacketPayload {
    public static final Type<EjectorAwardPacket> TYPE=new Type<>(Create.asResource("ejector_award"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EjectorAwardPacket> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeLong(p.nonce);},b->new EjectorAwardPacket(b.readBlockPos(),b.readLong()));
    public EjectorAwardPacket{pos=pos.immutable();}
    @Override public Type<EjectorAwardPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){var owner=EjectorNetworking.live(player,pos);return owner!=null&&owner.acceptAward(player,nonce);}
    static void registerCriterion(){var id=Create.asResource("ejector_maxed_builtin");if(!BuiltInRegistries.TRIGGER_TYPES.containsKey(id))com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(id);}
    static void award(ServerPlayer player){
        var holder=player.level().getServer().getAdvancements().get(Create.asResource("ejector_maxed"));
        if(holder==null||player.getAdvancements().getOrStartProgress(holder).isDone())return;
        if(!(BuiltInRegistries.TRIGGER_TYPES.getValue(Create.asResource("ejector_maxed_builtin")) instanceof SimpleCreateTrigger trigger))throw new IllegalStateException("Actual ejector criterion is absent");
        trigger.trigger(player);
    }
}
