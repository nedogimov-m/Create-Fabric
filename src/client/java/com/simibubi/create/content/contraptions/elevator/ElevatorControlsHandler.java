package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.contraptionControls.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.*;

/** Actual world-space controls display/scroll/click selection, with id+UUID server target admission. */
public final class ElevatorControlsHandler {
    public record Hover(AbstractContraptionEntity owner,MovementContext context,BlockHitResult hit){}
    private record RequestState(WeakReference<Contraption> structure,long tick,boolean confirmed){}
    private static final Map<AbstractContraptionEntity,RequestState> REQUESTS=new WeakHashMap<>();
    private static Hover hovered;
    public static Hover hovered(){return hovered;}
    private static Hover find(Minecraft client){
        if(client.player==null||client.level==null||client.player.isSpectator()||client.gui.screen()!=null)return null;var start=client.player.getEyePosition();var end=start.add(client.player.getViewVector(1).scale(client.player.blockInteractionRange()+1));AbstractContraptionEntity selected=null;BlockHitResult hit=null;double distance=Double.MAX_VALUE;
        for(var owner:client.level.getEntitiesOfClass(AbstractContraptionEntity.class,new AABB(start,end).inflate(1))){var candidate=owner.rayTrace(start,end,1);if(candidate==null||!owner.visibleFrom(start,end,candidate,1))continue;double d=start.distanceToSqr(owner.toGlobalVector(candidate.getLocation(),1));if(d<distance){selected=owner;hit=candidate;distance=d;}}
        if(selected==null||!(selected.getContraption() instanceof ElevatorContraption)||hit==null)return null;var actor=selected.getContraption().getActorAt(hit.getBlockPos());if(actor==null||actor.right==null||!actor.left.state().is(AllBlocks.CONTRAPTION_CONTROLS)||!ContraptionControlsBlock.onFilter(actor.left.state(),hit.getBlockPos(),hit.getLocation()))return null;return new Hover(selected,actor.right,hit);
    }
    public static boolean onScroll(double delta){
        var client=Minecraft.getInstance();if(delta==0||!client.mouseHandler.isMouseGrabbed())return false;var hover=find(client);if(hover==null)return false;hovered=hover;var cabin=(ElevatorContraption)hover.owner().getContraption();if(!(hover.context().temporaryData instanceof ContraptionControlsMovement.ElevatorFloorSelection))hover.context().temporaryData=new ContraptionControlsMovement.ElevatorFloorSelection();var selection=(ContraptionControlsMovement.ElevatorFloorSelection)hover.context().temporaryData;int previous=selection.currentIndex;selection.currentIndex+=(int)(delta>0?Math.ceil(delta):Math.floor(delta));ContraptionControlsMovement.tickFloorSelection(selection,cabin);
        if(previous!=selection.currentIndex&&!cabin.namesList.isEmpty()){float pitch=1+.5f*selection.currentIndex/cabin.namesList.size();client.level.playLocalSound(hover.owner().toGlobalVector(hover.hit().getLocation(),1).x,hover.owner().toGlobalVector(hover.hit().getLocation(),1).y,hover.owner().toGlobalVector(hover.hit().getLocation(),1).z,AllSoundEvents.SCROLL_VALUE,SoundSource.PLAYERS,.124f,pitch,false);}return true;
    }
    private static void request(AbstractContraptionEntity owner){
        var client=Minecraft.getInstance();if(client.level==null||owner.getContraption()==null)return;long tick=client.level.getGameTime();var previous=REQUESTS.get(owner);if(previous!=null&&previous.structure().get()==owner.getContraption()&&(previous.confirmed()||tick-previous.tick()<40))return;REQUESTS.put(owner,new RequestState(new WeakReference<>(owner.getContraption()),tick,false));ClientPlayNetworking.send(new ElevatorFloorListPacket.RequestFloorList(owner.getId(),owner.getUUID()));
    }
    private static void select(AbstractContraptionEntity owner,net.minecraft.core.BlockPos pos){var actor=owner.getContraption().getActorAt(pos);if(!(owner.getContraption() instanceof ElevatorContraption cabin)||actor==null||actor.right==null||!(actor.right.temporaryData instanceof ContraptionControlsMovement.ElevatorFloorSelection selection)||cabin.namesList.isEmpty()||selection.targetYEqualsSelection||cabin.isTargetUnreachable(selection.currentTargetY))return;ClientPlayNetworking.send(new ElevatorTargetFloorPacket(owner.getId(),owner.getUUID(),selection.currentTargetY));}
    public static void register(){
        ElevatorNetworking.setClientHandlers(ElevatorControlsHandler::request,ElevatorControlsHandler::select);
        ClientPlayNetworking.registerGlobalReceiver(ElevatorFloorListPacket.TYPE,(packet,context)->{
            var client=context.client();if(client.level==null||!(client.level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity owner)||!owner.getUUID().equals(packet.owner())||!(owner.getContraption() instanceof ElevatorContraption cabin))return;boolean changed=!cabin.namesList.equals(packet.floors());cabin.namesList=packet.floors();if(cabin.clientYTarget!=packet.targetY())cabin.setClientYTarget(packet.targetY());else if(changed)cabin.syncControlDisplays();cabin.arrived=packet.arrived();REQUESTS.put(owner,new RequestState(new WeakReference<>(cabin),client.level.getGameTime(),true));
        });
        ClientPlayNetworking.registerGlobalReceiver(ElevatorContactEditPacket.Open.TYPE,(packet,context)->{var client=context.client();if(client.level!=null&&client.player!=null&&client.level.getBlockEntity(packet.pos()) instanceof ElevatorContactBlockEntity)client.gui.setScreen(new ElevatorContactScreen(packet));});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{REQUESTS.clear();hovered=null;});ClientTickEvents.END_CLIENT_TICK.register(client->hovered=find(client));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("elevator_floor_selection"),(graphics,delta)->{
            var client=Minecraft.getInstance();var hover=hovered;if(hover==null||client.gui.hud.isHidden()||client.gui.screen()!=null||!(hover.context().temporaryData instanceof ContraptionControlsMovement.ElevatorFloorSelection selection))return;var cabin=(ElevatorContraption)hover.owner().getContraption();int color=cabin.isTargetUnreachable(selection.currentTargetY)?0xFFEA5C2B:0xFFFBDC7D;graphics.centeredText(client.font,Component.literal(selection.currentShortName+"  "+selection.currentLongName),graphics.guiWidth()/2,graphics.guiHeight()-84,color);
        });
    }
    private ElevatorControlsHandler(){}
}
