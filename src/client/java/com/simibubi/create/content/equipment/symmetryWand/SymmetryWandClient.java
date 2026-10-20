package com.simibubi.create.content.equipment.symmetryWand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.symmetryWand.mirror.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SymmetryWandClient {
    public record Marker(Vec3 position,Matrix4f transform,PartialModelRenderState model,int light){}
    public record Frame(List<Marker> markers){public Frame{markers=List.copyOf(markers);}}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest=new Frame(List.of());private static int ticks;
    public static Frame latest(){return latest;}
    public static Frame extract(Minecraft client,float partial){
        var output=new ArrayList<Marker>();if(client.player==null||client.level==null)return new Frame(output);float time=client.level.getGameTime()+partial;
        for(int slot=0;slot<Inventory.getSelectionSize();slot++){var stack=client.player.getInventory().getItem(slot);if(!(stack.getItem() instanceof SymmetryWandItem)||!SymmetryWandItem.isEnabled(stack))continue;var mirror=SymmetryWandItem.getMirror(stack);if(mirror instanceof EmptyMirror)continue;var pos=BlockPos.containing(mirror.getPosition());var pose=new PoseStack();pose.translate(0,Mth.sin(time/16)/5+.2,0);SymmetryMirrorClient.applyModelTransform(mirror,pose);output.add(new Marker(Vec3.atLowerCornerOf(pos),new Matrix4f(pose.last().pose()),SymmetryMirrorClient.extract(mirror),net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,pos)));}
        return new Frame(output);
    }
    private static void tick(Minecraft client){if(client.player==null||client.level==null||client.isPaused()||++ticks%10!=0)return;var random=client.level.getRandom();for(int slot=0;slot<Inventory.getSelectionSize();slot++){var stack=client.player.getInventory().getItem(slot);if(!(stack.getItem() instanceof SymmetryWandItem)||!SymmetryWandItem.isEnabled(stack))continue;var mirror=SymmetryWandItem.getMirror(stack);if(mirror instanceof EmptyMirror)continue;var point=mirror.getPosition().add(.5+(random.nextDouble()-.5)*.3,.25,.5+(random.nextDouble()-.5)*.3);client.level.addParticle(ParticleTypes.END_ROD,point.x,point.y,point.z,0,random.nextDouble()/8,0);}}
    public static void drawEffect(BlockPos from,BlockPos to){
        var level=Minecraft.getInstance().level;if(level==null)return;var start=Vec3.atCenterOf(from);var diff=Vec3.atCenterOf(to).subtract(start);double length=diff.length();if(length<.001)return;var step=diff.normalize().scale(.8f);int count=(int)(length/step.length());var random=level.getRandom();for(int i=3;i<count-1;i++){var pos=start.add(step.scale(i));level.addParticle(new DustParticleOptions(0xFFFFFF,1),pos.x,pos.y,pos.z,0,random.nextDouble()*-40,0);}for(int index:new int[]{2,count}){var pos=start.add(step.scale(index));level.addParticle(ParticleTypes.END_ROD,pos.x,pos.y,pos.z,0,random.nextDouble()/32,0);}
    }
    public static void register(){
        SymmetryMirrorClient.register();SymmetryWandPortrait.register();SymmetryWandItemRenderer.INSTANCE.register("wand_of_symmetry");
        ClientPlayNetworking.registerGlobalReceiver(SymmetryWandNetworking.Open.TYPE,(packet,context)->{var client=context.client();if(client.player!=null&&client.player.getItemInHand(packet.hand()).getItem() instanceof SymmetryWandItem)client.gui.setScreen(new SymmetryWandScreen(client.player.getItemInHand(packet.hand()),packet.hand(),packet.mirror(),packet.session()));});
        ClientPlayNetworking.registerGlobalReceiver(SymmetryEffectPacket.TYPE,(packet,context)->{var player=context.player();if(player.position().distanceTo(Vec3.atLowerCornerOf(packet.mirror()))<=100)for(var position:packet.positions())drawEffect(packet.mirror(),position);});
        ClientTickEvents.END_CLIENT_TICK.register(SymmetryWandClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{FRAMES.clear();latest=new Frame(List.of());ticks=0;});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var client=Minecraft.getInstance();latest=extract(client,client.getDeltaTracker().getGameTimeDeltaPartialTick(false));FRAMES.put(context.levelState(),latest);});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame==null)return;var camera=context.levelState().cameraRenderState.pos;for(var marker:frame.markers){var pose=new PoseStack();pose.translate(marker.position.x-camera.x,marker.position.y-camera.y,marker.position.z-camera.z);pose.mulPose(marker.transform);marker.model.submit(pose,context.submitNodeCollector(),marker.light,OverlayTexture.NO_OVERLAY,0);}});
    }
    private SymmetryWandClient(){}
}
