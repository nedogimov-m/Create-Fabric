package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Source position+instructions configuration, bounded before allocation and authoritative on the server. */
public record ConfigureSequencedGearshiftPacket(BlockPos pos,List<Instruction> instructions) implements CustomPacketPayload {
    public static final Type<ConfigureSequencedGearshiftPacket> TYPE=new Type<>(Create.asResource("configure_sequencer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigureSequencedGearshiftPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{
        buffer.writeBlockPos(packet.pos);Instruction.LIST_STREAM_CODEC.encode(buffer,packet.instructions);
    },buffer->new ConfigureSequencedGearshiftPacket(buffer.readBlockPos(),Instruction.LIST_STREAM_CODEC.decode(buffer)));
    public ConfigureSequencedGearshiftPacket{pos=pos.immutable();instructions=List.copyOf(instructions);if(instructions.isEmpty()||instructions.size()>5)throw new IllegalArgumentException("Sequencer requires one to five instructions");}
    @Override public Type<ConfigureSequencedGearshiftPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){
        if(player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)
            ||!player.level().mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(player.level(),pos))return false;
        if(!(LoadedLevelAccess.blockEntity(player.level(),pos)instanceof SequencedGearshiftBlockEntity owner))return false;
        owner.configure(instructions);return true;
    }
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.apply(context.player()));}
}
