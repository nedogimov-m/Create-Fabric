package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Proposed actual item input -> real pavement preview/debit/water/cargo -> native reload/unload/return. */
final class TrackPavingRenderingTest {
    private static final BlockPos A=new BlockPos(35006,10,6),B=new BlockPos(35014,10,6),C=new BlockPos(35038,10,16),D=new BlockPos(35058,12,16);
    private static final BlockPos WATER=A.offset(3,-1,1),CARGO=A.offset(4,-1,-1);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        boolean previouslyHidden=context.computeOnClient(client->client.gui.hud.isHidden());
        int clientView=context.computeOnClient(client->client.options.renderDistance().get());
        int serverView=world.getServer().computeOnServer(server->server.getPlayerList().getViewDistance());
        // The scene scans its complete five-chunk-wide real footprint from both
        // endpoints. Use ordinary native view settings so those actual chunks stay
        // available; never turn an unavailable cell into presumed empty terrain.
        try{
            context.runOnClient(client->{client.options.renderDistance().set(Math.max(clientView,6));client.options.broadcastOptions();});
            world.getServer().runOnServer(server->server.getPlayerList().setViewDistance(Math.max(serverView,6)));
            waitServer(context,world,level->level.getServer().getPlayerList().getPlayers().getFirst().requestedViewDistance()>=6&&level.getServer().getPlayerList().getViewDistance()>=6,200,"Actual native client information did not acknowledge the pavement fixture view distance");
            if(previouslyHidden)context.runOnClient(client->client.gui.hud.toggle());
            runVisible(context,world);
        }finally{
            world.getServer().runOnServer(server->server.getPlayerList().setViewDistance(serverView));
            context.runOnClient(client->{client.options.renderDistance().set(clientView);client.options.broadcastOptions();if(client.gui.hud.isHidden()!=previouslyHidden)client.gui.hud.toggle();});
        }
    }
    private static void runVisible(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 35032.5 22.0 16.5");
        context.waitFor(client->client.level.isLoaded(new BlockPos(35000,4,0))&&client.level.isLoaded(new BlockPos(35064,4,32)),400);
        waitServer(context,world,level->{for(int x=35000>>4;x<=35064>>4;x++)for(int z=0;z<=32>>4;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;return true;},600,"Actual native view settings did not load every source pavement fixture chunk");
        server.runCommand("fill 35000 4 0 35064 4 32 minecraft:smooth_quartz");server.runCommand("fill 35003 9 8 35017 9 12 minecraft:smooth_quartz");server.runCommand("fill 35035 9 18 35041 9 22 minecraft:smooth_quartz");server.runCommand("fill 35055 11 18 35061 11 22 minecraft:smooth_quartz");
        var originalBarrel=new AtomicReference<BarrelBlockEntity>();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var pos:List.of(A,B,C,D)){level.setBlockAndUpdate(pos.below(),Blocks.SMOOTH_QUARTZ.defaultBlockState());level.setBlockAndUpdate(pos,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO));}level.setBlockAndUpdate(WATER,Blocks.WATER.defaultBlockState());level.setBlockAndUpdate(CARGO,Blocks.BARREL.defaultBlockState());var barrel=(BarrelBlockEntity)level.getBlockEntity(CARGO);var diamonds=new ItemStack(Items.DIAMOND,7);diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Seven original native pavement diamonds"));barrel.setItem(0,diamonds);originalBarrel.set(barrel);});
        server.runCommand("gamemode survival @p");server.runCommand("clear @p");server.runOnServer(ignored->{var p=actual(world.getConnection().getServerLevel());var tracks=new ItemStack(AllBlocks.TRACK,64);tracks.set(DataComponents.CUSTOM_NAME,Component.literal("Original pavement scene selected stock"));p.setItemInHand(InteractionHand.MAIN_HAND,tracks);p.getInventory().setItem(9,new ItemStack(AllBlocks.TRACK,64));p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STONE_SLAB,64));for(int slot=10;slot<=12;slot++){var slabs=new ItemStack(Items.STONE_SLAB,64);slabs.set(DataComponents.CUSTOM_NAME,Component.literal("Original native pavement stock "+slot));p.getInventory().setItem(slot,slabs);}p.containerMenu.broadcastChanges();});
        context.waitFor(client->count(client.player,AllBlocks.TRACK.asItem())==128&&count(client.player,Items.STONE_SLAB)==256,200);
        select(context,world,A);target(context,world,B);context.waitFor(client->frame(B,false)&&TrackPlacementPreview.latest().requiredTracks()==7&&TrackPlacementPreview.latest().requiredPavement()==48&&TrackPlacementPreview.latest().pavementCells()==24,250);
        var straightPreview=context.computeOnClient(client->TrackPlacementPreview.latest());
        if(straightPreview.pavingItem()!=Items.STONE_SLAB||!straightPreview.hasPavement()||straightPreview.lines()<straightPreview.pavementCells()*4)throw new AssertionError("Actual native source HUD/footprint lacks real slab item/count/availability and cell outlines");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actual(level);if(count(p,AllBlocks.TRACK.asItem())!=128||count(p,Items.STONE_SLAB)!=256||slabQuantity(level)!=0||level.getBlockEntity(A)!=null||level.getBlockEntity(CARGO)!=originalBarrel.get()||!level.getBlockState(WATER).is(Blocks.WATER))throw new AssertionError("Actual pavement preview changed native world, water, cargo, stock or conditional BE before input");});
        screenshot(context,"create-vulkan-track-paving-real-slab-footprint-preview-source-cost-and-water-cargo");context.getInput().pressKey(options->options.keyUse);context.waitFor(client->!client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM)&&client.level.getBlockState(A.east(3)).is(AllBlocks.TRACK),250);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actual(level);if(count(p,AllBlocks.TRACK.asItem())!=121||count(p,Items.STONE_SLAB)!=208||slabQuantity(level)!=48||p.getMainHandItem().getCount()!=64||p.getInventory().getItem(9).getCount()!=57||p.getOffhandItem().getCount()!=64||p.getInventory().getItem(10).getCount()!=16||!p.getMainHandItem().has(DataComponents.CUSTOM_NAME)||!p.getInventory().getItem(10).has(DataComponents.CUSTOM_NAME))throw new AssertionError("Actual source pavement/track payment lost original main-before-selected-before-offhand order, components or exact stock quantities");var water=level.getBlockState(WATER);if(!water.is(Blocks.STONE_SLAB)||water.getValue(SlabBlock.TYPE)!=SlabType.DOUBLE||!water.getValue(BlockStateProperties.WATERLOGGED)||level.getBlockEntity(CARGO)!=originalBarrel.get())throw new AssertionError("Native source slab construction failed to preserve actual water or existing BE identity");cargo(level);});
        select(context,world,C);target(context,world,D);var paidTracks=new AtomicInteger();var paidPavement=new AtomicInteger();
        context.getInput().holdKey(options->options.keySprint);
        try{
            context.waitFor(client->frame(D,true)&&TrackPlacementPreview.latest().requiredPavement()>0&&TrackPlacementPreview.latest().pavementCells()>10,250);context.runOnClient(client->{paidTracks.set(TrackPlacementPreview.latest().requiredTracks());paidPavement.set(TrackPlacementPreview.latest().requiredPavement());});
            server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(level.getBlockEntity(C)!=null||level.getBlockEntity(D)!=null||slabQuantity(level)!=48||count(actual(level),Items.STONE_SLAB)!=208||level.getBlockEntity(CARGO)!=originalBarrel.get())throw new AssertionError("Real curved pavement preview mutated actual terrain, endpoint ownership, earlier cargo or paid stock");});screenshot(context,"create-vulkan-track-paving-real-sloped-curve-preview-native-half-slab-footprint");
            context.getInput().holdKey(options->options.keyUp);
            try{context.waitFor(client->client.player.isSprinting(),80);waitServer(context,world,level->actual(level).isSprinting(),80,"Actual input never reached authoritative native source sprint state");context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity be&&be.getConnections().containsKey(D),300);}finally{context.getInput().releaseKey(options->options.keyUp);}
        }finally{context.getInput().releaseKey(options->options.keySprint);}
        context.waitFor(client->!client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),200);
        var saved=new AtomicReference<Map<BlockPos,BlockState>>();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actual(level);var curve=((TrackBlockEntity)level.getBlockEntity(C)).getConnections().get(D);if(curve==null||curve.hasGirder||curve.bePositions.getFirst().equals(curve.bePositions.getSecond())||count(p,AllBlocks.TRACK.asItem())+7+paidTracks.get()!=128||count(p,Items.STONE_SLAB)+48+paidPavement.get()!=256||slabQuantity(level)!=48+paidPavement.get())throw new AssertionError("Actual native sloped paving world and paid source material ledger disagree");int tops=0,bottoms=0;var states=new LinkedHashMap<BlockPos,BlockState>();for(var pos:BlockPos.betweenClosed(35000,5,0,35064,15,32)){var state=loadedState(level,pos);if(state.is(Blocks.STONE_SLAB)){if(state.getValue(SlabBlock.TYPE)==SlabType.TOP)tops++;if(state.getValue(SlabBlock.TYPE)==SlabType.BOTTOM)bottoms++;states.put(pos.immutable(),state);}else if(state.is(AllBlocks.TRACK)||state.is(Blocks.BARREL))states.put(pos.immutable(),state);}if(tops==0||tops!=bottoms||level.getBlockEntity(CARGO)!=originalBarrel.get())throw new AssertionError("Native sloped source paving did not create actual paired slab halves or changed original cargo identity");cargo(level);saved.set(Map.copyOf(states));});
        view(context,world);rendered(context,world);screenshot(context,"create-vulkan-track-paving-actual-source-curve-half-slabs-water-and-original-barrel");
        var slabModel=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(WATER)));var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();
        context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(WATER))==slabModel)throw new AssertionError("Actual native slab model identity did not reload");for(var entry:saved.get().entrySet())if(client.level.getBlockState(entry.getKey())!=entry.getValue())throw new AssertionError("Native resource reload changed genuine pavement state");});
        var originalEndpoints=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();return new net.minecraft.world.level.block.entity.BlockEntity[]{level.getBlockEntity(C),level.getBlockEntity(D),originalBarrel.get()};});
        server.runOnServer(nativeServer->{if(!nativeServer.saveEverything(true,true,true))throw new AssertionError("Actual native pavement save did not complete");});server.runCommand("tp @p 36400.5 20.0 36400.5");waitServer(context,world,level->List.of(A,B,C,D,CARGO).stream().allMatch(pos->level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null),900,"Actual native source pavement endpoint/cargo chunks did not unload");
        NativeFixtureChunkUnload.finish(context,world,originalEndpoints);
        context.waitFor(client->List.of(A,B,C,D,CARGO).stream().allMatch(pos->client.level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null),400);
        server.runCommand("tp @p 35032.5 24.0 24.5");context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity be&&be.getConnections().containsKey(D)&&client.level.getBlockEntity(D) instanceof TrackBlockEntity&&client.level.getBlockEntity(CARGO) instanceof BarrelBlockEntity&&client.level.isLoaded(new BlockPos(35000,4,0))&&client.level.isLoaded(new BlockPos(35064,4,32)),500);
        waitServer(context,world,level->{for(int x=35000>>4;x<=35064>>4;x++)for(int z=0;z<=32>>4;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;return true;},600,"Actual returned native pavement fixture did not restore every source chunk before the exact material census");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int i=0;i<2;i++)if(level.getBlockEntity(i==0?C:D)==originalEndpoints[i])throw new AssertionError("Native paved curve return reused an unretired original endpoint BE");});
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var entry:saved.get().entrySet())if(level.getBlockState(entry.getKey())!=entry.getValue())throw new AssertionError("Genuine native save/unload/return changed real source pavement/track data");if(level.getBlockEntity(CARGO)==originalBarrel.get())throw new AssertionError("Native cargo fixture never actually unloaded and decoded");cargo(level);if(count(actual(level),AllBlocks.TRACK.asItem())+7+paidTracks.get()!=128||count(actual(level),Items.STONE_SLAB)+48+paidPavement.get()!=256||slabQuantity(level)!=48+paidPavement.get())throw new AssertionError("Actual native return changed original paid source materials");});
        view(context,world);rendered(context,world);screenshot(context,"create-vulkan-track-paving-native-resource-reload-save-unload-return-original-material-ledger");
    }
    private static boolean frame(BlockPos target,boolean maximised){var frame=TrackPlacementPreview.latest();return frame!=null&&target.equals(frame.target())&&frame.valid()&&frame.maximised()==maximised&&frame.pavingItem()==Items.STONE_SLAB&&frame.hasTracks()&&frame.hasPavement();}
    private static void select(ClientGameTestContext context,TestSingleplayerContext world,BlockPos source){target(context,world,source);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),200);}
    private static void target(ClientGameTestContext context,TestSingleplayerContext world,BlockPos pos){world.getServer().runCommand("tp @p "+(pos.getX()+.5)+" "+pos.getY()+" "+(pos.getZ()+3.5));context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-pos.getX()-.5)<.1,200);look(context,Vec3.atBottomCenterOf(pos).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos),200);}
    private static void view(ClientGameTestContext context,TestSingleplayerContext world){world.getServer().runCommand("gamemode spectator @p");world.getServer().runCommand("tp @p 35032.5 32.0 56.5");context.waitFor(client->Math.abs(client.player.getX()-35032.5)<.1&&Math.abs(client.player.getZ()-56.5)<.1,200);look(context,new Vec3(35032,10,14));}
    private static void rendered(ClientGameTestContext context,TestSingleplayerContext world){
        try{world.getConnection().waitForChunksRender();}catch(AssertionError failure){
            String clientState=context.computeOnClient(client->{int radius=client.options.getEffectiveRenderDistance();var center=client.player.chunkPosition();var missing=new ArrayList<String>();for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(client.level.getChunkSource().getChunkNow(center.x()+x,center.z()+z)==null)missing.add((center.x()+x)+","+(center.z()+z));return "configured="+client.options.renderDistance().get()+", effective="+radius+", rendered="+client.levelRenderer.hasRenderedAllSections()+", playerCenter="+center+", missing="+missing;});
            String serverState=world.getServer().computeOnServer(server->"view="+server.getPlayerList().getViewDistance()+", requested="+server.getPlayerList().getPlayers().getFirst().requestedViewDistance());
            throw new AssertionError("Actual native pavement chunks did not finish rendering; client "+clientState+"; server "+serverState,failure);
        }
    }
    private static int slabQuantity(ServerLevel level){int total=0;for(var pos:BlockPos.betweenClosed(35000,5,0,35064,15,32)){var state=loadedState(level,pos);if(state.is(Blocks.STONE_SLAB))total+=state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE?2:1;}return total;}
    private static BlockState loadedState(ServerLevel level,BlockPos pos){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)throw new AssertionError("Actual native pavement scene terrain unavailable at "+pos);return chunk.getBlockState(pos);}
    private static void cargo(ServerLevel level){var be=level.getBlockEntity(CARGO);if(!(be instanceof BarrelBlockEntity barrel)||!barrel.getItem(0).is(Items.DIAMOND)||barrel.getItem(0).getCount()!=7||!barrel.getItem(0).has(DataComponents.CUSTOM_NAME))throw new AssertionError("Seven original named native pavement diamonds changed");}
    private static Player actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static int count(Player p,Item item){int total=0;for(int i=0;i<p.getInventory().getNonEquipmentItems().size();i++)if(p.getInventory().getItem(i).is(item))total+=p.getInventory().getItem(i).getCount();if(p.getOffhandItem().is(item))total+=p.getOffhandItem().getCount();return total;}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int limit,String message){var ready=new AtomicBoolean();for(int i=0;i<limit;i++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(ready.get())return;context.waitTicks(1);}throw new AssertionError(message);}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var d=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
