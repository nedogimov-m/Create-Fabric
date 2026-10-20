package com.simibubi.create.content.equipment.zapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.EquipmentClient;
import com.simibubi.create.content.equipment.zapper.terrainzapper.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
public final class ZapperClient {
    private static final Map<LevelRenderState,WorldshaperRenderHandler.Frame> FRAMES=new WeakHashMap<>();
    public static WorldshaperRenderHandler.Frame latest;
    public static void register(){ZapperItem.OPEN_SCREEN=(stack,hand)->Minecraft.getInstance().gui.setScreen(new WorldshaperScreen(stack,hand));ZapperItem.DONT_ANIMATE=EquipmentClient.ZAPPER::dontAnimateItem;WorldshaperItemRenderer.INSTANCE.register("handheld_worldshaper");ZapperBlockPortrait.register();ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.level!=null&&!client.isPaused()){EquipmentClient.ZAPPER.tick();WorldshaperRenderHandler.tick();}});ClientPlayNetworking.registerGlobalReceiver(ZapperBeamPacket.TYPE,(packet,context)->{var client=context.client();if(client.getCameraEntity()==null||client.getCameraEntity().position().distanceTo(packet.location)>100)return;EquipmentClient.ZAPPER.addBeam(new ZapperRenderHandler.LaserBeam(packet.location,packet.target));if(packet.self)EquipmentClient.ZAPPER.shoot(packet.hand,packet.location);else EquipmentClient.ZAPPER.playSound(packet.hand,packet.location);});ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{EquipmentClient.ZAPPER.reset();WorldshaperRenderHandler.clear();FRAMES.clear();latest=null;});LevelExtractionEvents.END_EXTRACTION.register(context->{latest=WorldshaperRenderHandler.extract(EquipmentClient.ZAPPER.extract());FRAMES.put(context.levelState(),latest);});LevelRenderEvents.COLLECT_SUBMITS.register(context->{var state=FRAMES.get(context.levelState());if(state==null)return;var camera=context.levelState().cameraRenderState.pos;var pose=new PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);state.submit(pose,context.submitNodeCollector());});}
    private ZapperClient(){}
}
