package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.waterwheel.*;
import java.util.*;
import java.util.function.Predicate;
import net.createmod.catnip.outliner.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Bounded actual native click/failure/outline/status/expiry then one paid retry and eight real cells. Authored1 UNRUN. */
public final class LargeWaterWheelItemRenderingTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(48,5,48),SUPPORT=P.below(),OBSTACLE=P.above().south();
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{if(!RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"))throw new AssertionError("Large-wheel feedback must render on the actual native Vulkan backend");});
        try(var world=context.worldBuilder().adjustSettings(creator->creator.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()){
            world.getServer().runCommand("fill 45 4 45 54 4 52 minecraft:smooth_quartz");world.getServer().runCommand("tp @p 51.5 5 48.5");
            context.waitFor(client->client.level!=null&&client.level.isLoaded(P)&&client.player.onGround()&&client.player.position().distanceToSqr(new Vec3(51.5,5,48.5))<.05,400);
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var stock=new ItemStack(AllBlocks.LARGE_WATER_WHEEL,4);stock.set(DataComponents.CUSTOM_NAME,Component.literal("Four original Vulkan large wheels"));player.getInventory().setItem(player.getInventory().getSelectedSlot(),stock);player.connection.send(player.getInventory().createInventoryUpdatePacket(player.getInventory().getSelectedSlot()));player.inventoryMenu.broadcastChanges();level.setBlockAndUpdate(OBSTACLE,Blocks.BARREL.defaultBlockState());var cargo=new ItemStack(Items.DIAMOND,11);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Eleven original Vulkan wheel obstruction diamonds"));((BarrelBlockEntity)level.getBlockEntity(OBSTACLE)).setItem(0,cargo);});
            context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.LARGE_WATER_WHEEL.asItem())&&client.player.getMainHandItem().getCount()==4&&client.level.getBlockState(OBSTACLE).is(Blocks.BARREL),200);
            look(context,Vec3.atCenterOf(SUPPORT).add(0,.499,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(SUPPORT)&&hit.getDirection()==Direction.UP,180);context.waitTicks(2);
            context.getInput().holdKey(options->options.keyUse);
            try {
            context.waitFor(client->Outliner.getInstance().getOutlines().containsKey(LargeWaterWheelPlacementClient.boundsKey(P)),40);
            context.runOnClient(client->{var entry=Outliner.getInstance().getOutlines().get(LargeWaterWheelPlacementClient.boundsKey(P));if(entry==null||!(entry.getOutline() instanceof AABBOutline bounds)||!bounds.getBounds().equals(new AABB(P).inflate(1).deflate(1,0,0)))throw new AssertionError("Actual source client failure did not retain the original one-by-three-by-three footprint");var message=status(client);var expected=Component.translatable("create.large_water_wheel.not_enough_space");if(expected.getString().equals("create.large_water_wheel.not_enough_space")||message==null||!message.getString().equals(expected.getString())||message.getStyle().getColor()==null||message.getStyle().getColor().getValue()!=0xFF5D6C)throw new AssertionError("Actual native status lost source translation or red error color");});
            context.waitFor(client->OutlinerRenderHandler.latest().outlines().stream().anyMatch(geometry->geometry.segments().size()==12&&(geometry.color()&0xFFFFFF)==0xFF5D6C),4);
            context.takeScreenshot("create-vulkan-large-wheel-real-placement-failure-red-three-by-three-source-outline-and-status");
            } finally {context.getInput().releaseKey(options->options.keyUse);}
            context.waitTicks(14);
            context.runOnClient(client->{if(Outliner.getInstance().getOutlines().containsKey(LargeWaterWheelPlacementClient.boundsKey(P))||OutlinerRenderHandler.latest().outlines().stream().anyMatch(geometry->(geometry.color()&0xFFFFFF)==0xFF5D6C))throw new AssertionError("Actual source Outliner error did not expire within its native finite fade lifetime");});
            await(context,world,level->level.getBlockState(P).isAir()&&level.getBlockState(P.above()).isAir()&&level.getBlockState(OBSTACLE).is(Blocks.BARREL)&&((BarrelBlockEntity)level.getBlockEntity(OBSTACLE)).getItem(0).getCount()==11&&level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()==4,80,"Rejected actual native client/server placement consumed source stock or changed obstruction/cargo");
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var barrel=(BarrelBlockEntity)level.getBlockEntity(OBSTACLE);var cargo=barrel.removeItemNoUpdate(0);if(cargo.getCount()!=11)throw new AssertionError("Actual original obstruction stock was lost");level.getServer().getPlayerList().getPlayers().getFirst().getInventory().placeItemBackInInventory(cargo,Prediction.SERVER_ONLY);level.destroyBlock(OBSTACLE,false);});
            context.waitFor(client->client.level.getBlockState(OBSTACLE).isAir(),160);look(context,Vec3.atCenterOf(SUPPORT).add(0,.499,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(SUPPORT)&&hit.getDirection()==Direction.UP,120);context.getInput().pressKey(options->options.keyUse);
            await(context,world,level->{var centre=P.above();if(!level.getBlockState(centre).is(AllBlocks.LARGE_WATER_WHEEL)||level.getBlockState(centre).getValue(LargeWaterWheelBlock.AXIS)!=Direction.Axis.X||level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()!=3)return false;for(var entry:LargeWaterWheelBlock.structure(centre,Direction.Axis.X).entrySet()){var state=level.getBlockState(entry.getKey());if(!state.is(AllBlocks.WATER_WHEEL_STRUCTURAL)||state.getValue(WaterWheelStructuralBlock.FACING)!=entry.getValue()||!centre.equals(WaterWheelStructuralBlock.master(level,entry.getKey(),state)))return false;}return true;},180,"Actual native retry click failed exact one survival debit and eight structural owners");
            context.waitFor(client->client.level.getBlockState(P.above()).is(AllBlocks.LARGE_WATER_WHEEL)&&client.player.getMainHandItem().getCount()==3,180);context.takeScreenshot("create-vulkan-large-wheel-original-support-native-retry-single-paid-centre-and-eight-structural-cells");
        }finally{context.getInput().releaseKey(options->options.keyUse);}
    }
    private static Component status(Minecraft client){try{var field=net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString");field.setAccessible(true);return (Component)field.get(client.gui.hud);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void await(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String failure){for(int index=0;index<ticks;index++){if(world.getServer().computeOnServer(ignored->predicate.test(world.getConnection().getServerLevel())))return;context.waitTicks(1);}throw new AssertionError(failure);}
}
