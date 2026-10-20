package com.simibubi.create.content.trains.track;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

/** Native input and feedback only. Every real deletion/refund remains the server's current-ray operation. */
public final class CurvedTrackInteraction {
    private static TrackCurveSelection.Hit attacking;
    private static float progress;
    private static int ticks;
    public static float progress(){return progress;}
    private static boolean same(TrackCurveSelection.Hit a,TrackCurveSelection.Hit b){return a!=null&&b!=null&&a.owner().equals(b.owner())&&a.target().equals(b.target());}
    private static void send(TrackCurveSelection.Hit selection,CurvedTrackDestroyPacket.Action action){ClientPlayNetworking.send(new CurvedTrackDestroyPacket(selection.owner(),selection.target(),selection.segment(),action));}
    private static TrackCurveSelection.Hit selected(Minecraft client){
        var selected=TrackBlockOutline.result();if(selected==null||client.level==null||client.player==null||client.player.isSpectator()||!client.player.mayBuild())return null;
        if(!(LoadedLevelAccess.blockEntity(client.level,selected.owner()) instanceof TrackBlockEntity endpoint)||!endpoint.getConnections().containsKey(selected.target()))return null;return selected;
    }
    public static boolean onUse(Minecraft client){
        var selected=selected(client);if(selected==null)return false;
        if(client.player.getMainHandItem().getItem() instanceof TrackBlockItem){client.player.sendOverlayMessage(Component.translatable("create.track.turn_start"));client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);return true;}
        if(!client.player.isShiftKeyDown()||!client.player.getMainHandItem().is(AllItems.WRENCH))return false;
        send(selected,CurvedTrackDestroyPacket.Action.WRENCH);stop(false);client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);return true;
    }
    public static boolean onAttack(Minecraft client){var selected=selected(client);if(selected==null)return false;stop(true);attacking=selected;progress=0;ticks=0;send(selected,CurvedTrackDestroyPacket.Action.BEGIN);return true;}
    public static boolean suppressAttack(Minecraft client,boolean active){if(!active){stop(true);return false;}return attacking!=null||selected(client)!=null;}
    private static void stop(boolean notify){if(attacking!=null&&notify&&Minecraft.getInstance().level!=null)send(attacking,CurvedTrackDestroyPacket.Action.ABORT);attacking=null;progress=0;ticks=0;}
    private static void tick(Minecraft client){
        if(attacking==null)return;var selected=selected(client);
        if(client.gui.screen()!=null||client.player==null||!client.options.keyAttack.isDown()||!same(attacking,selected)){stop(true);return;}
        attacking=selected;send(selected,CurvedTrackDestroyPacket.Action.HOLD);ticks++;
        if(ticks%4==0&&LoadedLevelAccess.blockEntity(client.level,selected.owner()) instanceof TrackBlockEntity endpoint){
            var state=endpoint.getBlockState();var sound=state.getSoundType();client.level.playLocalSound(net.minecraft.core.BlockPos.containing(selected.point()),sound.getHitSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/8,sound.getPitch()*.5f,false);
            var point=selected.point();client.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),point.x,point.y,point.z,0,0,0);client.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);
        }
    }
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(CurvedTrackInteraction::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->stop(false));
        ClientPlayNetworking.registerGlobalReceiver(CurvedTrackDestroyPacket.Progress.TYPE,(packet,context)->{
            if(attacking==null||!attacking.owner().equals(packet.pos())||!attacking.target().equals(packet.targetPos())||!Float.isFinite(packet.progress()))return;
            progress=Math.clamp(packet.progress(),0,1);if(packet.removed())stop(false);
        });
    }
    private CurvedTrackInteraction(){}
}
