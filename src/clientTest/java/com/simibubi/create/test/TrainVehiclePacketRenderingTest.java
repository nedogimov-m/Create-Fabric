package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Real native Boat passenger/control counterpart; proves the narrow Train adaptation preserves vanilla sending. UNRUN. */
final class TrainVehiclePacketRenderingTest {
    private static final BlockPos WATER=new BlockPos(45048,5,36);
    static void verifyVanillaBoat(ClientGameTestContext context,TestSingleplayerContext world){
        var boatId=new AtomicReference<UUID>();var origin=new AtomicReference<Vec3>();
        try{
            world.getServer().runCommand("gamemode survival @p");world.getServer().runCommand("tp @p 45048.5 5 39.5");
            context.waitFor(client->!client.player.isSpectator()&&!client.player.isCreative()&&client.player.position().distanceToSqr(new Vec3(45048.5,5,39.5))<.02,250);
            await(context,world,level->level.getServer().getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer()==GameType.SURVIVAL,200,"Actual native Boat counterpart is not in survival");
            int empty=world.getServer().computeOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();for(int i=0;i<9;i++)if(player.getInventory().getItem(i).isEmpty())return i;throw new AssertionError("Native Boat input requires an actually empty hotbar slot");});
            context.getInput().pressKey(InputConstants.KEY_1+empty);context.waitFor(client->client.player.getMainHandItem().isEmpty()&&client.player.getInventory().getSelectedSlot()==empty,200);
            await(context,world,level->{var player=level.getServer().getPlayerList().getPlayers().getFirst();return player.getInventory().getSelectedSlot()==empty&&player.getMainHandItem().isEmpty();},200,"Actual native empty-hand selection was not acknowledged");
            NativeContraptionFixtures.awaitEntityReady(context,world,400,WATER);
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var pos:BlockPos.betweenClosed(WATER.offset(-1,0,-1),WATER.offset(1,0,1)))level.setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState());var boat=EntityTypes.OAK_BOAT.create(level,EntitySpawnReason.COMMAND);if(boat==null)throw new AssertionError("Native Boat factory refused the real fixture owner");boat.setPos(Vec3.atCenterOf(WATER).add(0,.1,0));if(!level.addFreshEntity(boat))throw new AssertionError("Native level refused its real Boat");boatId.set(boat.getUUID());origin.set(boat.position());});
            context.waitFor(client->find(client,boatId.get())!=null,250);TrainVehiclePacketProbe.watch(boatId.get());
            var angle=context.computeOnClient(client->{var delta=find(client,boatId.get()).getBoundingBox().getCenter().subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);
            context.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(boatId.get()),200);context.waitTicks(3);context.getInput().pressKey(options->options.keyUse);
            context.waitFor(client->client.player.getRootVehicle().getUUID().equals(boatId.get())&&client.player.getRootVehicle().getControllingPassenger()==client.player,250);
            await(context,world,level->{var boat=level.getEntity(boatId.get());var player=level.getServer().getPlayerList().getPlayers().getFirst();return boat!=null&&player.getRootVehicle()==boat&&boat.getControllingPassenger()==player;},250,"Real native interact packet did not establish the legitimate vanilla controlling passenger");
            context.getInput().holdKey(options->options.keyUp);try{context.waitTicks(30);}finally{context.getInput().releaseKey(options->options.keyUp);}
            await(context,world,level->{var boat=level.getEntity(boatId.get());return boat!=null&&Math.pow(boat.getX()-origin.get().x,2)+Math.pow(boat.getZ()-origin.get().z,2)>.0025;},200,"Actual native Boat input did not move its authoritative physical owner");
            TrainVehiclePacketProbe.assertVanillaVehicle();if(Boolean.getBoolean("create.test.nativeTrace"))System.out.println("CREATE_VANILLA_ACTUAL_VEHICLE_PACKET_BOUNDARIES "+TrainVehiclePacketProbe.snapshot());
            context.takeScreenshot("create-vulkan-train-narrow-packet-adaptation-preserves-real-vanilla-boat-control");
            context.getInput().holdKey(options->options.keyShift);try{context.waitFor(client->!client.player.isPassenger(),250);await(context,world,level->!level.getServer().getPlayerList().getPlayers().getFirst().isPassenger(),250,"Native Boat dismount never reached the real server");}finally{context.getInput().releaseKey(options->options.keyShift);}
        }finally{
            TrainVehiclePacketProbe.clear();context.getInput().releaseKey(options->options.keyUp);context.getInput().releaseKey(options->options.keyShift);
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(boatId.get()!=null){var boat=level.getEntity(boatId.get());if(boat!=null&&!boat.isVehicle())boat.discard();}for(var pos:BlockPos.betweenClosed(WATER.offset(-1,0,-1),WATER.offset(1,0,1)))if(level.getBlockState(pos).is(Blocks.WATER))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());});
        }
    }
    private static Entity find(net.minecraft.client.Minecraft client,UUID id){for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void await(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int timeout,String message){for(int i=0;i<timeout;i++){if(world.getServer().computeOnServer(ignored->condition.test(world.getConnection().getServerLevel())))return;context.waitTicks(1);}throw new AssertionError(message);}
    private TrainVehiclePacketRenderingTest(){}
}
