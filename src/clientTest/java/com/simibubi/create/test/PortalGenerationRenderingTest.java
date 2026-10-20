package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** Actual native absent-exit frame transaction, real player portal arrival/input, native models and real two-world unload. */
final class PortalGenerationRenderingTest {
    private static final BlockPos SOURCE=new BlockPos(43007,100,16),PORTAL=SOURCE.east(),TARGET_PORTAL=new BlockPos(5376,100,2),TARGET=new BlockPos(5377,100,2),CARGO=new BlockPos(5381,100,12);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());if(hidden)context.runOnClient(client->client.gui.hud.toggle());
        try{runVisible(context,world);}finally{context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});}
    }
    private static void runVisible(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @a[limit=1]");
        // Genuine registered player visits each actual dimension; no forced/mod readiness ticket.
        travel(world,"minecraft:the_nether","5380.5 104.0 10.5");context.waitFor(client->client.level.dimension().equals(Level.NETHER)&&client.level.isLoaded(new BlockPos(5375,100,0))&&client.level.isLoaded(new BlockPos(5383,100,16)),400);
        var originalCargo=new AtomicReference<BarrelBlockEntity>();
        server.runOnServer(ignored->{var level=nativeServer(world).getLevel(Level.NETHER);for(var pos:BlockPos.betweenClosed(5375,98,0,5383,105,16))level.setBlockAndUpdate(pos,pos.getY()<100?Blocks.SMOOTH_QUARTZ.defaultBlockState():Blocks.AIR.defaultBlockState());frame(level,TARGET_PORTAL);level.setBlockAndUpdate(CARGO,Blocks.BARREL.defaultBlockState());var barrel=(BarrelBlockEntity)level.getBlockEntity(CARGO);var diamonds=new ItemStack(Items.DIAMOND,7);diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Seven original generated portal scene diamonds"));barrel.setItem(0,diamonds);originalCargo.set(barrel);});
        travel(world,"minecraft:overworld","43032.5 112.0 16.5");context.waitFor(client->client.level.dimension().equals(Level.OVERWORLD)&&client.level.isLoaded(new BlockPos(43000,99,0))&&client.level.isLoaded(new BlockPos(43064,99,32)),400);
        server.runOnServer(ignored->{var level=nativeServer(world).overworld();for(var pos:BlockPos.betweenClosed(43000,99,0,43018,105,24))level.setBlockAndUpdate(pos,pos.getY()<100?Blocks.SMOOTH_QUARTZ.defaultBlockState():Blocks.AIR.defaultBlockState());frame(level,PORTAL);});
        travel(world,"minecraft:overworld","43005.5 100.0 16.5");context.waitFor(client->Math.abs(client.player.getX()-43005.5)<.1,200);
        server.runCommand("gamemode survival @a[limit=1]");server.runCommand("clear @a[limit=1]");
        context.getInput().lookAt(-90,0);context.getInput().holdKey(options->options.keyUp);
        try{context.waitFor(client->client.player.getX()>=43008.1||client.level.dimension().equals(Level.NETHER),200);}finally{context.getInput().releaseKey(options->options.keyUp);}
        // Native vanilla dwell/arrival supplies the genuine vanilla PORTAL ticket for the target.
        context.waitFor(client->client.level.dimension().equals(Level.NETHER),300);
        server.runOnServer(ignored->{var level=nativeServer(world).getLevel(Level.NETHER);if(level.getChunkSource().getChunkNow(TARGET.getX()>>4,TARGET.getZ()>>4)==null)throw new AssertionError("Actual native Nether arrival did not publish its genuine target chunk");cargo(level);
            // Remove only this scene's temporary native arrival frame. Its genuine vanilla
            // PORTAL ticket stays native; creation now faces a genuinely absent real exit.
            for(var pos:BlockPos.betweenClosed(5375,98,0,5378,105,6))level.setBlockAndUpdate(pos,pos.getY()<100?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());cargo(level);
        });
        travel(world,"minecraft:overworld","43004.5 100.0 16.5");context.waitFor(client->client.level.dimension().equals(Level.OVERWORLD)&&Math.abs(client.player.getX()-43004.5)<.1&&client.player.onGround(),200);
        var generatedShape=new AtomicReference<Map<BlockPos,net.minecraft.world.level.block.state.BlockState>>();
        server.runOnServer(ignored->{var minecraft=nativeServer(world);if(com.simibubi.create.api.contraption.train.PortalTrackProvider.getOtherSide(minecraft.overworld(),new net.createmod.catnip.math.BlockFace(SOURCE,Direction.EAST))!=null)throw new AssertionError("Actual generation scene has a real existing exit before paid input");var plan=NetherPortalFramePlan.find(minecraft.getLevel(Level.NETHER),TARGET_PORTAL,Direction.Axis.Z).orElseThrow();if(plan.fallback()||!plan.rectangle().minCorner.equals(TARGET_PORTAL))throw new AssertionError("Actual native prepared site did not select its genuine closest ordinary frame rectangle");var states=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var write:plan.writes())states.put(write.position(),write.state());generatedShape.set(Map.copyOf(states));cargo(minecraft.getLevel(Level.NETHER));});
        server.runOnServer(ignored->{var p=actual(world);var stock=new ItemStack(AllBlocks.TRACK,8);stock.set(DataComponents.CUSTOM_NAME,Component.literal("Eight original generation scene rails"));p.setItemInHand(InteractionHand.MAIN_HAND,stock);p.containerMenu.broadcastChanges();});
        context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.TRACK.asItem())&&client.player.getMainHandItem().getCount()==8,100);
        look(context,Vec3.atCenterOf(SOURCE.below()).add(0,.5,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(SOURCE.below())&&hit.getDirection()==Direction.UP,200);
        context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockEntity(SOURCE) instanceof TrackBlockEntity owner&&owner.getPortalBinding()!=null&&owner.boundLocation!=null,300);
        var identity=new AtomicReference<UUID>();var originalFirst=new AtomicReference<TrackBlockEntity>();var originalSecond=new AtomicReference<TrackBlockEntity>();
        server.runOnServer(ignored->{var minecraft=nativeServer(world);var first=owner(minecraft.overworld(),SOURCE);var second=owner(minecraft.getLevel(Level.NETHER),TARGET);if(first.getBlockState().getValue(TrackBlock.SHAPE)!=TrackShape.TE||second.getBlockState().getValue(TrackBlock.SHAPE)!=TrackShape.TW||actual(world).getMainHandItem().getCount()!=7||!actual(world).getMainHandItem().has(DataComponents.CUSTOM_NAME))throw new AssertionError("Actual native item input did not generate the real missing frame and source portal geometry with one original track debit/components");reciprocal(minecraft,first.getPortalBinding());identity.set(first.getPortalBinding());originalFirst.set(first);originalSecond.set(second);for(var entry:generatedShape.get().entrySet())if(minecraft.getLevel(Level.NETHER).getBlockState(entry.getKey())!=entry.getValue())throw new AssertionError("Actual paid native input failed to install exact vanilla generated frame/portal footprint");cargo(minecraft.getLevel(Level.NETHER));});
        CubeParticleRenderingTest.verify(context,SOURCE);world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-portal-generation-original-source-native-paid-item-and-reciprocal-owner");
        var oldModel=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(SOURCE)));var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(SOURCE))==oldModel)throw new AssertionError("Actual portal source model identity never reloaded");});CubeParticleRenderingTest.verify(context,SOURCE);
        travel(world,"minecraft:the_nether","5381.5 103.0 10.5");context.waitFor(client->client.level.dimension().equals(Level.NETHER)&&client.level.getBlockEntity(TARGET) instanceof TrackBlockEntity owner&&identity.get().equals(owner.getPortalBinding())&&client.level.getBlockEntity(CARGO) instanceof BarrelBlockEntity,300);look(context,Vec3.atCenterOf(TARGET));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-portal-generation-nether-original-counterpart-and-native-model");
        server.runOnServer(nativeServer->{if(!nativeServer.saveEverything(true,true,true))throw new AssertionError("Actual native generated portal save did not complete");});server.runCommand("gamemode spectator @a[limit=1]");travel(world,"minecraft:overworld","44400.5 120.0 44400.5");context.waitFor(client->client.level.dimension().equals(Level.OVERWORLD),200);
        waitServer(context,world,()->{var minecraft=nativeServer(world);return missing(minecraft.overworld(),SOURCE)&&missing(minecraft.getLevel(Level.NETHER),TARGET)&&missing(minecraft.getLevel(Level.NETHER),CARGO);},1200,"Both actual source/Nether chunks must unload after genuine vanilla portal ticket expires");
        var overworld=server.computeOnServer(ignored->nativeServer(world).overworld());
        var nether=server.computeOnServer(ignored->nativeServer(world).getLevel(Level.NETHER));
        NativeFixtureChunkUnload.finish(context,world,overworld,originalFirst.get());
        NativeFixtureChunkUnload.finish(context,world,nether,originalSecond.get(),originalCargo.get());
        context.waitFor(client->client.level.getChunkSource().getChunkNow(SOURCE.getX()>>4,SOURCE.getZ()>>4)==null,300);
        server.runOnServer(ignored->{if(!originalFirst.get().isRemoved()||!originalSecond.get().isRemoved()||!originalCargo.get().isRemoved())throw new AssertionError("Old native endpoint/cargo block entities were never genuinely unloaded");});
        // Native player first reloads Nether, then source; deferred pair validation must preserve cargo.
        travel(world,"minecraft:the_nether","5381.5 103.0 10.5");context.waitFor(client->client.level.dimension().equals(Level.NETHER)&&client.level.getBlockEntity(TARGET) instanceof TrackBlockEntity owner&&identity.get().equals(owner.getPortalBinding()),400);
        travel(world,"minecraft:overworld","43004.5 102.0 16.5");context.waitFor(client->client.level.dimension().equals(Level.OVERWORLD)&&client.level.getBlockEntity(SOURCE) instanceof TrackBlockEntity owner&&identity.get().equals(owner.getPortalBinding()),400);
        // Direct command travel does not refresh a native PORTAL ticket. Genuinely
        // enter the original portal again so the returned remote owner stays
        // available for the two-world custody and physical cleanup assertions.
        server.runCommand("gamemode survival @a[limit=1]");travel(world,"minecraft:overworld","43005.5 100.0 16.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-43005.5)<.1,200);
        context.getInput().lookAt(-90,0);context.getInput().holdKey(options->options.keyUp);
        try{context.waitFor(client->client.player.getX()>=43008.1||client.level.dimension().equals(Level.NETHER),200);}finally{context.getInput().releaseKey(options->options.keyUp);}
        context.waitFor(client->client.level.dimension().equals(Level.NETHER),300);
        travel(world,"minecraft:overworld","43004.5 100.0 16.5");context.waitFor(client->client.level.dimension().equals(Level.OVERWORLD)&&client.player.onGround()&&client.level.getBlockEntity(SOURCE) instanceof TrackBlockEntity owner&&identity.get().equals(owner.getPortalBinding()),400);
        waitServer(context,world,()->{var minecraft=nativeServer(world);var remote=minecraft.getLevel(Level.NETHER);return !missing(remote,TARGET)&&remote.getBlockEntity(TARGET) instanceof TrackBlockEntity owner&&identity.get().equals(owner.getPortalBinding())&&!missing(remote,CARGO);},300,"Actual returned portal arrival did not retain the genuine remote endpoint/cargo chunks");
        server.runOnServer(ignored->{var minecraft=nativeServer(world);reciprocal(minecraft,identity.get());if(owner(minecraft.overworld(),SOURCE)==originalFirst.get()||owner(minecraft.getLevel(Level.NETHER),TARGET)==originalSecond.get()||minecraft.getLevel(Level.NETHER).getBlockEntity(CARGO)==originalCargo.get()||actual(world).getMainHandItem().getCount()!=7)throw new AssertionError("Actual two-world unload did not decode new owners/cargo or changed original paid stock");cargo(minecraft.getLevel(Level.NETHER));});
        look(context,Vec3.atCenterOf(SOURCE));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-portal-generation-native-reload-two-world-unload-original-uuid-cargo-and-stock");
        // Actual normal attack input removes one real original endpoint; counterpart cannot pay twice.
        server.runCommand("gamemode survival @a[limit=1]");travel(world,"minecraft:overworld","43004.5 100.0 16.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-43004.5)<.1,200);server.runOnServer(ignored->{var p=actual(world);p.getInventory().setItem(9,p.getMainHandItem());p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.containerMenu.broadcastChanges();});look(context,Vec3.atBottomCenterOf(SOURCE).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(SOURCE),200);
        context.getInput().holdKey(options->options.keyAttack);try{context.waitFor(client->client.level.getBlockState(SOURCE).isAir(),200);}finally{context.getInput().releaseKey(options->options.keyAttack);}
        waitServer(context,world,()->{var minecraft=nativeServer(world);return !missing(minecraft.getLevel(Level.NETHER),TARGET)&&minecraft.getLevel(Level.NETHER).getBlockState(TARGET).isAir();},200,"Actual removed source must clean its genuine loaded counterpart without native duplicate item");
        server.runOnServer(ignored->{var minecraft=nativeServer(world);cargo(minecraft.getLevel(Level.NETHER));for(var entry:generatedShape.get().entrySet())if(minecraft.getLevel(Level.NETHER).getBlockState(entry.getKey())!=entry.getValue())throw new AssertionError("Real pair cleanup changed native generated portal/frame ownership");if(actual(world).getInventory().getItem(9).getCount()!=7||!actual(world).getInventory().getItem(9).has(DataComponents.CUSTOM_NAME))throw new AssertionError("Actual generated frame/track lifecycle changed seven original unspent native rail items");int loose=minecraft.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(SOURCE).inflate(4)).stream().filter(entity->entity.getItem().is(AllBlocks.TRACK.asItem())).mapToInt(entity->entity.getItem().getCount()).sum();int remote=minecraft.getLevel(Level.NETHER).getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(TARGET).inflate(4)).stream().filter(entity->entity.getItem().is(AllBlocks.TRACK.asItem())).mapToInt(entity->entity.getItem().getCount()).sum();if(loose!=1||remote!=0)throw new AssertionError("Real native player destruction must produce exactly one original physical rail and no remote copy");});
    }
    private static MinecraftServer nativeServer(TestSingleplayerContext world){return world.getConnection().getServerLevel().getServer();}
    private static Player actual(TestSingleplayerContext world){return nativeServer(world).getPlayerList().getPlayers().getFirst();}
    private static void travel(TestSingleplayerContext world,String dimension,String position){world.getServer().runCommand("execute as @a[limit=1] in "+dimension+" run tp @s "+position);}
    private static void frame(ServerLevel level,BlockPos corner){for(int z=-1;z<=2;z++)for(int y=-1;y<=3;y++)if(z==-1||z==2||y==-1||y==3)level.setBlockAndUpdate(corner.offset(0,y,z),Blocks.OBSIDIAN.defaultBlockState());for(int z=0;z<2;z++)for(int y=0;y<3;y++)level.setBlockAndUpdate(corner.offset(0,y,z),Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,Direction.Axis.Z));}
    private static boolean missing(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null;}
    private static TrackBlockEntity owner(ServerLevel level,BlockPos pos){if(missing(level,pos)||!(level.getBlockEntity(pos) instanceof TrackBlockEntity result))throw new AssertionError("Actual native portal owner unavailable at "+pos);return result;}
    private static void reciprocal(MinecraftServer minecraft,UUID id){var first=owner(minecraft.overworld(),SOURCE);var second=owner(minecraft.getLevel(Level.NETHER),TARGET);if(id==null||!id.equals(first.getPortalBinding())||!id.equals(second.getPortalBinding())||first.boundLocation==null||second.boundLocation==null||!first.boundLocation.getFirst().equals(Level.NETHER)||!first.boundLocation.getSecond().equals(TARGET)||!second.boundLocation.getFirst().equals(Level.OVERWORLD)||!second.boundLocation.getSecond().equals(SOURCE))throw new AssertionError("Actual native portal ownership is not reciprocal original UUID/dimensions/positions");}
    private static void cargo(ServerLevel level){if(missing(level,CARGO)||!(level.getBlockEntity(CARGO) instanceof BarrelBlockEntity barrel)||!barrel.getItem(0).is(Items.DIAMOND)||barrel.getItem(0).getCount()!=7||!barrel.getItem(0).has(DataComponents.CUSTOM_NAME))throw new AssertionError("Seven original named native Nether diamonds changed");}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,BooleanSupplier condition,int limit,String message){var ready=new AtomicBoolean();for(int tick=0;tick<limit;tick++){world.getServer().runOnServer(ignored->ready.set(condition.getAsBoolean()));if(ready.get())return;context.waitTicks(1);}throw new AssertionError(message);}
    private static void look(ClientGameTestContext context,Vec3 target){var angles=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angles.x,angles.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
