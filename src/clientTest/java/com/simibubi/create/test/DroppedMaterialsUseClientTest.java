package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.mixin.LegacyBeaconAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Finite original chromatic compound is dropped using Q into actual lights, a beacon beam and the void. */
public final class DroppedMaterialsUseClientTest implements FabricClientGameTest {
    private final List<String> passed=new ArrayList<>();
    @Override public void runTest(ClientGameTestContext c){
        try(var w=c.worldBuilder().create()){
            var server=w.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 48.5 10 54.5");
            var min=new BlockPos(32,4,32);var max=new BlockPos(64,4,64);c.waitFor(client->client.level.isLoaded(min)&&client.level.isLoaded(max),400);
            NativeScreenshotReadiness.beforeFloor(c,w,min,max,new BlockPos(48,5,51),new BlockPos(48,5,54));
            server.runCommand("fill 32 5 32 64 30 64 minecraft:air");server.runCommand("fill 32 4 32 64 4 64 minecraft:smooth_quartz");NativeScreenshotReadiness.afterFloor(c,w,min,max);
            server.runCommand("gamemode survival @p");server.runCommand("tp @p 48.5 5 54.5");
            c.waitFor(client->client.player.onGround()&&!client.player.isSpectator()&&client.player.distanceToSqr(48.5,5,54.5)<.01,200);
            c.getInput().pressKey(InputConstants.KEY_2);c.getInput().pressKey(InputConstants.KEY_1);await(c,w,level->player(level).getInventory().getSelectedSlot()==0,"Native original drop slot");
            server.runCommand("fill 45 5 48 51 5 53 minecraft:torch");supply(c,w);look(c,new Vec3(48.5,5,51.5));aimAcknowledged(c,w);c.getInput().pressKey(options->options.keyDrop);
            await(c,w,level->player(level).getMainHandItem().isEmpty()&&items(level).stream().anyMatch(item->item.getItem().is(AllItems.CHROMATIC_COMPOUND)),"The actual native Q input must drop the one original compound");
            await(c,w,DroppedMaterialsUseClientTest::charged,"Physically dropped compound must consume a real light source and acquire charge");
            server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();int torches=0;for(var pos:BlockPos.betweenClosed(45,5,48,51,5,53))if(level.getBlockState(pos).is(Blocks.TORCH))torches++;
                int stock=player(level).getInventory().countItem(AllItems.CHROMATIC_COMPOUND)+items(level).stream().filter(item->item.getItem().is(AllItems.CHROMATIC_COMPOUND)).mapToInt(item->item.getItem().getCount()).sum();
                if(torches>=42||stock!=1)throw new AssertionError("Real light conversion failed to pay a torch or duplicated the original compound");});
            c.takeScreenshot("create-native-dropped-compound-actual-torch-absorption");passed.add("Actual Q drop consumes a real torch and charges the one original chromatic compound");cleanup(w);
            server.runCommand("fill 45 5 48 51 5 53 minecraft:air");server.runCommand("fill 47 3 50 49 3 52 minecraft:iron_block");server.runCommand("setblock 48 4 51 minecraft:beacon");
            await(c,w,level->level.getBlockEntity(new BlockPos(48,4,51)) instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity beacon&&!((LegacyBeaconAccessor)beacon).create$legacyBeamSections().isEmpty(),"Actual native beacon beam becomes ready without synthetic beam state");
            server.runCommand("tp @p 48.5 5 53.5");c.waitFor(client->client.player.distanceToSqr(48.5,5,53.5)<.01&&client.player.onGround(),200);supply(c,w);c.getInput().lookAt(180,0);aimAcknowledged(c,w);c.getInput().pressKey(options->options.keyDrop);
            var radiance=new AtomicReference<UUID>();await(c,w,level->{var found=items(level).stream().filter(item->item.getItem().is(AllItems.REFINED_RADIANCE)&&item.isNoGravity()).toList();if(found.size()!=1)return false;radiance.set(found.getFirst().getUUID());return player(level).getMainHandItem().isEmpty()&&found.getFirst().getItem().getCount()==1&&items(level).stream().noneMatch(item->item.getItem().is(AllItems.CHROMATIC_COMPOUND));},"Physical beacon-beam drop must convert exactly one actual original into floating radiance");
            verifyTracked(c,radiance.get(),AllItems.REFINED_RADIANCE);c.takeScreenshot("create-native-dropped-compound-actual-beacon-radiance");passed.add("Actual Q drop into a live beacon beam produces one tracked, rendered, gravity-free refined radiance");cleanup(w);
            server.runCommand("setblock 48 4 51 minecraft:air");server.runCommand("tp @p 48.5 5 54.5");c.waitFor(client->client.player.distanceToSqr(48.5,5,54.5)<.01&&client.player.onGround(),200);
            int bottom=c.computeOnClient(client->client.level.getMinY());server.runCommand("fill 38 "+bottom+" 34 57 4 53 minecraft:air");
            supply(c,w);c.getInput().lookAt(180,45);aimAcknowledged(c,w);c.getInput().pressKey(options->options.keyDrop);
            var shadow=new AtomicReference<UUID>();await(c,w,level->{var found=items(level).stream().filter(item->item.getItem().is(AllItems.SHADOW_STEEL)&&item.isNoGravity()).toList();if(found.size()!=1)return false;shadow.set(found.getFirst().getUUID());return player(level).getMainHandItem().isEmpty()&&found.getFirst().getItem().getCount()==1&&items(level).stream().noneMatch(item->item.getItem().is(AllItems.CHROMATIC_COMPOUND));},"Physical drop through the actual void must become exactly one gravity-free shadow steel before native removal");
            var position=new AtomicReference<Vec3>();server.runOnServer(ignored->position.set(w.getConnection().getServerLevel().getEntity(shadow.get()).position()));
            server.runCommand("gamemode spectator @p");server.runCommand("tp @p "+position.get().x+" "+(position.get().y+2)+" "+(position.get().z+5));
            verifyTracked(c,shadow.get(),AllItems.SHADOW_STEEL);var at=c.computeOnClient(client->find(client,shadow.get()).position());look(c,at);c.takeScreenshot("create-native-dropped-compound-actual-void-shadow-steel");
            passed.add("Actual Q drop through the genuine world bottom converts one original into tracked, rendered, gravity-free shadow steel");
        }finally{
            try{var file=Path.of("build/reports/dropped-materials-physical-use.json");Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(passed)+"\n");}
            catch(Exception failure){throw new AssertionError(failure);}
        }
    }
    private static void supply(ClientGameTestContext c,TestSingleplayerContext w){w.getServer().runCommand("clear @p");w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.getInventory().setItem(0,new ItemStack(AllItems.CHROMATIC_COMPOUND));player.containerMenu.broadcastChanges();});c.waitFor(client->client.player.getMainHandItem().is(AllItems.CHROMATIC_COMPOUND)&&client.player.getMainHandItem().getCount()==1,200);c.waitTicks(5);}
    private static void cleanup(TestSingleplayerContext w){w.getServer().runOnServer(ignored->new ArrayList<>(items(w.getConnection().getServerLevel())).forEach(ItemEntity::discard));}
    private static List<ItemEntity> items(ServerLevel level){return level.getEntitiesOfClass(ItemEntity.class,new AABB(16,level.getMinY()-64,16,80,64,80));}
    private static ServerPlayer player(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static boolean charged(ServerLevel level){if(items(level).stream().anyMatch(item->item.getItem().is(AllItems.CHROMATIC_COMPOUND)&&item.getItem().getOrDefault(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,0)>0))return true;var inventory=player(level).getInventory();for(int slot=0;slot<inventory.getContainerSize();slot++){var stack=inventory.getItem(slot);if(stack.is(AllItems.CHROMATIC_COMPOUND)&&stack.getOrDefault(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,0)>0)return true;}return false;}
    private static ItemEntity find(net.minecraft.client.Minecraft client,UUID id){for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id)&&entity instanceof ItemEntity item)return item;return null;}
    private static void verifyTracked(ClientGameTestContext c,UUID id,net.minecraft.world.item.Item item){c.waitFor(client->{var actual=find(client,id);return actual!=null&&actual.isNoGravity()&&actual.getItem().is(item);},300);c.runOnClient(client->{var actual=find(client,id);var state=new net.minecraft.client.renderer.item.ItemStackRenderState();client.getItemModelResolver().updateForTopItem(state,actual.getItem(),ItemDisplayContext.GROUND,client.level,null,actual.getId());if(state.isEmpty())throw new AssertionError("Actual converted native item has no render model: "+item);});}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> condition,String message){var ok=new AtomicBoolean();for(int tick=0;tick<600;tick++){w.getServer().runOnServer(ignored->ok.set(condition.test(w.getConnection().getServerLevel())));if(ok.get())return;c.waitTicks(1);}var diagnostic=new AtomicReference<String>();w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();diagnostic.set("held="+player(level).getMainHandItem()+", heldLight="+player(level).getMainHandItem().getOrDefault(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,0)+", inventoryCompound="+player(level).getInventory().countItem(AllItems.CHROMATIC_COMPOUND)+", items="+items(level).stream().map(item->item.getItem()+" at "+item.position()+" light="+item.getItem().getOrDefault(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,0)).toList());});throw new AssertionError(message+"; "+diagnostic.get());}
    private static void aimAcknowledged(ClientGameTestContext c,TestSingleplayerContext w){var angles=c.computeOnClient(client->new Vec2(client.player.getYRot(),client.player.getXRot()));await(c,w,level->Math.abs(net.minecraft.util.Mth.wrapDegrees(player(level).getYRot()-angles.x))<.1&&Math.abs(player(level).getXRot()-angles.y)<.1,"Actual native drop direction must reach the server before Q");}
    private static void look(ClientGameTestContext c,Vec3 target){var angles=c.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angles.x,angles.y);}
}
