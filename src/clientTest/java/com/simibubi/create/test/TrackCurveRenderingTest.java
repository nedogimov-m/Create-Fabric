package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Proposed real input -> paid girder curve -> native outline/beam frames -> wrench/abort/server break. */
final class TrackCurveRenderingTest {
    private static final BlockPos P=new BlockPos(21012,5,4),Q=P.offset(8,0,8);
    private static final AABB AREA=new AABB(21002,3,-2,21025,10,19);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());
        context.runOnClient(client->{if(client.gui.hud.isHidden())client.gui.hud.toggle();});
        try{runVisible(context,world);}finally{
            context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});
        }
    }
    private static void runVisible(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 21012.5 8 7.5");
        context.waitFor(client->client.level.isLoaded(P)&&client.level.isLoaded(Q)&&Math.abs(client.player.getX()-21012.5)<.1,400);
        NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(21003, 4, -1), new BlockPos(21023, 4, 17), P, Q);
        server.runCommand("fill 21003 4 -1 21023 4 17 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(21003, 4, -1), new BlockPos(21023, 4, 17));
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(P,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO));level.setBlockAndUpdate(Q,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.ZO));});
        server.runCommand("gamemode survival @p");server.runCommand("clear @p");server.runCommand("item replace entity @p weapon.mainhand with create:track 64");server.runCommand("item replace entity @p weapon.offhand with create:metal_girder 64");
        build(context,world);verify(context);aimCurve(context,world);
        context.waitFor(client->TrackBlockOutline.result()!=null&&TrackBlockOutline.latest()!=null&&TrackBlockOutline.latest().lines()==12,200);
        context.runOnClient(client->{var frame=TrackBlockOutline.latest();if(frame.selection()==null||frame.lines()!=12||client.hitResult.getType()!=HitResult.Type.MISS)throw new AssertionError("Actual curve picking did not publish immutable twelve-line native gizmo frame and protect block behind");});
        screenshot(context,"create-vulkan-track-paid-girder-native-beams-caps-and-curve-selection");
        var old=context.computeOnClient(client->((TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true)).pieces.getFirst().model().mesh);
        var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();verify(context);
        context.runOnClient(client->{var state=(TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true);if(state.pieces.getFirst().model().mesh==old)throw new AssertionError("Actual girder source model retained stale reload mesh");});
        var originalTracks=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();return new BlockEntity[]{LoadedLevelAccess.blockEntity(level,P),LoadedLevelAccess.blockEntity(level,Q)};});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 22200 20 22200");
        waitServer(context,world,level->level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null&&level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)==null,800,"Actual paid girder endpoint chunks did not unload");
        NativeFixtureChunkUnload.finish(context,world,originalTracks);
        server.runCommand("tp @p 21016.5 8 12.5");context.waitFor(client->Math.abs(client.player.getX()-21016.5)<.1&&Math.abs(client.player.getZ()-12.5)<.1&&client.level.getBlockEntity(P) instanceof TrackBlockEntity a&&a.getConnections().get(Q)!=null&&a.getConnections().get(Q).hasGirder&&client.level.getBlockEntity(Q) instanceof TrackBlockEntity b&&b.getConnections().containsKey(P),500);verify(context);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var a=(TrackBlockEntity)level.getBlockEntity(P);var b=(TrackBlockEntity)level.getBlockEntity(Q);if(a==originalTracks[0]||b==originalTracks[1]||!a.getConnections().get(Q).equalsSansMaterial(b.getConnections().get(P)))throw new AssertionError("Real native reload changed paid source girder curve");});
        server.runCommand("gamemode survival @p");giveTool(world,AllItems.WRENCH);context.waitFor(client->client.player.getMainHandItem().is(AllItems.WRENCH),200);aimCurve(context,world);
        var block=context.computeOnClient(client->BlockPos.containing(client.player.getEyePosition().add(client.player.getLookAngle().scale(1.3))));
        server.runOnServer(ignored->world.getConnection().getServerLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState()));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(block)&&TrackBlockOutline.result()==null,200);
        context.getInput().holdKey(options->options.keyShift);context.getInput().pressKey(options->options.keyUse);context.waitTicks(4);context.getInput().releaseKey(options->options.keyShift);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(!connected(level))throw new AssertionError("Native use through actual occluder removed curve");level.setBlockAndUpdate(block,Blocks.AIR.defaultBlockState());});
        context.waitFor(client->TrackBlockOutline.result()!=null,200);context.getInput().holdKey(options->options.keyShift);
        try{context.getInput().pressKey(options->options.keyUse);context.waitFor(client->!client.level.getBlockState(P).getValue(TrackBlock.HAS_BE)&&!client.level.getBlockState(Q).getValue(TrackBlock.HAS_BE)&&client.level.getBlockEntity(P)==null&&client.level.getBlockEntity(Q)==null,300);}finally{context.getInput().releaseKey(options->options.keyShift);}
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actualPlayer(level);if(count(p,AllBlocks.TRACK.asItem())!=64||count(p,AllBlocks.METAL_GIRDER.asItem())!=64||!level.getBlockState(P).is(AllBlocks.TRACK)||!level.getBlockState(Q).is(AllBlocks.TRACK))throw new AssertionError("Actual native shift-wrench did not conserve original track/girder stocks and both physical source endpoints");});
        screenshot(context,"create-vulkan-track-real-shift-wrench-refunds-and-retires-curve-with-endpoints-intact");
        // Build the same real curve again using existing inventory; no new curve or material is scripted.
        holdConstruction(world);context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.TRACK.asItem()),200);build(context,world);giveTool(world,Items.NETHERITE_PICKAXE);context.waitFor(client->client.player.getMainHandItem().is(Items.NETHERITE_PICKAXE),200);aimCurve(context,world);
        context.getInput().holdKey(options->options.keyAttack);
        try{context.waitFor(client->CurvedTrackInteraction.progress()>0&&TrackBlockOutline.latest()!=null&&TrackBlockOutline.latest().breakProgress()>0,100);screenshot(context,"create-vulkan-track-native-server-tick-hardness-progress");}finally{context.getInput().releaseKey(options->options.keyAttack);}
        waitServer(context,world,level->connected(level)&&CurvedTrackServerInteraction.progress(actualPlayer(level))==0,80,"Real native attack release did not abort actual server session");
        context.waitTicks(12);server.runOnServer(ignored->{if(!connected(world.getConnection().getServerLevel()))throw new AssertionError("Aborted source curve broke after release");});
        var before=new AtomicReference<Set<UUID>>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var ids=new HashSet<UUID>();level.getEntitiesOfClass(ItemEntity.class,AREA).forEach(e->ids.add(e.getUUID()));before.set(ids);});
        aimCurve(context,world);context.getInput().holdKey(options->options.keyAttack);
        try{context.waitFor(client->client.level.getBlockEntity(P)==null&&client.level.getBlockEntity(Q)==null&&!client.level.getBlockState(P).getValue(TrackBlock.HAS_BE),300);}finally{context.getInput().releaseKey(options->options.keyAttack);}
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actualPlayer(level);int track=count(p,AllBlocks.TRACK.asItem()),girder=count(p,AllBlocks.METAL_GIRDER.asItem());for(var drop:level.getEntitiesOfClass(ItemEntity.class,AREA))if(!before.get().contains(drop.getUUID())){if(drop.getItem().is(AllBlocks.TRACK.asItem()))track+=drop.getItem().getCount();if(drop.getItem().is(AllBlocks.METAL_GIRDER.asItem()))girder+=drop.getItem().getCount();}if(connected(level)||track!=64||girder!=64||!level.getBlockState(P).is(AllBlocks.TRACK)||!level.getBlockState(Q).is(AllBlocks.TRACK))throw new AssertionError("Actual native survival curve breaking lost/duplicated paid inventory or physical endpoints");});
        screenshot(context,"create-vulkan-track-real-held-attack-breaks-paid-curve-with-exact-native-drops");
    }
    private static void build(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var beforeTracks=new AtomicInteger();var beforeGirders=new AtomicInteger();server.runOnServer(ignored->{var p=actualPlayer(world.getConnection().getServerLevel());beforeTracks.set(count(p,AllBlocks.TRACK.asItem()));beforeGirders.set(count(p,AllBlocks.METAL_GIRDER.asItem()));});
        server.runCommand("tp @p 21012.5 5 7.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-21012.5)<.1&&client.player.getMainHandItem().is(AllBlocks.TRACK.asItem()),200);
        look(context,Vec3.atBottomCenterOf(P).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),200);
        server.runCommand("tp @p 21020.5 5 15.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-21020.5)<.1,200);look(context,Vec3.atBottomCenterOf(Q).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(Q),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockEntity(P) instanceof TrackBlockEntity a&&a.getConnections().containsKey(Q)&&a.getConnections().get(Q).hasGirder&&client.level.getBlockEntity(Q) instanceof TrackBlockEntity b&&b.getConnections().containsKey(P),300);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var a=(TrackBlockEntity)level.getBlockEntity(P);var curve=a.getConnections().get(Q);var p=actualPlayer(level);if(!curve.primary||!curve.hasGirder||count(p,AllBlocks.TRACK.asItem())!=beforeTracks.get()-curve.getTrackItemCost()||count(p,AllBlocks.METAL_GIRDER.asItem())!=beforeGirders.get()-curve.getGirderItemCost())throw new AssertionError("Actual native input failed exact source track/girder debit");if(TrackCurveCoverage.positions(curve).stream().noneMatch(pos->level.getBlockState(pos).is(AllBlocks.FAKE_TRACK)))throw new AssertionError("Actual curve created no native path coverage");});
    }
    private static void aimCurve(ClientGameTestContext context,TestSingleplayerContext world){var point=context.computeOnClient(client->((TrackBlockEntity)client.level.getBlockEntity(P)).getConnections().get(Q).getPosition(.5));world.getServer().runCommand("tp @p "+point.x+" 5 "+(point.z+3));context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-point.x)<.1&&Math.abs(client.player.getZ()-point.z-3)<.1,200);look(context,point);context.waitFor(client->TrackBlockOutline.result()!=null,200);}
    private static Player actualPlayer(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static boolean connected(ServerLevel level){return level.getBlockEntity(P) instanceof TrackBlockEntity a&&a.getConnections().containsKey(Q);}
    private static int count(Player player,Item item){int total=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot).is(item))total+=player.getInventory().getItem(slot).getCount();return total;}
    private static void giveTool(TestSingleplayerContext world,Item item){world.getServer().runOnServer(ignored->{var p=actualPlayer(world.getConnection().getServerLevel());if(!p.getMainHandItem().isEmpty()){int free=p.getInventory().getFreeSlot();if(free<0)throw new AssertionError("No actual empty fixture inventory slot");p.getInventory().setItem(free,p.getMainHandItem());p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));p.containerMenu.broadcastChanges();});}
    private static void holdConstruction(TestSingleplayerContext world){world.getServer().runOnServer(ignored->{var p=actualPlayer(world.getConnection().getServerLevel());int slot=-1;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(AllBlocks.TRACK.asItem())){slot=i;break;}if(slot<0)throw new AssertionError("No actual conserved stock for second native construction");var stock=p.getInventory().getItem(slot);p.getInventory().setItem(slot,p.getMainHandItem());p.setItemInHand(InteractionHand.MAIN_HAND,stock);p.containerMenu.broadcastChanges();});}
    private static void verify(ClientGameTestContext context){
        // Native distance admission follows the renderer camera, which can lag a teleport packet.
        context.waitFor(client->client.level.getBlockEntity(P) instanceof TrackBlockEntity first&&client.level.getBlockEntity(Q) instanceof TrackBlockEntity second&&client.getBlockEntityRenderDispatcher().tryExtractRenderState(first,.5f,null,true)!=null&&client.getBlockEntityRenderDispatcher().tryExtractRenderState(second,.5f,null,true)!=null,300);
        context.runOnClient(client->{var a=(TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true);var b=(TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(Q),.5f,null,true);if(a==null||a.primaryCurves!=1||a.segments<1||a.girderPieces!=a.segments*6||a.pieces.size()!=a.segments*9||b==null||!b.pieces.isEmpty())throw new AssertionError("Actual primary curve needs two source beams/four caps/two rails/ties per segment and no secondary draw: first="+(a==null?"null":a.primaryCurves+"/"+a.segments+"/"+a.girderPieces+"/"+a.pieces.size())+", second="+(b==null?"null":b.primaryCurves+"/"+b.segments+"/"+b.pieces.size())+", camera="+client.gameRenderer.mainCamera().position());for(var piece:a.pieces)if(piece.model().mesh==null||piece.model().mesh.size()==0||piece.model().isEmpty()||!piece.transform().isFinite()||piece.light()==0)throw new AssertionError("Native source girder/track frame is empty, invalid, or unlit");});}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int limit,String message){var ready=new AtomicBoolean();for(int i=0;i<limit;i++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(ready.get())return;context.waitTicks(1);}throw new AssertionError(message);}
    private static void look(ClientGameTestContext context,Vec3 point){var angles=context.computeOnClient(client->{var d=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angles.x,angles.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
