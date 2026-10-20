package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.steamWhistle.*;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.WhistleSize;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import java.util.*;

final class WhistleRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 484 9 15");context.waitFor(client->Math.abs(client.player.getX()-484.5)<0.1&&client.level.isLoaded(new BlockPos(484,4,7)),500);
        server.runCommand("fill 477 2 2 487 2 10 minecraft:smooth_quartz");server.runCommand("fill 480 3 5 482 3 7 minecraft:magma_block");server.runCommand("fill 480 4 5 482 6 7 create:fluid_tank");
        for(int i=0;i<3;i++)server.runCommand("setblock "+(480+i)+" 7 5 create:steam_whistle[facing=south,size="+WhistleSize.values()[i].getSerializedName()+"]");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int i=0;i<3;i++)for(int n=0;n<new int[]{0,3,12}[i];n++)WhistleBlock.incrementSize(level,new BlockPos(480+i,7,5));});
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(482,7,5)) instanceof WhistleBlockEntity whistle&&whistle.getPitch()==12,200);
        server.runCommand("fill 480 7 4 482 7 4 minecraft:redstone_block");context.waitFor(client->client.level.getBlockEntity(new BlockPos(480,7,5)) instanceof WhistleBlockEntity whistle&&whistle.isSounding()&&whistle.getAnimation(1)>0.8f,200);context.waitTicks(5);
        check(context,0,0,WhistleSize.SMALL);check(context,1,3,WhistleSize.MEDIUM);check(context,2,12,WhistleSize.LARGE);
        context.runOnClient(client->{var tank=(FluidTankBlockEntity)client.level.getBlockEntity(new BlockPos(480,4,5));if(tank.boiler.attachedWhistles!=3||tank.boiler.waterSupply!=0||tank.boiler.attachedEngines!=0)throw new AssertionError("Whistle boiler requires unexpected engine/water");});
        context.getInput().lookAt(new BlockPos(481,8,5));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-steam-whistles-three-octaves");
        var oldGeometry=new java.util.concurrent.atomic.AtomicReference<Object>();var rimQuads=new java.util.concurrent.atomic.AtomicInteger();
        context.runOnClient(client->{var pos=new BlockPos(480,7,5);var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);oldGeometry.set(model.createGeometryKey(client.level,pos,state,net.minecraft.util.RandomSource.create()));var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,pos,state,net.minecraft.util.RandomSource.create(),side->false);rimQuads.set(mesh.immutableCopy().size());});
        server.runOnServer(ignored->WhistleBlock.incrementSize(world.getConnection().getServerLevel(),new BlockPos(480,7,5)));context.waitFor(client->((WhistleBlockEntity)client.level.getBlockEntity(new BlockPos(480,7,5))).getPitch()==1,200);
        context.runOnClient(client->{var pos=new BlockPos(480,7,5);var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);var key=model.createGeometryKey(client.level,pos,state,net.minecraft.util.RandomSource.create());var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,pos,state,net.minecraft.util.RandomSource.create(),side->false);
            if(mesh.immutableCopy().size()!=rimQuads.get()-6||Objects.equals(key,oldGeometry.get()))throw new AssertionError("Extension did not hide base rim/update geometry key");});
        var previous=new java.util.concurrent.atomic.AtomicReference<WhistleSoundInstance>();context.runOnClient(client->previous.set(WhistleEffects.getSound((WhistleBlockEntity)client.level.getBlockEntity(new BlockPos(481,7,5)))));
        server.runCommand("setblock 481 7 5 create:steam_whistle[facing=south,size=small,powered=true]");context.waitFor(client->client.level.getBlockEntity(new BlockPos(481,7,5)) instanceof WhistleBlockEntity whistle&&WhistleEffects.getSound(whistle)!=null&&WhistleEffects.getSound(whistle)!=previous.get(),200);
        context.waitFor(client->previous.get().isStopped(),100);check(context,1,3,WhistleSize.SMALL);
        var oldModels=new java.util.concurrent.atomic.AtomicReference<Object>();context.runOnClient(client->{var rendered=(WhistleRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(new BlockPos(480,7,5)),0.5f,null,false);oldModels.set(rendered.mouth.mesh);});
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitTicks(10);
        context.runOnClient(client->{var rendered=(WhistleRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(new BlockPos(480,7,5)),0.5f,null,false);if(rendered.mouth.mesh==null||rendered.mouth.mesh==oldModels.get())throw new AssertionError("Whistle mouth not rebuilt on resource reload");});
        check(context,0,1,WhistleSize.SMALL);check(context,1,3,WhistleSize.SMALL);check(context,2,12,WhistleSize.LARGE);
        var sounds=new ArrayList<WhistleSoundInstance>();context.runOnClient(client->{for(int i=0;i<3;i++)sounds.add(WhistleEffects.getSound((WhistleBlockEntity)client.level.getBlockEntity(new BlockPos(480+i,7,5))));});
        server.runCommand("fill 480 7 4 482 7 4 minecraft:air");context.waitFor(client->sounds.stream().allMatch(WhistleSoundInstance::isStopped),150);
        context.runOnClient(client->{for(int i=0;i<3;i++){var whistle=(WhistleBlockEntity)client.level.getBlockEntity(new BlockPos(480+i,7,5));if(whistle.isSounding()||whistle.getAnimation(1)>0.01f||WhistleEffects.getSound(whistle)!=null)throw new AssertionError("Whistle failed to stop after redstone removed");}});
        context.takeScreenshot("create-vulkan-steam-whistles-silent");
    }
    private static void check(ClientGameTestContext context,int index,int pitch,WhistleSize size){context.runOnClient(client->{
        var whistle=(WhistleBlockEntity)client.level.getBlockEntity(new BlockPos(480+index,7,5));var sound=WhistleEffects.getSound(whistle);
        if(sound==null||sound.isStopped()||sound.getOctave()!=size||Math.abs(sound.getPitch()-Math.pow(2,-pitch/12.0))>0.001)throw new AssertionError("Whistle octave/pitch mismatch: "+index);
        if(!client.getSoundManager().isActive(sound)||sound.getSound()==null)throw new AssertionError("Native sound engine is not playing whistle: "+index);
        var state=(WhistleRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(whistle,0.5f,null,false);if(state.mouth.mesh==null||state.mouth.mesh.size()==0||state.offset<=0.15f)throw new AssertionError("Animated whistle mouth missing: "+index);
    });}
}
