package com.simibubi.create.content.contraptions;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;

public final class ContraptionNetworking {
    public static void syncSeats(AbstractContraptionEntity entity,net.minecraft.world.entity.Entity passenger,net.minecraft.world.phys.Vec3 position){
        if(entity.getContraption()==null)return;var data=new net.minecraft.nbt.CompoundTag();entity.getContraption().writeSeatMapping(data);
        if(passenger!=null&&position!=null){var dismount=new net.minecraft.nbt.CompoundTag();dismount.putInt("Entity",passenger.getId());dismount.store("Id",net.minecraft.core.UUIDUtil.CODEC,passenger.getUUID());dismount.store("Pos",net.minecraft.world.phys.Vec3.CODEC,position);data.put("Dismount",dismount);}
        var watchers=PlayerLookup.tracking(entity);if(watchers.isEmpty())return;for(var packet:ContraptionDataPacket.split(entity.getId(),entity.getUUID(),false,data))for(var player:watchers)ServerPlayNetworking.send(player,packet);
    }
    public static void syncActors(AbstractContraptionEntity entity){
        if(entity.getContraption()==null)return;var watchers=PlayerLookup.tracking(entity);if(watchers.isEmpty())return;
        var data=new net.minecraft.nbt.CompoundTag();entity.getContraption().writeActors(data,entity.registryAccess());data.putBoolean("Stalled",entity.isStalled());
        for(var packet:ContraptionDataPacket.split(entity.getId(),entity.getUUID(),false,data))for(var player:watchers)ServerPlayNetworking.send(player,packet);
    }
    public static void syncActorData(com.simibubi.create.content.contraptions.behaviour.MovementContext context){
        var owner=context.contraption.entity;if(owner==null||owner.level().isClientSide())return;var watchers=PlayerLookup.tracking(owner);if(watchers.isEmpty())return;
        var data=new net.minecraft.nbt.CompoundTag();var entries=new net.minecraft.nbt.ListTag();var entry=new net.minecraft.nbt.CompoundTag();entry.store("Pos",net.minecraft.core.BlockPos.CODEC,context.localPos);entry.put("Data",context.data.copy());entry.putBoolean("Stall",context.stall);entries.add(entry);data.put("ActorData",entries);
        for(var packet:ContraptionDataPacket.split(owner.getId(),owner.getUUID(),false,data))for(var watcher:watchers)ServerPlayNetworking.send(watcher,packet);
    }
    public static void register() {
        ContraptionPassengerData.register();
        com.simibubi.create.foundation.advancement.TrainPassengerAdvancements.register();
        PayloadTypeRegistry.serverboundPlay().register(TrainCollisionPacket.TYPE,TrainCollisionPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TrainCollisionPacket.TYPE,(packet,context)->packet.handle(context.player()));
        PayloadTypeRegistry.clientboundPlay().register(com.simibubi.create.content.contraptions.sync.LimbSwingUpdatePacket.TYPE,com.simibubi.create.content.contraptions.sync.LimbSwingUpdatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.simibubi.create.content.contraptions.sync.ClientMotionPacket.TYPE,com.simibubi.create.content.contraptions.sync.ClientMotionPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.simibubi.create.content.contraptions.sync.ClientMotionPacket.TYPE,(packet,context)->packet.apply(context.player()));
        PayloadTypeRegistry.clientboundPlay().register(ContraptionDataPacket.TYPE, ContraptionDataPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ContraptionInteractionPacket.TYPE, ContraptionInteractionPacket.CODEC);
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> { if (entity instanceof AbstractContraptionEntity contraption) sendFull(contraption, player); });
        ServerPlayNetworking.registerGlobalReceiver(ContraptionInteractionPacket.TYPE, (packet, context) -> {
            var player = context.player();
            if (player.level().getEntity(packet.entityId()) instanceof AbstractContraptionEntity entity && entity.getUUID().equals(packet.entityUuid())) entity.handleInteractionPacket(player, packet);
        });
    }
    public static void sendFull(AbstractContraptionEntity entity, ServerPlayer player) {
        if (entity.getContraption() == null) return;
        for (var packet : ContraptionDataPacket.split(entity.getId(), entity.getUUID(), true, entity.getContraption().writeNBT(entity.registryAccess(), true))) ServerPlayNetworking.send(player, packet);
    }
    public static void syncStorage(AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) return;
        var data = entity.getContraption().getStorage().collectSync(entity.registryAccess()); if (data.isEmpty()) return;
        var watchers = PlayerLookup.tracking(entity); if (watchers.isEmpty()) return;
        for (var packet : ContraptionDataPacket.split(entity.getId(), entity.getUUID(), false, data)) for (var player : watchers) ServerPlayNetworking.send(player, packet);
    }
    private ContraptionNetworking() {}
}
