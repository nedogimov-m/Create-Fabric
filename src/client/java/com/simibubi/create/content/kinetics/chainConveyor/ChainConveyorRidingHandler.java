package com.simibubi.create.content.kinetics.chainConveyor;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Source elastic skyhook movement, with a server-issued session and path corrections. */
public final class ChainConveyorRidingHandler {
    private static ChainConveyorRidingState path;
    private static ClientLevel level;
    private static int session,rejectedThrough,age,ticks,lastAck;
    private static Set<UUID> hanging=Set.of();
    public static ChainConveyorRidingState state(){return path;}
    public static boolean active(){return path!=null;}
    public static int session(){return session;}
    public static boolean hanging(UUID player){return hanging.contains(player);}
    public static void embark(ChainConveyorBlockEntity conveyor,ChainConveyorInteraction.Selection selected){
        var client=Minecraft.getInstance();if(!ChainConveyorRidingState.eligible(client.player))return;
        if(path!=null)stop(true);path=new ChainConveyorRidingState(conveyor.getBlockPos(),selected.connection(),selected.position(),conveyor.isReversed());level=client.level;session=0;age=0;lastAck=ticks;
        client.player.sendOverlayMessage(Component.translatable("mount.onboard",client.options.keyShift.getTranslatedKeyMessage()));client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHAIN_HIT,1,.5f));
    }
    private static void update(ServerChainConveyorHandler.Update packet){
        var client=Minecraft.getInstance();if(client.level==null||client.player==null)return;
        if(!packet.active()){if(packet.session()==session){rejectedThrough=Math.max(rejectedThrough,session);stop(false);}return;}
        if(packet.session()<=rejectedThrough)return;
        var next=new ChainConveyorRidingState(packet.anchor(),packet.connection(),packet.position(),packet.reversed());next.catchingUp=packet.catchingUp();
        boolean changed=path==null||session!=packet.session();
        if(changed||!path.anchor.equals(next.anchor)||!Objects.equals(path.connection,next.connection)||Math.abs(path.connection==null?net.minecraft.util.Mth.wrapDegrees(path.position-next.position):path.position-next.position)>(path.connection==null?45:.75))path=next;
        else path.catchingUp=Math.min(path.catchingUp,next.catchingUp);
        session=packet.session();level=client.level;lastAck=ticks;
    }
    public static void stop(boolean notify){
        if(path==null)return;var client=Minecraft.getInstance();if(notify&&session>0&&client.getConnection()!=null)ClientPlayNetworking.send(new ServerChainConveyorHandler.Heartbeat(session,true));
        rejectedThrough=Math.max(rejectedThrough,session);path=null;level=null;session=0;
        if(client.player!=null)client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHAIN_HIT,.75f,.35f));
    }
    private static void tick(Minecraft client){
        ticks++;if(path==null)return;if(client.player==null||client.level!=level){stop(false);return;}if(client.isPaused())return;
        if(!ChainConveyorRidingState.eligible(client.player)||session==0&&++age>20||session>0&&ticks-lastAck>40||!path.advance(client.level,client.player.getLookAngle(),com.simibubi.create.foundation.utility.ServerSpeedProvider.get())){stop(true);return;}
        var target=path.target(client.level);if(target==null||!path.inReach(client.player,target)){stop(true);return;}
        if(path.catchingUp>0)path.catchingUp--;
        client.player.setDeltaMovement(ChainConveyorRidingState.spring(client.player.getDeltaMovement(),target.subtract(ChainConveyorRidingState.grip(client.player))));
        if(session>0&&ticks%10==0)ClientPlayNetworking.send(new ServerChainConveyorHandler.Heartbeat(session,false));
    }
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(ServerChainConveyorHandler.Update.TYPE,(packet,context)->update(packet));
        ClientPlayNetworking.registerGlobalReceiver(ServerChainConveyorHandler.Hanging.TYPE,(packet,context)->hanging=Set.copyOf(packet.players()));
        ClientTickEvents.END_CLIENT_TICK.register(ChainConveyorRidingHandler::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{stop(false);hanging=Set.of();rejectedThrough=0;});
    }
    private ChainConveyorRidingHandler(){}
}
