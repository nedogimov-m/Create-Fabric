package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class EjectorNetworking {
    public record Limits(EjectorConfig.Values values) implements CustomPacketPayload {
        public static final Type<Limits> TYPE=new Type<>(Create.asResource("ejector_limits"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Limits> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.values.maxDistance());b.writeVarInt(p.values.scanInterval());},b->new Limits(new EjectorConfig.Values(b.readVarInt(),b.readVarInt())));
        @Override public Type<Limits> type(){return TYPE;}
    }
    public record Settings(BlockPos pos,int value) implements CustomPacketPayload {
        public static final Type<Settings> TYPE=new Type<>(Create.asResource("ejector_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Settings> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeVarInt(p.value);},b->new Settings(b.readBlockPos(),b.readVarInt()));
        public Settings{pos=pos.immutable();if(value<0||value>64)throw new IllegalArgumentException("Ejector setting outside source0..64");}
        @Override public Type<Settings> type(){return TYPE;}
        public boolean apply(ServerPlayer player){var owner=interactable(player,pos);if(owner==null)return false;owner.setMinimumStackSize(value);return true;}
    }
    static EjectorBlockEntity live(ServerPlayer player,BlockPos pos){return !player.isAlive()||!LoadedLevelAccess.isLoaded(player.level(),pos)||!(LoadedLevelAccess.blockEntity(player.level(),pos) instanceof EjectorBlockEntity owner)?null:owner;}
    static EjectorBlockEntity interactable(ServerPlayer player,BlockPos pos){return player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)||!player.level().mayInteract(player,pos)?null:live(player,pos);}
    public static void openSettings(ServerPlayer player,EjectorBlockEntity owner){if(interactable(player,owner.getBlockPos())==owner)ServerPlayNetworking.send(player,new Settings(owner.getBlockPos(),owner.getMinimumStackSize()));}
    private static java.util.function.Consumer<BlockPos> clientTrigger;
    public static void installClientTrigger(java.util.function.Consumer<BlockPos> callback){clientTrigger=java.util.Objects.requireNonNull(callback);}
    static void requestClientTrigger(BlockPos pos){if(clientTrigger==null)throw new IllegalStateException("Actual ejector client networking has not initialized");clientTrigger.accept(pos);}
    private static boolean registered;
    public static void register(){
        if(registered)return;registered=true;EjectorAwardPacket.registerCriterion();
        PayloadTypeRegistry.serverboundPlay().register(EjectorPlacementPacket.TYPE,EjectorPlacementPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EjectorPlacementPacket.ClientBoundRequest.TYPE,EjectorPlacementPacket.ClientBoundRequest.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EjectorTriggerPacket.TYPE,EjectorTriggerPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EjectorAwardPacket.TYPE,EjectorAwardPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EjectorElytraPacket.TYPE,EjectorElytraPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EjectorLaunchPacket.TYPE,EjectorLaunchPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Settings.TYPE,Settings.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Settings.TYPE,Settings.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Limits.TYPE,Limits.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EjectorPlacementPacket.TYPE,(packet,context)->packet.apply(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(EjectorTriggerPacket.TYPE,(packet,context)->packet.apply(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(EjectorAwardPacket.TYPE,(packet,context)->packet.apply(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(EjectorElytraPacket.TYPE,(packet,context)->packet.apply(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Settings.TYPE,(packet,context)->packet.apply(context.player()));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sender.sendPacket(new Limits(EjectorConfig.server())));
    }
    private EjectorNetworking(){}
}
