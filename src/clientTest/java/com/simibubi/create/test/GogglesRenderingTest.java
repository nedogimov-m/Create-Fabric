package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.goggles.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.*;
import com.simibubi.create.infrastructure.config.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import java.util.concurrent.atomic.*;

final class GogglesRenderingTest {
    private static final BlockPos TANK=new BlockPos(520,4,5),BASIN=new BlockPos(526,4,5),PRESS=new BlockPos(533,4,5),OVERLOAD=new BlockPos(539,4,5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var oldHud=new AtomicBoolean();context.runOnClient(client->{oldHud.set(client.gui.hud.isHidden());if(oldHud.get())client.gui.hud.toggle();});
        server.runCommand("tp @p 522 4 9");context.waitFor(client->Math.abs(client.player.getX()-522.5)<0.1&&client.level.isLoaded(new BlockPos(540,3,5)),500);
        server.runCommand("fill 512 3 1 543 3 12 minecraft:smooth_quartz");server.runCommand("fill 520 4 5 520 5 5 create:fluid_tank");server.runCommand("setblock 526 4 5 create:basin");
        server.runCommand("setblock 532 4 5 create:creative_motor[facing=east]");server.runCommand("setblock 533 4 5 create:mechanical_press[facing=east]");
        server.runCommand("setblock 538 4 5 create:mechanical_press[facing=east]");server.runCommand("setblock 539 4 5 create:mechanical_press[facing=east]");server.runCommand("setblock 540 4 5 create:hand_crank[facing=east]");
        server.runCommand("gamemode creative @p");server.runCommand("item replace entity @p weapon.mainhand with create:goggles");
        context.waitFor(client->client.player.getMainHandItem().is(AllItems.GOGGLES)&&!client.player.isSpectator(),200);context.getInput().lookAt(new BlockPos(522,18,9));context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->GogglesItem.isWearingGoggles(client.player),200);
        var equipped=new AtomicBoolean();for(int i=0;i<100&&!equipped.get();i++){context.waitTicks(1);server.runOnServer(ignored->equipped.set(GogglesItem.isWearingGoggles(world.getConnection().getServerLevel().players().getFirst())));}
        if(!equipped.get()){server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().players().getFirst();Create.LOGGER.error("Goggles use packet: player={}, hand={}, head={}, mode={}",player.getName(),player.getMainHandItem(),player.getItemBySlot(EquipmentSlot.HEAD),player.gameMode.getGameModeForPlayer());});throw new AssertionError("Native use packet did not equip goggles on server");}
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){
            ((FluidTankBlockEntity)level.getBlockEntity(TANK)).storage().insert(FluidVariant.of(AllFluids.HONEY),4*FluidConstants.BUCKET,tx);
            var basin=(BasinBlockEntity)level.getBlockEntity(BASIN);basin.inventory.automation.insert(ItemVariant.of(Items.APPLE),3,tx);basin.fluids.automation.insert(FluidVariant.of(AllFluids.MILK),20250,tx);tx.commit();}
        });
        context.runOnClient(client->{var head=new net.minecraft.client.renderer.item.ItemStackRenderState();var gui=new net.minecraft.client.renderer.item.ItemStackRenderState();client.getItemModelResolver().updateForLiving(head,new ItemStack(AllItems.GOGGLES),ItemDisplayContext.HEAD,client.player);client.getItemModelResolver().updateForLiving(gui,new ItemStack(AllItems.GOGGLES),ItemDisplayContext.GUI,client.player);
            if(head.isEmpty()||gui.isEmpty()||head.getModelBoundingBox().getZsize()<=0.1||head.getModelBoundingBox().equals(gui.getModelBoundingBox()))throw new AssertionError("Head context did not select 3D goggles model: head="+head.getModelBoundingBox()+", gui="+gui.getModelBoundingBox()+", model="+client.getModelManager().getItemModel(Create.asResource("goggles")));});
        look(context,TANK);context.waitTicks(12);assertOverlay(context,TANK,"4,000","16,000");context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-goggles-fluid-tank");
        server.runCommand("tp @p 527 4 9");context.waitFor(client->Math.abs(client.player.getX()-527.5)<0.1,200);look(context,BASIN);context.waitTicks(8);assertOverlay(context,BASIN,"x3","250");context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-goggles-basin-contents");
        server.runCommand("tp @p 534 4 9");context.waitFor(client->Math.abs(client.player.getX()-534.5)<0.1,200);look(context,PRESS);context.waitTicks(8);assertOverlay(context,PRESS,"128");context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-goggles-kinetic-stress");
        server.runCommand("item replace entity @p armor.head with minecraft:air");context.waitFor(client->!GogglesItem.isWearingGoggles(client.player),200);context.waitTicks(2);
        context.runOnClient(client->{if(GoggleOverlayRenderer.collect(client.level,client.player,PRESS)!=null)throw new AssertionError("Goggle-only stats visible without goggles");});
        server.runCommand("tp @p 540 4 9");context.waitFor(client->Math.abs(client.player.getX()-540.5)<0.1,200);look(context,OVERLOAD);
        for(int i=0;i<6;i++){server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(540,4,5))).turnCrank(false));context.waitTicks(3);}
        assertOverlay(context,OVERLOAD,"Overstressed");context.takeScreenshot("create-vulkan-goggles-overstress-without-goggles");
        config(context);burner(context,world);boilerAndProxies(context,world);wornModel(context,world);
        server.runCommand("gamemode spectator @p");context.runOnClient(client->{if(client.gui.hud.isHidden()!=oldHud.get())client.gui.hud.toggle();});
    }
    private static void look(ClientGameTestContext context,BlockPos pos){context.getInput().lookAt(pos);context.waitFor(client->client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(pos),200);}
    private static void assertOverlay(ClientGameTestContext context,BlockPos pos,String... expected){context.runOnClient(client->{
        var info=GoggleOverlayRenderer.collect(client.level,client.player,pos);if(info==null)throw new AssertionError("No inspected information at "+pos);var text=info.lines().stream().map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "));
        for(var part:expected)if(!text.contains(part))throw new AssertionError("Missing overlay information: "+part+" in "+text);
        var extracted=new net.minecraft.client.renderer.state.gui.GuiRenderState();var graphics=new net.minecraft.client.gui.GuiGraphicsExtractor(client,extracted,0,0);GoggleOverlayRenderer.renderOverlay(graphics,client.getDeltaTracker());var count=new AtomicInteger();extracted.forEachText(line->count.incrementAndGet());if(count.get()==0)throw new AssertionError("HUD extractor did not receive goggle text");
        var layout=GoggleOverlayRenderer.layout(client,info,120,100,Integer.MAX_VALUE,Integer.MIN_VALUE);if(layout.x()<0||layout.x()+layout.width()>120||layout.y()<0)throw new AssertionError("Overlay exceeds window after offset clamp");
    });}
    private static void config(ClientGameTestContext context){
        var original=new AtomicReference<java.nio.file.Path>();var temporary=new AtomicReference<java.nio.file.Path>();
        context.runOnClient(client->{try{var settings=GoggleOverlaySettings.INSTANCE;original.set(settings.path());var path=java.nio.file.Files.createTempDirectory("create-goggles-test").resolve("client.json");temporary.set(path);java.nio.file.Files.writeString(path,"{\"unrelated\":true,\"overlayOffsetX\":5,\"overlayOffsetY\":6}");settings.load(path);
            var config=AllConfigs.client();if(settings.overlayOffsetX!=5||settings.overlayOffsetY!=6||config.overlayOffsetX.get()!=5||config.overlayOffsetY.get()!=6)throw new AssertionError("Actual native CLIENT store did not load the original overlay aliases");
            net.fabricmc.fabric.api.client.command.v2.ClientCommands.getActiveDispatcher().execute("create overlay",(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)client.player.connection.getSuggestionsProvider());
        }catch(Exception exception){throw new AssertionError(exception);}});
        context.waitFor(client->client.gui.screen() instanceof GoggleConfigScreen,200);context.takeScreenshot("create-vulkan-goggles-overlay-position-screen");
        var click=context.computeOnClient(client->{var window=client.getWindow();return new net.minecraft.world.phys.Vec2(20f*window.getScreenWidth()/window.getGuiScaledWidth(),35f*window.getScreenHeight()/window.getGuiScaledHeight());});
        context.getInput().setCursorPos(click.x,click.y);context.waitTicks(2);context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);context.waitTicks(2);context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,200);
        context.runOnClient(client->{
            try{var settings=GoggleOverlaySettings.INSTANCE;var config=AllConfigs.client();var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(temporary.get())).getAsJsonObject();var canonicalX=NativeConfigStore.find(saved,config.overlayOffsetX.path());var canonicalY=NativeConfigStore.find(saved,config.overlayOffsetY.path());
                if(canonicalX==null||canonicalY==null||!saved.get("unrelated").getAsBoolean()||canonicalX.getAsInt()==5||canonicalX.getAsInt()!=settings.overlayOffsetX||canonicalY.getAsInt()!=settings.overlayOffsetY||config.overlayOffsetX.get()!=canonicalX.getAsInt()||config.overlayOffsetY.get()!=canonicalY.getAsInt())throw new AssertionError("Overlay screen did not save/preserve preferences: "+saved);
                if(saved.get("overlayOffsetX").getAsInt()!=5||saved.get("overlayOffsetY").getAsInt()!=6)throw new AssertionError("Native canonical save changed the preserved original flat overlay aliases");
                int savedX=canonicalX.getAsInt(),savedY=canonicalY.getAsInt();settings.load(temporary.get());var reloaded=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(temporary.get())).getAsJsonObject();
                if(settings.overlayOffsetX!=savedX||settings.overlayOffsetY!=savedY||config.overlayOffsetX.get()!=savedX||config.overlayOffsetY.get()!=savedY||NativeConfigStore.find(reloaded,config.overlayOffsetX.path()).getAsInt()!=savedX||NativeConfigStore.find(reloaded,config.overlayOffsetY.path()).getAsInt()!=savedY||!reloaded.get("unrelated").getAsBoolean())throw new AssertionError("Fresh native CLIENT reload did not retain the saved canonical overlay position and unknown preferences");
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.getActiveDispatcher().execute("create overlay reset",(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)client.player.connection.getSuggestionsProvider());if(settings.overlayOffsetX!=20||settings.overlayOffsetY!=0)throw new AssertionError("Reset command did not restore defaults");settings.load(original.get());java.nio.file.Files.delete(temporary.get());java.nio.file.Files.delete(temporary.get().getParent());
            }catch(Exception exception){throw new AssertionError(exception);}
        });
    }
    private static void wornModel(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var id=new AtomicInteger();server.runCommand("tp @p 517 4 9");context.waitFor(client->Math.abs(client.player.getX()-517.5)<0.1,200);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var stand=EntityTypes.ARMOR_STAND.create(level,EntitySpawnReason.COMMAND);stand.setPos(516.5,4,5.5);stand.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AllItems.GOGGLES));level.addFreshEntity(stand);id.set(stand.getId());});context.waitFor(client->client.level.getEntity(id.get())!=null,200);
        context.runOnClient(client->{var extracted=client.getEntityRenderDispatcher().extractEntity(client.level.getEntity(id.get()),0.5f);if(!(extracted instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living)||living.headItem.isEmpty()||living.headItem.getModelBoundingBox().getZsize()<=0.1)throw new AssertionError("Worn goggles missing from native entity extraction");});
        context.getInput().lookAt(new BlockPos(516,5,5));context.takeScreenshot("create-vulkan-goggles-worn-model");
    }
    private static void burner(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var pos=new BlockPos(546,4,5);server.runCommand("fill 544 3 1 550 3 12 minecraft:smooth_quartz");server.runCommand("tp @p 546 4 9");server.runCommand("setblock 546 4 5 create:blaze_burner[blaze=smouldering]");server.runCommand("item replace entity @p weapon.mainhand with create:goggles");
        context.waitFor(client->Math.abs(client.player.getX()-546.5)<0.1&&client.player.getMainHandItem().is(AllItems.GOGGLES)&&client.level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity,200);look(context,pos);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->((BlazeBurnerBlockEntity)client.level.getBlockEntity(pos)).goggles,200);
        var small=new AtomicReference<Object>();context.runOnClient(client->{var state=(BlazeBurnerRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),0.5f,null,false);if(state.goggles.mesh==null||state.goggles.mesh.size()==0||!client.player.getMainHandItem().is(AllItems.GOGGLES)||GogglesItem.isWearingGoggles(client.player))throw new AssertionError("Burner interaction did not decorate inert blaze");small.set(state.goggles.mesh);});
        context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-goggles-burner-inert");
        server.runOnServer(ignored->((BlazeBurnerBlockEntity)world.getConnection().getServerLevel().getBlockEntity(pos)).tryFuel(new ItemStack(Items.COAL),false,false));context.waitFor(client->client.level.getBlockState(pos).getValue(BlazeBurnerBlock.HEAT_LEVEL)==BlazeBurnerBlock.HeatLevel.KINDLED,200);
        var active=new AtomicReference<Object>();context.runOnClient(client->{var state=(BlazeBurnerRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),0.5f,null,false);if(state.goggles.mesh==null||state.goggles.mesh==small.get())throw new AssertionError("Burning blaze did not select large goggles");active.set(state.goggles.mesh);});
        context.takeScreenshot("create-vulkan-goggles-burner-kindled");
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitTicks(5);
        context.runOnClient(client->{var state=(BlazeBurnerRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),0.5f,null,false);if(state.goggles.mesh==null||state.goggles.mesh==active.get())throw new AssertionError("Burner goggles not rebuilt after reload");});
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);look(context,pos);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->!((BlazeBurnerBlockEntity)client.level.getBlockEntity(pos)).goggles,200);
        context.runOnClient(client->{var state=(BlazeBurnerRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),0.5f,null,false);if(!state.goggles.isEmpty())throw new AssertionError("Removed goggles still in burner render snapshot");});
    }
    private static void boilerAndProxies(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var tankPos=new BlockPos(556,4,5);var enginePos=new BlockPos(555,4,5);server.runCommand("fill 552 3 2 570 3 12 minecraft:smooth_quartz");server.runCommand("tp @p 557 4 9");context.waitFor(client->Math.abs(client.player.getX()-557.5)<0.1&&client.level.isLoaded(new BlockPos(568,4,5)),500);
        server.runCommand("fill 556 4 5 557 4 6 create:fluid_tank");server.runCommand("setblock 556 3 5 create:blaze_burner[blaze=smouldering]");server.runCommand("setblock 553 4 5 create:shaft[axis=z]");server.runCommand("setblock 555 4 5 create:steam_engine[face=wall,facing=west]");server.runCommand("setblock 557 5 5 create:steam_whistle[facing=south]");
        server.runCommand("item replace entity @p armor.head with create:goggles");server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((BlazeBurnerBlockEntity)level.getBlockEntity(new BlockPos(556,3,5))).tryFuel(new ItemStack(Items.COAL),false,false);com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.incrementSize(level,new BlockPos(557,5,5));});
        context.waitFor(client->GogglesItem.isWearingGoggles(client.player)&&client.level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank&&tank.boiler.attachedEngines==1&&tank.boiler.activeHeat==1,200);
        var tankFront=tankPos.south();look(context,tankFront);context.waitTicks(8);assertOverlay(context,tankFront,"Boiler","Water","10");context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-goggles-boiler-needs-water");
        server.runCommand("setblock 559 4 5 create:creative_fluid_tank");server.runCommand("setblock 558 4 5 create:mechanical_pump[facing=west]");server.runCommand("setblock 558 5 5 create:cogwheel[axis=x]");server.runCommand("setblock 559 5 5 create:creative_motor[facing=west]");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var source=(com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity)level.getBlockEntity(new BlockPos(559,4,5));((com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank)source.getTankInventory()).setContainedFluid(new com.simibubi.create.foundation.fluid.FluidStack(FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),81000));((KineticBlockEntity)level.getBlockEntity(new BlockPos(559,5,5))).adjustGeneratedSpeed(false);});
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(553,4,5)) instanceof KineticBlockEntity shaft&&shaft.getSpeed()!=0,300);look(context,tankFront);context.waitTicks(8);assertOverlay(context,tankFront,"16,384");context.takeScreenshot("create-vulkan-goggles-boiler-running");
        server.runCommand("tp @p 554 4 9");context.waitFor(client->Math.abs(client.player.getX()-554.5)<0.1,200);look(context,enginePos);context.waitTicks(8);assertOverlay(context,enginePos,"16,384");
        var extension=new BlockPos(557,6,5);context.runOnClient(client->{var info=GoggleOverlayRenderer.collect(client.level,client.player,extension);if(info==null||!info.position().equals(extension.below())||info.lines().stream().noneMatch(line->line.getString().contains("Pitch")))throw new AssertionError("Whistle extension did not proxy note tooltip");});
        server.runCommand("setblock 566 5 5 create:large_water_wheel[axis=z]");context.waitFor(client->client.level.getBlockState(new BlockPos(565,5,5)).is(AllBlocks.WATER_WHEEL_STRUCTURAL),200);
        context.runOnClient(client->{var info=GoggleOverlayRenderer.collect(client.level,client.player,new BlockPos(565,5,5));if(info==null||!info.position().equals(new BlockPos(566,5,5)))throw new AssertionError("Large wheel structural cell did not proxy generator tooltip");});
        look(context,enginePos);context.runOnClient(client->{client.gui.hud.toggle();var state=new net.minecraft.client.renderer.state.gui.GuiRenderState();GoggleOverlayRenderer.renderOverlay(new net.minecraft.client.gui.GuiGraphicsExtractor(client,state,0,0),client.getDeltaTracker());var lines=new AtomicInteger();state.forEachText(line->lines.incrementAndGet());client.gui.hud.toggle();if(lines.get()!=0||GoggleOverlayRenderer.hoverTicks!=0)throw new AssertionError("Hidden HUD still extracts goggle text");});
    }
}
