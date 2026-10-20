package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.pulley.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Isolated next-stage real solver, shared mirrors, mounted passenger, resource reload and native unload. */
final class PulleyRenderingTest {
    private static final BlockPos B=new BlockPos(7655,14,5),MIRROR=B.east(2),BODY=B.below(3),SEAT=BODY.east().above();
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 7656.5 16 15.5");context.waitFor(client->Math.abs(client.player.getX()-7656.5)<.1&&client.level.isLoaded(B)&&client.level.isLoaded(MIRROR),400);server.runCommand("fill 7649 3 -2 7663 3 16 minecraft:smooth_quartz");
        NativeContraptionFixtures.awaitEntityReady(context,world,300,B,MIRROR,BODY,SEAT);
        var pigId=new AtomicReference<UUID>();server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();for(var pos:List.of(B,MIRROR)){level.setBlockAndUpdate(pos,AllBlocks.ROPE_PULLEY.defaultBlockState());((PulleyBlockEntity)level.getBlockEntity(pos)).setMovementMode(IControlContraption.MovementMode.MOVE_NEVER_PLACE);level.setBlockAndUpdate(pos.below(),AllBlocks.ROPE.defaultBlockState());level.setBlockAndUpdate(pos.below(2),AllBlocks.PULLEY_MAGNET.defaultBlockState());}
            level.setBlockAndUpdate(BODY,Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(BODY)).setItem(0,cargo());for(var pos:List.of(BODY.east(),BODY.east(2)))level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(SEAT,AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState());
            var pig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);pig.setNoAi(true);pig.setPersistenceRequired();pig.setPos(Vec3.atCenterOf(SEAT).add(0,.5,0));if(!level.addFreshEntity(pig)||!SeatBlock.trySitDown(level,SEAT,pig))throw new AssertionError("Actual world seat failed to capture fixture pig");pigId.set(pig.getUUID());level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(BODY,SEAT.east())));
        });
        NativeContraptionFixtures.awaitCaptureReady(context,world,400,BODY,SEAT,BODY.east(),BODY.east(2),B.below(),MIRROR.below());server.runCommand("setblock 7654 14 5 create:creative_motor[facing=east]");
        var ownerId=new AtomicReference<UUID>();waitServer(context,world,level->{
            var root=(PulleyBlockEntity)level.getBlockEntity(B);var owner=root.getMovedContraption();var mirror=(PulleyBlockEntity)level.getBlockEntity(MIRROR);if(owner==null||mirror.getAttachedContraption()!=owner||level.getEntity(pigId.get()).getVehicle()!=owner)return false;
            if(owner.getContraption().getBlocks().size()!=4||!exact(owner.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0))||!mirror.getMirrorOwner().equals(owner.getUUID())||mirror.getMovedContraption()!=null)throw new AssertionError("Real native mirror duplicates body/cargo or fails passenger ownership");ownerId.set(owner.getUUID());return root.offset>2.3f;
        },400,"Real source RPM did not capture the body/pig into one native owner with a passive mirror");
        waitClient(context,ownerId.get());verify(context,world,ownerId.get());look(context,Vec3.atCenterOf(BODY));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-rope-pulley-real-body-passenger-and-shared-mirrored-ropes");
        var before=new AtomicReference<Double>();server.runOnServer(ignored->before.set(world.getConnection().getServerLevel().getEntity(ownerId.get()).getY()));context.waitTicks(12);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=level.getEntity(ownerId.get());if(owner==null||owner.getY()>=before.get()||level.getEntity(pigId.get()).getVehicle()!=owner)throw new AssertionError("Real motor must lower the actual native body and its original seated passenger");});
        var old=context.computeOnClient(client->client.getModelManager().getModel(AllPartialModels.ROPE_COIL));var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{if(client.getModelManager().getModel(AllPartialModels.ROPE_COIL)==old)throw new AssertionError("Native rope pulley kept stale coil geometry after resource reload");});verify(context,world,ownerId.get());screenshot(context,"create-vulkan-rope-pulley-native-coil-uv-rope-magnet-after-resource-reload");
        server.runCommand("tp @p 9300 16 9300");waitServer(context,world,level->level.getEntity(ownerId.get())==null&&level.getEntity(pigId.get())==null&&level.getChunkSource().getChunkNow(B.getX()>>4,B.getZ()>>4)==null,900,"Actual pulley body/passenger or mirror metadata retained native source chunks");server.runCommand("tp @p 7656.5 16 15.5");
        waitServer(context,world,level->{
            if(!(level.getBlockEntity(B) instanceof PulleyBlockEntity root)||!(level.getBlockEntity(MIRROR) instanceof PulleyBlockEntity mirror)||!(level.getEntity(ownerId.get()) instanceof LinearContraptionEntity owner)||level.getEntity(pigId.get())==null)return false;
            if(root.getMovedContraption()!=owner||mirror.getAttachedContraption()!=owner||level.getEntity(pigId.get()).getVehicle()!=owner)return false;if(!exact(owner.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0)))throw new AssertionError("Native body unload/return changed exact named cargo");return true;
        },700,"Original actual pulley body and seated pig UUIDs did not reconnect with both native controllers");waitClient(context,ownerId.get());verify(context,world,ownerId.get());look(context,Vec3.atCenterOf(B).add(0,-4,0));screenshot(context,"create-vulkan-rope-pulley-original-native-uuid-and-passenger-after-genuine-unload-return");
        server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(B.west())).setGeneratedSpeed(0));waitServer(context,world,level->level.getBlockEntity(B) instanceof PulleyBlockEntity root&&root.getSpeed()==0&&root.getMovedContraption()!=null,300,"Real motor source did not pause the actual body before native player restoration");
        var barrelTarget=new AtomicReference<BlockPos>();var seatTarget=new AtomicReference<BlockPos>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var root=(PulleyBlockEntity)level.getBlockEntity(B);var transform=root.getMovedContraption().getRestorationTransform();barrelTarget.set(transform.apply(BlockPos.ZERO));seatTarget.set(transform.apply(new BlockPos(1,1,0)));root.setMovementMode(IControlContraption.MovementMode.MOVE_PLACE);});
        server.runCommand("setblock 7655 12 9 minecraft:smooth_quartz");server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");server.runCommand("tp @p 7655.5 13 9.0");server.runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&client.player.getMainHandItem().isEmpty(),250);look(context,Vec3.atCenterOf(B));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(B),200);context.getInput().pressKey(options->options.keyUse);
        waitServer(context,world,level->{var root=(PulleyBlockEntity)level.getBlockEntity(B);var mirror=(PulleyBlockEntity)level.getBlockEntity(MIRROR);if(root.isRunning()||mirror.getMirrorParent()!=null||level.getEntity(ownerId.get())!=null)return false;var pig=level.getEntity(pigId.get());if(!(level.getBlockEntity(barrelTarget.get()) instanceof BarrelBlockEntity barrel)||!exact(barrel.getItem(0))||pig==null||!(pig.getVehicle() instanceof SeatEntity seat)||!seat.blockPosition().equals(seatTarget.get()))return false;return true;},500,"Actual native empty-hand dispatch at zero RPM did not restore the original named cargo, both physical ropes and original passenger to its real world seat");look(context,Vec3.atCenterOf(barrelTarget.get()));screenshot(context,"create-vulkan-rope-pulley-native-player-stop-restored-exact-cargo-and-original-world-seat-passenger");
    }
    private static void verify(ClientGameTestContext context,TestSingleplayerContext world,UUID id){context.runOnClient(client->{for(var pos:List.of(B,MIRROR)){var pulley=(PulleyBlockEntity)client.level.getBlockEntity(pos);var dispatcher=client.getBlockEntityRenderDispatcher();var renderer=(PulleyRenderer)dispatcher.<PulleyBlockEntity,PulleyRenderer.State>getRenderer(pulley);var state=renderer.createRenderState();renderer.extractRenderState(pulley,state,.5f,client.player.getEyePosition(),null);if(state.coil.mesh==null||state.coil.mesh.size()==0||state.shaft.mesh==null||state.sections.size()<2||state.sections.stream().anyMatch(section->section.model().mesh==null||section.model().mesh.size()==0)||!state.running||state.mirror!=pos.equals(MIRROR))throw new AssertionError("Actual native rope/half-magnet/coil/shared mirror extraction is incomplete");}if(!(find(client,id) instanceof LinearContraptionEntity owner)||owner.getContraption()==null)throw new AssertionError("Actual client native body snapshot missing");});}
    private static void waitClient(ClientGameTestContext context,UUID id){context.waitFor(client->find(client,id) instanceof LinearContraptionEntity owner&&owner.getContraption()!=null&&client.level.getBlockEntity(B) instanceof PulleyBlockEntity&&client.level.getBlockEntity(MIRROR) instanceof PulleyBlockEntity mirror&&mirror.getMirrorParent()!=null,500);}
    private static Entity find(Minecraft client,UUID id){if(client.level!=null)for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static ItemStack cargo(){var stack=new ItemStack(Items.DIAMOND,7);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Actual native pulley cargo"));return stack;}
    private static boolean exact(ItemStack stack){return stack.getCount()==7&&ItemStack.isSameItemSameComponents(stack,cargo());}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var done=new AtomicBoolean();for(int i=0;i<ticks&&!done.get();i++){world.getServer().runOnServer(ignored->done.set(predicate.test(world.getConnection().getServerLevel())));if(!done.get())context.waitTicks(1);}if(!done.get())throw new AssertionError(message);}
    private static void look(ClientGameTestContext context,Vec3 point){var angles=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angles.x,angles.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
