package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ArmNetworking {
    public record Range(int value) implements CustomPacketPayload {
        public static final Type<Range> TYPE=new Type<>(Create.asResource("arm_range"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Range> STREAM_CODEC=StreamCodec.of((buffer,packet)->buffer.writeVarInt(packet.value),buffer->new Range(buffer.readVarInt()));
        public Range{if(value<1)throw new IllegalArgumentException("Invalid mechanical arm range");}
        @Override public Type<Range> type(){return TYPE;}
    }
    public record Settings(BlockPos pos,ArmBlockEntity.SelectionMode mode) implements CustomPacketPayload {
        public static final Type<Settings> TYPE=new Type<>(Create.asResource("arm_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Settings> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeBlockPos(packet.pos);buffer.writeEnum(packet.mode);},buffer->new Settings(buffer.readBlockPos(),buffer.readEnum(ArmBlockEntity.SelectionMode.class)));
        public Settings{pos=pos.immutable();java.util.Objects.requireNonNull(mode);}
        @Override public Type<Settings> type(){return TYPE;}
        public boolean apply(ServerPlayer player){
            if(player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)||!player.level().mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(player.level(),pos))return false;
            if(!(LoadedLevelAccess.blockEntity(player.level(),pos) instanceof ArmBlockEntity arm))return false;
            arm.setSelectionMode(mode);return true;
        }
    }
    public static void openSettings(ServerPlayer player,ArmBlockEntity arm){ServerPlayNetworking.send(player,new Settings(arm.getBlockPos(),arm.getSelectionMode()));}
    private static boolean registered;
    public static void register(){
        if(registered)return;registered=true;
        PayloadTypeRegistry.serverboundPlay().register(ArmPlacementPacket.TYPE,ArmPlacementPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ArmPlacementPacket.ClientBoundRequest.TYPE,ArmPlacementPacket.ClientBoundRequest.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Settings.TYPE,Settings.STREAM_CODEC);PayloadTypeRegistry.clientboundPlay().register(Settings.TYPE,Settings.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Range.TYPE,Range.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ArmPlacementPacket.TYPE,(packet,context)->packet.apply(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Settings.TYPE,(packet,context)->packet.apply(context.player()));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(level.isClientSide()||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,hit.getBlockPos())||!(player.getItemInHand(hand).getItem() instanceof ArmItem))return net.minecraft.world.InteractionResult.PASS;
            return ArmInteractionPoint.isInteractable(level,hit.getBlockPos(),LoadedLevelAccess.blockState(level,hit.getBlockPos()))?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sender.sendPacket(new Range(ArmConfig.server())));
    }
    private ArmNetworking(){}
}
