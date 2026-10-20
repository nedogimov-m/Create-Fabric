package com.simibubi.create.content.contraptions;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;

/** Tracking snapshots, visible storage updates, and actual server-validated moving block clicks. */
public final class ContraptionClient {
    private record Incoming(ContraptionDataPacket packet,ContraptionDataPacket.Assembly assembly,long expires){}
    private record Ready(ContraptionDataPacket packet,CompoundTag data,long expires){}
    private static final Map<UUID,Incoming> incoming=new LinkedHashMap<>();
    private static final List<Ready> ready=new ArrayList<>();
    private record Dismount(int id,UUID uuid,Vec3 position,long expires){}
    private static final List<Dismount> dismounts=new ArrayList<>();
    private record Limb(float swing,long expires){}
    private static final Map<UUID,Limb> limbs=new HashMap<>();
    private static ClientLevel level;
    private static long ticks;
    private static void ensureWorld(ClientLevel current){if(level!=current){incoming.clear();ready.clear();dismounts.clear();limbs.clear();level=current;ticks=0;}}
    public static void register(){
        ContraptionCollider.clientTrainCollision=ClientPlayNetworking::send;
        ContraptionCollider.isLocalPlayer=player->player==Minecraft.getInstance().player;
        ContraptionCollider.clientMotion=(owner,motion,carry)->{var player=Minecraft.getInstance().player;if(player==null)return;double x=player.getX()-player.xo-carry.x,z=player.getZ()-player.zo-carry.z;float swing=(float)Math.min(1,Math.hypot(x,z)*4);player.walkAnimation.setSpeed(swing);ClientPlayNetworking.send(new com.simibubi.create.content.contraptions.sync.ClientMotionPacket(owner.getId(),owner.getUUID(),motion,player.onGround(),swing));};
        ClientPlayNetworking.registerGlobalReceiver(com.simibubi.create.content.contraptions.sync.LimbSwingUpdatePacket.TYPE,(packet,context)->{ensureWorld(context.client().level);if(level==null)return;var player=level.getEntity(packet.entityId());if(player instanceof net.minecraft.world.entity.player.Player remote&&remote!=context.client().player&&remote.getUUID().equals(packet.uuid())&&Float.isFinite(packet.limbSwing())){limbs.put(packet.uuid(),new Limb(packet.limbSwing(),ticks+5));remote.moveOrInterpolateTo(net.minecraft.world.entity.PositionPath.of(packet.position()));}});
        ClientPlayNetworking.registerGlobalReceiver(ContraptionDataPacket.TYPE,(packet,context)->{
            ensureWorld(context.client().level);if(level==null)return;
            try{
                if(!incoming.containsKey(packet.transfer())&&incoming.size()>=32)incoming.remove(incoming.keySet().iterator().next());
                var assembly=incoming.computeIfAbsent(packet.transfer(),key->new Incoming(packet,new ContraptionDataPacket.Assembly(packet),ticks+200));
                var data=assembly.assembly.accept(packet);if(data.isPresent()){incoming.remove(packet.transfer());if(ready.size()>=64)ready.removeFirst();ready.add(new Ready(packet,data.get(),ticks+200));apply();}
            }catch(RuntimeException failure){incoming.remove(packet.transfer());Create.LOGGER.error("Rejected contraption network data",failure);}
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{ensureWorld(client.level);ticks++;incoming.values().removeIf(value->value.expires<ticks);ready.removeIf(value->value.expires<ticks);limbs.values().removeIf(value->value.expires<ticks);apply();if(level!=null){for(var iterator=dismounts.iterator();iterator.hasNext();){var next=iterator.next();var passenger=level.getEntity(next.id);if(next.expires<ticks){iterator.remove();continue;}if(passenger!=null&&passenger.getUUID().equals(next.uuid)&&!passenger.isPassenger()){passenger.setPos(next.position);passenger.xo=next.position.x;passenger.yo=next.position.y;passenger.zo=next.position.z;passenger.setOnGround(false);iterator.remove();}}ContraptionHandler.tick(level);for(var entity:level.entitiesForRendering()){if(entity instanceof AbstractContraptionEntity contraption&&contraption.getClientRenderState() instanceof com.simibubi.create.content.contraptions.render.VirtualRenderWorld view)view.tickClientAnimations();if(entity instanceof net.minecraft.world.entity.player.Player player&&limbs.containsKey(player.getUUID()))player.walkAnimation.setSpeed(limbs.get(player.getUUID()).swing);}}});
    }
    private static void apply(){
        if(level==null)return;
        for(var iterator=ready.iterator();iterator.hasNext();){var update=iterator.next();var packet=update.packet;
            if(!(level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity entity)||!entity.getUUID().equals(packet.entityUuid()))continue;
            if(!packet.full()&&entity.getContraption()==null)continue;
            try{if(packet.full())entity.setContraption(Contraption.fromNBT(level,update.data,true));else{if(update.data.contains("ActorData"))entity.getContraption().readActorData(update.data);if(update.data.contains("Actors")){entity.getContraption().readActors(level,update.data);entity.getContraption().stalled=update.data.getBooleanOr("Stalled",entity.isStalled());}if(update.data.contains("items")||update.data.contains("fluids"))entity.getContraption().getStorage().handleSync(update.data,level.registryAccess(),entity.getContraption());if(update.data.contains("Passengers")){entity.getContraption().readSeatMapping(update.data);for(var passenger:entity.getPassengers())entity.positionRider(passenger);}if(update.data.contains("Dismount")){var tag=update.data.getCompoundOrEmpty("Dismount");var uuid=tag.read("Id",net.minecraft.core.UUIDUtil.CODEC).orElse(null);var position=tag.read("Pos",Vec3.CODEC).orElse(null);if(uuid!=null&&position!=null)dismounts.add(new Dismount(tag.getIntOr("Entity",-1),uuid,position,ticks+30));}}}
            catch(RuntimeException failure){Create.LOGGER.error("Could not apply moving contraption data",failure);}iterator.remove();
        }
    }
    public static boolean onUse(Minecraft client){
        if(client.level==null||client.player==null||client.player.isSpectator()||client.gui.screen()!=null)return false;
        var start=client.player.getEyePosition();var end=start.add(client.player.getViewVector(1).scale(client.player.blockInteractionRange()+1));
        AbstractContraptionEntity selected=null;BlockHitResult nearest=null;double distance=Double.MAX_VALUE;
        for(var entity:client.level.getEntitiesOfClass(AbstractContraptionEntity.class,new AABB(start,end).inflate(1))){var hit=entity.rayTrace(start,end,1);if(hit==null||!entity.visibleFrom(start,end,hit,1))continue;double d=start.distanceToSqr(entity.toGlobalVector(hit.getLocation(),1));if(d<distance){distance=d;selected=entity;nearest=hit;}}
        if(selected==null||nearest==null)return false;
        if(selected instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity carriage
                &&client.player.getMainHandItem().is(com.simibubi.create.AllItems.WRENCH)&&!client.player.isShiftKeyDown())
            return com.simibubi.create.content.trains.entity.TrainRelocatorClient.carriageWrenched(selected.toGlobalVector(nearest.getLocation(),1),carriage);
        boolean seat=selected.getContraption().getSeats().contains(nearest.getBlockPos());if(seat&&client.player.isPassenger())return false;if(!seat&&com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(selected.getContraption().getBlocks().get(nearest.getBlockPos()).state())==null&&!selected.getContraption().getStorage().getInteractablePositions().contains(nearest.getBlockPos()))return false;
        var interaction=com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(selected.getContraption().getBlocks().get(nearest.getBlockPos()).state());
        if(interaction instanceof com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovingInteraction)interaction.handlePlayerInteraction(client.player,InteractionHand.MAIN_HAND,nearest.getBlockPos(),selected);
        ClientPlayNetworking.send(new ContraptionInteractionPacket(selected.getId(),selected.getUUID(),nearest.getBlockPos(),InteractionHand.MAIN_HAND));return true;
    }
    private ContraptionClient(){}
}
