package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.goggles.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.gauge.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;

final class GaugeRenderingTest {
    private static final BlockPos SPEED=new BlockPos(600,4,5),STRESS=new BlockPos(612,4,5),CRANK=new BlockPos(614,4,5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var oldHud=new AtomicBoolean();context.runOnClient(client->{oldHud.set(client.gui.hud.isHidden());if(oldHud.get())client.gui.hud.toggle();});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 608 7 12");context.waitFor(client->Math.abs(client.player.getX()-608.5)<0.1&&client.level.isLoaded(CRANK),500);server.runCommand("fill 594 3 1 626 3 12 minecraft:smooth_quartz");
        server.runCommand("setblock 599 4 5 create:creative_motor[facing=east]");server.runCommand("setblock 600 4 5 create:speedometer[facing=up,axis_along_first=true]");server.runCommand("setblock 601 4 5 create:shaft[axis=x]");
        server.runCommand("setblock 611 4 5 create:speedometer[facing=up,axis_along_first=true]");server.runCommand("setblock 612 4 5 create:stressometer[facing=up,axis_along_first=true]");server.runCommand("setblock 613 4 5 create:mechanical_press[facing=east]");server.runCommand("setblock 614 4 5 create:hand_crank[facing=east]");
        // Every orientation must bake into a visible base and a separate shaft/head snapshot.
        int index=0;for(var facing:Direction.values())for(boolean first:new boolean[]{false,true})server.runCommand("setblock "+(596+index++)+" 4 2 create:"+(first?"stressometer":"speedometer")+"[facing="+facing.getSerializedName()+",axis_along_first="+first+"]");
        server.runCommand("gamemode creative @p");server.runCommand("item replace entity @p armor.head with create:goggles");server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");server.runCommand("tp @p 600 4 9");
        context.waitFor(client->Math.abs(client.player.getX()-600.5)<0.1&&GogglesItem.isWearingGoggles(client.player)&&client.level.getBlockEntity(SPEED) instanceof SpeedGaugeBlockEntity gauge&&gauge.getSpeed()==16,200);
        look(context,SPEED);context.waitTicks(20);check(context,SPEED,2,0.2f,"16");context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-speedometer-slow");
        server.runOnServer(ignored->{var motor=(KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(SPEED.west());for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);motor.adjustGeneratedSpeed(true);});
        context.waitFor(client->((SpeedGaugeBlockEntity)client.level.getBlockEntity(SPEED)).getSpeed()==-256,200);context.waitTicks(20);check(context,SPEED,2,1,"256");context.takeScreenshot("create-vulkan-speedometer-max-reversed");
        context.runOnClient(client->{for(int i=0;i<12;i++){var pos=new BlockPos(596+i,4,2);var gauge=(GaugeBlockEntity)client.level.getBlockEntity(pos);var extracted=(KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(gauge,0.5f,null,false);var block=(GaugeBlock)gauge.getBlockState().getBlock();int heads=gauge.getBlockState().getValue(GaugeBlock.FACING).getAxis()==Direction.Axis.Y?2:1;
            if(extracted==null||extracted.model.isEmpty()||extracted.gaugeHead.mesh==null||extracted.gaugeDial.mesh==null||extracted.gaugeFaces.size()!=heads)throw new AssertionError("Incomplete gauge orientation "+gauge.getBlockState());
            var item=new net.minecraft.client.renderer.item.ItemStackRenderState();client.getItemModelResolver().updateForLiving(item,new net.minecraft.world.item.ItemStack(block),net.minecraft.world.item.ItemDisplayContext.GUI,client.player);if(item.isEmpty()||item.getModelBoundingBox().getXsize()<=0)throw new AssertionError("Missing gauge item model");
        }});
        server.runCommand("setblock 600 4 6 minecraft:stone");context.waitFor(client->client.level.getBlockState(SPEED.south()).is(net.minecraft.world.level.block.Blocks.STONE),200);check(context,SPEED,1,0.9f,"256");server.runCommand("setblock 600 4 6 minecraft:air");context.waitFor(client->client.level.getBlockState(SPEED.south()).isAir(),200);
        var oldMesh=new AtomicReference<Object>();context.runOnClient(client->oldMesh.set(((KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(SPEED),0.5f,null,false)).gaugeDial.mesh));
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitTicks(5);check(context,SPEED,2,0.9f,"256");context.runOnClient(client->{if(((KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(SPEED),0.5f,null,false)).gaugeDial.mesh==oldMesh.get())throw new AssertionError("Gauge mesh retained stale resources");});
        server.runCommand("tp @p 612 4 9");context.waitFor(client->Math.abs(client.player.getX()-612.5)<0.1,200);look(context,STRESS);crank(context,world);check(context,STRESS,2,0.7f,"100%","256");context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-stressometer-full-load");
        server.runCommand("setblock 610 4 5 create:mechanical_press[facing=east]");crank(context,world);check(context,STRESS,2,0.8f,"200%","-256");context.runOnClient(client->{var gauge=(StressGaugeBlockEntity)client.level.getBlockEntity(STRESS);if(!gauge.isOverstressed()||gauge.getComparatorSignal()!=15||gauge.getSpeed()!=0)throw new AssertionError("Client overload state/comparator not synchronized");});context.takeScreenshot("create-vulkan-stressometer-overloaded");
        context.waitTicks(20);context.waitFor(client->((GaugeBlockEntity)client.level.getBlockEntity(STRESS)).dialTarget==0,200);check(context,STRESS,2,0,"No Rotation");context.takeScreenshot("create-vulkan-stressometer-stopped");
        server.runCommand("gamemode spectator @p");context.runOnClient(client->{if(client.gui.hud.isHidden()!=oldHud.get())client.gui.hud.toggle();});
    }
    private static void crank(ClientGameTestContext context,TestSingleplayerContext world){for(int i=0;i<10;i++){world.getServer().runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(CRANK)).turnCrank(false));context.waitTicks(2);}}
    private static void look(ClientGameTestContext context,BlockPos pos){context.getInput().lookAt(pos);context.waitFor(client->client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(pos),200);}
    private static void check(ClientGameTestContext context,BlockPos pos,int heads,float minimum,String... expected){context.runOnClient(client->{
        var gauge=(GaugeBlockEntity)client.level.getBlockEntity(pos);var state=(KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(gauge,0.5f,null,false);
        if(state==null||state.model.isEmpty()||state.gaugeHead.mesh==null||state.gaugeDial.mesh==null||state.gaugeFaces.size()!=heads||!Float.isFinite(state.gaugeProgress)||state.gaugeProgress<minimum)throw new AssertionError("Gauge mesh/dial/faces incomplete at "+pos+": "+(state==null?"no state":"shaft="+!state.model.isEmpty()+", head="+(state.gaugeHead.mesh!=null)+", dial="+(state.gaugeDial.mesh!=null)+", faces="+state.gaugeFaces+", progress="+state.gaugeProgress+", target="+gauge.dialTarget));
        var info=GoggleOverlayRenderer.collect(client.level,client.player,pos);if(info==null)throw new AssertionError("No gauge goggles tooltip");var text=info.lines().stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "));for(var part:expected)if(!text.contains(part))throw new AssertionError("Missing gauge tooltip "+part+" in "+text);
        if(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(pos)) {
            var extracted=new net.minecraft.client.renderer.state.gui.GuiRenderState();var graphics=new net.minecraft.client.gui.GuiGraphicsExtractor(client,extracted,0,0);GoggleOverlayRenderer.renderOverlay(graphics,client.getDeltaTracker());
            extracted.forEachText(line->{var bounds=line.bounds();if(bounds!=null&&(bounds.top()<0||bounds.bottom()>graphics.guiHeight()))throw new AssertionError("Gauge HUD text exceeds screen: "+bounds+", height="+graphics.guiHeight());});
        }
    });}
}
