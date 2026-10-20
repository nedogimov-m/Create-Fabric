package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.redstone.*;
import com.simibubi.create.content.redstone.smartObserver.*;
import com.simibubi.create.content.logistics.funnel.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;

final class ObserverRenderingTest {
    private static final BlockPos P=new BlockPos(1210,4,5),SOURCE=P.north(),FUNNEL=P.east(5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var hidden=new AtomicBoolean();context.runOnClient(client->{hidden.set(client.gui.hud.isHidden());if(hidden.get())client.gui.hud.toggle();});server.runCommand("gamemode spectator @p");server.runCommand("tp @p 1210 7 12");context.waitFor(client->Math.abs(client.player.getX()-1210.5)<.1,300);
        waitForSceneChunks(context,world,new BlockPos(1205,3,0),new BlockPos(1226,3,15));
        server.runCommand("fill 1205 3 0 1226 3 15 minecraft:smooth_quartz");
        server.runCommand("setblock 1210 4 4 minecraft:chest");server.runCommand("setblock 1210 4 5 create:content_observer[facing=north,target=wall]");server.runCommand("setblock 1209 4 5 minecraft:redstone_lamp");
        server.runCommand("setblock 1213 4 5 create:content_observer[facing=east,target=floor]");server.runCommand("setblock 1213 6 5 create:content_observer[facing=west,target=ceiling]");
        server.runCommand("setblock 1214 4 5 minecraft:chest");server.runCommand("setblock 1215 4 5 create:brass_funnel[facing=east,extracting=false]");server.runCommand("setblock 1215 4 6 create:content_observer[facing=north,target=wall]");
        server.runCommand("tp @p 1212 4 8");context.waitFor(client->client.level.getBlockEntity(P) instanceof SmartObserverBlockEntity&&client.level.getBlockState(new BlockPos(1212,3,8)).is(Blocks.SMOOTH_QUARTZ)&&Math.abs(client.player.getX()-1212.5)<.1,300);world.getConnection().waitForChunksRender();
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.setHealth(player.getMaxHealth());player.resetFallDistance();});
        server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:apple 8");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(Items.APPLE),200);
        look(context,P,Direction.SOUTH);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->((SmartObserverBlockEntity)client.level.getBlockEntity(P)).filtering.getFilter().is(Items.APPLE),200);context.runOnClient(client->{if(client.player.getMainHandItem().getCount()!=8)throw new AssertionError("Ordinary observer sample consumed apples");});
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((ChestBlockEntity)level.getBlockEntity(SOURCE)).setItem(0,new ItemStack(Items.APPLE,12));});
        context.waitFor(client->client.level.getBlockState(P).getValue(SmartObserverBlock.POWERED)&&client.level.getBlockState(P.west()).getValue(RedstoneLampBlock.LIT),200);context.runOnClient(client->{var state=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,false);if(!(state instanceof SmartObserverRenderer.State observer)||observer.sampleFaces()!=2)throw new AssertionError("Sided observer filter samples not extracted");var block=client.level.getBlockState(P);var model=client.getModelManager().getBlockStateModelSet().get(block);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),(net.minecraft.client.renderer.block.BlockAndTintGetter)client.level,P,block,net.minecraft.util.RandomSource.create(42),side->false);if(mesh.immutableCopy().size()==0)throw new AssertionError("Powered observer model missing");});
        world.getConnection().waitForChunksRender();context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-observer-powered-filter");
        server.runOnServer(ignored->((ChestBlockEntity)world.getConnection().getServerLevel().getBlockEntity(SOURCE)).setItem(0,ItemStack.EMPTY));context.waitFor(client->!client.level.getBlockState(P).getValue(SmartObserverBlock.POWERED)&&!client.level.getBlockState(P.west()).getValue(RedstoneLampBlock.LIT),200);
        server.runCommand("item replace entity @p weapon.mainhand with create:filter");context.waitFor(client->client.player.getMainHandItem().is(AllItems.FILTER),200);look(context,P,Direction.SOUTH);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->((SmartObserverBlockEntity)client.level.getBlockEntity(P)).filtering.getFilter().is(AllItems.FILTER)&&client.player.getMainHandItem().isEmpty(),200);
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.runOnClient(client->{var state=(SmartObserverRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,false);if(state.sampleFaces()!=2)throw new AssertionError("Observer filter disappeared on resource reload");});
        // Committed instantaneous funnel transfers produce a four-tick pulse even without stored cargo.
        var activated=new AtomicBoolean();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var observer=(SmartObserverBlockEntity)level.getBlockEntity(FUNNEL.south());observer.filtering.setFilter(new ItemStack(Items.APPLE));((FunnelBlockEntity)level.getBlockEntity(FUNNEL)).insert(new ItemStack(Items.APPLE,7),false);activated.set(observer.turnOffTicks==4&&observer.getBlockState().getValue(SmartObserverBlock.POWERED));});if(!activated.get())throw new AssertionError("Funnel did not trigger a committed observer pulse");
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 1214 7 10");context.waitFor(client->Math.abs(client.player.getX()-1214.5)<.1,200);context.getInput().lookAt(new BlockPos(1213,5,5));world.getConnection().waitForChunksRender();context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-observer-orientations");chunkUnload(context,world);context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden.get())client.gui.hud.toggle();});
    }
    private static void chunkUnload(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var observerPos=new BlockPos(1248,4,5);var target=observerPos.west();server.runCommand("tp @p 1248 7 10");context.waitFor(client->Math.abs(client.player.getX()-1248.5)<.1,300);waitForSceneChunks(context,world,target,observerPos);server.runCommand("setblock 1247 4 5 minecraft:chest");server.runCommand("setblock 1248 4 5 create:content_observer[facing=west,target=wall]");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((ChestBlockEntity)level.getBlockEntity(target)).setItem(0,new ItemStack(Items.APPLE,12));level.getChunkSource().addTicket(new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.FORCED,33),new net.minecraft.world.level.ChunkPos(1248>>4,0));});context.waitFor(client->client.level.getBlockEntity(observerPos) instanceof SmartObserverBlockEntity observer&&observer.getBlockState().getValue(SmartObserverBlock.POWERED),200);
        server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var observer=(SmartObserverBlockEntity)com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,observerPos);if(observer!=null)for(int tick=0;tick<10;tick++)SmartObserverBlockEntity.tick(level,observerPos,observer.getBlockState(),observer);unloaded.set(!level.isLoaded(target));});}if(!unloaded.get())throw new AssertionError("Observer lookup kept target chunk loaded");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var observer=(SmartObserverBlockEntity)com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,observerPos);for(int tick=0;tick<30;tick++)SmartObserverBlockEntity.tick(level,observerPos,observer.getBlockState(),observer);if(observer.turnOffTicks!=0||level.isLoaded(target))throw new AssertionError("Unloaded inventory sustained a stale observer pulse");});
        server.runCommand("tp @p 1248 7 10");context.waitFor(client->Math.abs(client.player.getX()-1248.5)<.1&&client.level.getBlockEntity(observerPos) instanceof SmartObserverBlockEntity observer&&observer.getBlockState().getValue(SmartObserverBlock.POWERED),400);server.runOnServer(ignored->world.getConnection().getServerLevel().getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,new net.minecraft.world.level.ChunkPos(1248>>4,0),0));
    }
    private static void waitForSceneChunks(ClientGameTestContext context,TestSingleplayerContext world,BlockPos first,BlockPos last){
        // Client chunks from the previous scene can outlive their server counterparts during teleportation.
        // Commands require full server chunks as well as the client-visible scene.
        var serverReady=new AtomicBoolean();
        for(int attempt=0;attempt<300;attempt++){
            world.getServer().runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();
                serverReady.set(level.hasChunkAt(first)&&level.hasChunkAt(last));
            });
            if(serverReady.get()&&context.computeOnClient(client->client.level.isLoaded(first)&&client.level.isLoaded(last)))return;
            context.waitTicks(1);
        }
        throw new AssertionError("Observer scene chunks did not load on both server and client");
    }
    private static void look(ClientGameTestContext context,BlockPos pos,Direction side){var rotation=context.computeOnClient(client->{var point=SmartObserverBlock.FILTER_SLOT.position(side).add(Vec3.atLowerCornerOf(pos));var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y);context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&SmartObserverBlock.FILTER_SLOT.testHit(client.level.getBlockState(pos),pos,hit),200);}
}
