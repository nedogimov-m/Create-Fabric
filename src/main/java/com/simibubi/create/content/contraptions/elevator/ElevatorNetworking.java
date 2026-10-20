package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.decoration.slidingDoor.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;

public final class ElevatorNetworking {
    private static Consumer<AbstractContraptionEntity> clientRequester=owner->{};
    private static BiConsumer<AbstractContraptionEntity,BlockPos> clientSelector=(owner,pos)->{};
    public static void setClientHandlers(Consumer<AbstractContraptionEntity> requester,BiConsumer<AbstractContraptionEntity,BlockPos> selector){clientRequester=Objects.requireNonNull(requester);clientSelector=Objects.requireNonNull(selector);}
    public static void requestFloors(AbstractContraptionEntity owner){if(owner.level().isClientSide())clientRequester.accept(owner);}
    public static void selectFromControls(AbstractContraptionEntity owner,BlockPos pos){if(owner.level().isClientSide())clientSelector.accept(owner,pos);}
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(ElevatorTargetFloorPacket.TYPE,ElevatorTargetFloorPacket.CODEC);PayloadTypeRegistry.serverboundPlay().register(ElevatorFloorListPacket.RequestFloorList.TYPE,ElevatorFloorListPacket.RequestFloorList.CODEC);PayloadTypeRegistry.serverboundPlay().register(ElevatorContactEditPacket.TYPE,ElevatorContactEditPacket.CODEC);PayloadTypeRegistry.clientboundPlay().register(ElevatorFloorListPacket.TYPE,ElevatorFloorListPacket.CODEC);PayloadTypeRegistry.clientboundPlay().register(ElevatorContactEditPacket.Open.TYPE,ElevatorContactEditPacket.Open.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ElevatorTargetFloorPacket.TYPE,(packet,context)->handleTarget(context.player(),packet));ServerPlayNetworking.registerGlobalReceiver(ElevatorFloorListPacket.RequestFloorList.TYPE,(packet,context)->{var owner=resolve(context.player(),packet.entityId(),packet.owner());if(owner!=null)sendFloors(owner,context.player());});ServerPlayNetworking.registerGlobalReceiver(ElevatorContactEditPacket.TYPE,(packet,context)->handleEdit(context.player(),packet));
        EntityTrackingEvents.START_TRACKING.register((entity,player)->{if(entity instanceof AbstractContraptionEntity owner&&owner.getContraption() instanceof ElevatorContraption)sendFloors(owner,player);});ServerTickEvents.END_LEVEL_TICK.register(ElevatorColumn::tick);ServerLevelEvents.UNLOAD.register((server,level)->ElevatorColumn.clear(level));
        DoorControlBehaviour.registerResolver(context->{if(!(context.contraption instanceof ElevatorContraption cabin))return null;var target=cabin.getCurrentTargetY(context.world);var column=cabin.getGlobalColumn();if(target==null||column==null)return null;var pos=column.contactAt(target);return LoadedLevelAccess.blockEntity(context.world,pos) instanceof ElevatorContactBlockEntity contact?contact.doorControls:null;});
    }
    private static AbstractContraptionEntity resolve(ServerPlayer player,int id,UUID uuid){if(!(player.level().getEntity(id) instanceof AbstractContraptionEntity owner)||!owner.getUUID().equals(uuid)||!owner.isAlive()||!owner.hasOwnedStructure()||!(owner.getContraption() instanceof ElevatorContraption)||owner.distanceToSqr(player)>2500)return null;return owner;}
    public static boolean handleTarget(ServerPlayer player,ElevatorTargetFloorPacket packet){
        var owner=resolve(player,packet.entityId(),packet.owner());if(owner==null||player.isSpectator()||ContraptionAssemblyGraph.isRecovering(owner))return false;var cabin=(ElevatorContraption)owner.getContraption();if(cabin.isTargetUnreachable(packet.targetY()))return false;var column=ElevatorColumn.get(player.level(),cabin.getGlobalColumn());if(column==null||!Objects.equals(column.activeOwner(),owner.getUUID())||!column.contains(packet.targetY()))return false;var pos=column.contactAt(packet.targetY());var state=LoadedLevelAccess.blockState(player.level(),pos);if(!(state.getBlock() instanceof ElevatorContactBlock contact)||!column.coordinates().equals(ElevatorContactBlock.getColumnCoords(player.level(),pos)))return false;contact.callToContactAndUpdate(column,state,player.level(),pos,false);return true;
    }
    public static boolean handleEdit(ServerPlayer player,ElevatorContactEditPacket packet){
        var level=player.level();if(player.isSpectator()||!player.mayBuild()||!LoadedLevelAccess.isLoaded(level,packet.pos())||player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(packet.pos()))>64||!level.mayInteract(player,packet.pos())||!(LoadedLevelAccess.blockEntity(level,packet.pos()) instanceof ElevatorContactBlockEntity contact))return false;contact.updateName(packet.shortName(),packet.longName());contact.doorControls.set(packet.doorMode());contact.notifyUpdate();return true;
    }
    public static void openContact(ServerPlayer player,ElevatorContactBlockEntity contact){if(!contact.isRemoved()&&player.level()==contact.getLevel()&&player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(contact.getBlockPos()))<=64)ServerPlayNetworking.send(player,new ElevatorContactEditPacket.Open(contact.getBlockPos(),contact.shortName,contact.longName,contact.doorControls.mode,contact.lastReportedCurrentFloor));}
    private static ElevatorFloorListPacket floors(AbstractContraptionEntity owner){var cabin=(ElevatorContraption)owner.getContraption();var column=ElevatorColumn.get(owner.level(),cabin.getGlobalColumn());if(column!=null)cabin.namesList=column.compileNamesList();var target=cabin.getCurrentTargetY(owner.level());return new ElevatorFloorListPacket(owner.getId(),owner.getUUID(),cabin.namesList,target==null?cabin.clientYTarget:target,cabin.arrived);}
    public static void sendFloors(AbstractContraptionEntity owner){if(!owner.hasOwnedStructure()||!(owner.getContraption() instanceof ElevatorContraption))return;var packet=floors(owner);for(var player:PlayerLookup.tracking(owner))ServerPlayNetworking.send(player,packet);}
    public static void sendFloors(AbstractContraptionEntity owner,ServerPlayer player){if(owner.hasOwnedStructure()&&owner.getContraption() instanceof ElevatorContraption)ServerPlayNetworking.send(player,floors(owner));}
    private ElevatorNetworking(){}
}
