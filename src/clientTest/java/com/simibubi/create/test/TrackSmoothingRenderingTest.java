package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Proposed actual source preview/input -> source smoothing -> native quad deformation -> real save/unload/undo. */
final class TrackSmoothingRenderingTest {
    private static final BlockPos C=new BlockPos(25021,10,12),L=new BlockPos(25002,6,12),R=new BlockPos(25031,12,12);
    private record Vertex(float x,float y,float z,float u,float v){}
    private record Mesh(List<Vertex> vertices,double maxY,Object model,Object key){}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        boolean previouslyHidden=context.computeOnClient(client->client.gui.hud.isHidden());
        if(previouslyHidden)context.runOnClient(client->client.gui.hud.toggle());
        try{runVisible(context,world);}finally{context.runOnClient(client->{if(client.gui.hud.isHidden()!=previouslyHidden)client.gui.hud.toggle();});}
    }
    private static void runVisible(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 25021.5 15 15.5");
        context.waitFor(client->client.level.isLoaded(new BlockPos(25000,4,0))&&client.level.isLoaded(new BlockPos(25040,4,24)),400);
        server.runCommand("fill 25000 4 0 25040 4 24 minecraft:smooth_quartz");
        server.runCommand("fill 25000 5 14 25005 5 18 minecraft:smooth_quartz");server.runCommand("fill 25018 9 14 25024 9 18 minecraft:smooth_quartz");server.runCommand("fill 25028 11 14 25034 11 18 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var pos:List.of(C,L,R)){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.SMOOTH_QUARTZ.defaultBlockState());level.setBlockAndUpdate(pos,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO));}});
        server.runCommand("gamemode survival @p");server.runCommand("clear @p");server.runOnServer(ignored->{var p=actual(world.getConnection().getServerLevel());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.TRACK,64));p.getInventory().setItem(9,new ItemStack(AllBlocks.TRACK,64));p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AllBlocks.METAL_GIRDER,64));p.getInventory().setItem(10,new ItemStack(AllBlocks.METAL_GIRDER,64));p.containerMenu.broadcastChanges();});
        context.waitFor(client->count(client.player,AllBlocks.TRACK.asItem())==128&&count(client.player,AllBlocks.METAL_GIRDER.asItem())==128,200);
        var lowPaid=new AtomicInteger();var highPaid=new AtomicInteger();build(context,world,L,lowPaid,true);
        var flat=context.computeOnClient(TrackSmoothingRenderingTest::mesh);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(TrackBlockEntity)level.getBlockEntity(C);if(owner.isTilted()||owner.getConnections().size()!=1)throw new AssertionError("One actual ascending curve fabricated smoothing");});
        build(context,world,R,highPaid,false);
        context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity be&&be.isTilted()&&be.getConnections().size()==2,300);
        verify(context,flat);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(TrackBlockEntity)level.getBlockEntity(C);var low=owner.getConnections().get(L);var p=actual(level);if(low.getTrackItemCost()==lowPaid.get())throw new AssertionError("Real native source fixture did not reproduce the fresh-geometry source item-cost drift");if(count(p,AllBlocks.TRACK.asItem())+lowPaid.get()+highPaid.get()!=128||count(p,AllBlocks.METAL_GIRDER.asItem())+(lowPaid.get()+highPaid.get())*2!=128)throw new AssertionError("Actual source smoothing changed already-paid native inventory");});
        view(context,world);world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-track-real-opposed-slope-smoothing-native-deformed-source-model");
        var before=context.computeOnClient(TrackSmoothingRenderingTest::mesh);var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();verify(context,flat);
        context.runOnClient(client->{var after=mesh(client);if(after.model()==before.model()||!after.vertices().equals(before.vertices()))throw new AssertionError("Native actual resource reload failed to replace wrapper identity while retaining exact source deformed positions/UVs");});
        var originalEndpoints=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();return new net.minecraft.world.level.block.entity.BlockEntity[]{level.getBlockEntity(C),level.getBlockEntity(L),level.getBlockEntity(R)};});
        server.runCommand("save-all flush");server.runCommand("gamemode spectator @p");server.runCommand("tp @p 26400 20 26400");
        waitServer(context,world,level->List.of(C,L,R).stream().allMatch(pos->level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null),900,"Actual three native slope endpoint chunks did not unload");
        NativeFixtureChunkUnload.finish(context,world,originalEndpoints);
        context.waitFor(client->List.of(C,L,R).stream().allMatch(pos->client.level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null),400);
        server.runCommand("tp @p 25021.5 15 15.5");context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity a&&a.isTilted()&&a.getConnections().size()==2&&client.level.getBlockEntity(L) instanceof TrackBlockEntity&&client.level.getBlockEntity(R) instanceof TrackBlockEntity,500);verify(context,flat);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var positions=List.of(C,L,R);for(int i=0;i<positions.size();i++)if(level.getBlockEntity(positions.get(i))==originalEndpoints[i])throw new AssertionError("Native slope return reused an unretired original endpoint BE");});
        context.runOnClient(client->{if(!mesh(client).vertices().equals(before.vertices()))throw new AssertionError("Actual native chunk return changed source smoothed block geometry/UVs");});world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-track-real-three-endpoint-unload-return-preserves-smoothing");
        server.runCommand("gamemode survival @p");giveWrench(world);context.waitFor(client->client.player.getMainHandItem().is(AllItems.WRENCH),200);var midpoint=context.computeOnClient(client->((TrackBlockEntity)client.level.getBlockEntity(C)).getConnections().get(L).getPosition(.5));
        server.runCommand("tp @p "+midpoint.x+" 5 "+(midpoint.z+3));context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-midpoint.x)<.1,200);look(context,midpoint);
        context.getInput().holdKey(options->options.keyShift);
        try{
            context.waitFor(client->client.player.isShiftKeyDown()&&client.player.isCrouching(),200);
            waitServer(context,world,level->actual(level).isShiftKeyDown()&&actual(level).isCrouching(),100,"Actual native server did not acknowledge crouched wrench input");
            // Crouching changes the real eye height. Aim and acknowledge the exact
            // source curve from that actual pose before pressing the native use key.
            look(context,midpoint);
            context.waitFor(client->TrackBlockOutline.result()!=null&&TrackBlockOutline.result().owner().equals(C)&&TrackBlockOutline.result().target().equals(L),200);
            waitServer(context,world,level->{var p=actual(level);var curve=((TrackBlockEntity)level.getBlockEntity(C)).getConnections().get(L);return TrackCurveSelection.trace(curve,p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(p.blockInteractionRange()+1)))!=null;},100,"Actual native crouched server player ray did not acknowledge curve view");
            context.getInput().pressKey(options->options.keyUse);
            context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity owner&&!owner.isTilted()&&owner.getConnections().size()==1&&!owner.getConnections().containsKey(L),300);
        }catch(AssertionError failure){
            var client=context.computeOnClient(mc->"pose="+mc.player.getPose()+", shift="+mc.player.isShiftKeyDown()+", eye="+mc.player.getEyePosition()+", ray="+TrackBlockOutline.result()+", vanillaHit="+mc.hitResult);
            var actual=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actual(level);var owner=(TrackBlockEntity)level.getBlockEntity(C);return "pose="+p.getPose()+", shift="+p.isShiftKeyDown()+", eye="+p.getEyePosition()+", tool="+p.getMainHandItem()+", tilted="+(owner!=null&&owner.isTilted())+", connections="+(owner==null?null:owner.getConnections().keySet());});
            throw new AssertionError("Actual crouched slope wrench did not complete; client=["+client+"], server=["+actual+"]",failure);
        }finally{context.getInput().releaseKey(options->options.keyShift);}
        context.runOnClient(client->{var after=mesh(client);if(!after.vertices().equals(flat.vertices()))throw new AssertionError("Actual source undo did not rebake original native flat positions/UVs");});
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actual(level);if(count(p,AllBlocks.TRACK.asItem())+highPaid.get()!=128||count(p,AllBlocks.METAL_GIRDER.asItem())+highPaid.get()*2!=128)throw new AssertionError("Source-bug-fix review: actual saved smoothed curve wrench must recover original paid material quantities");});
        world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-track-native-curve-wrench-undo-restores-original-flat-source-model");
        server.runCommand("tp @p 25021.5 10 15.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-25021.5)<.1,200);look(context,Vec3.atBottomCenterOf(C).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(C)&&hit.getType()==HitResult.Type.BLOCK,200);context.getInput().holdKey(options->options.keyShift);
        try{
            context.waitFor(client->client.player.isShiftKeyDown()&&client.player.isCrouching(),200);
            waitServer(context,world,level->actual(level).isShiftKeyDown()&&actual(level).isCrouching(),100,"Actual native server did not acknowledge crouched endpoint wrench input");
            look(context,Vec3.atBottomCenterOf(C).add(0,.1,0));
            context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(C)&&hit.getType()==HitResult.Type.BLOCK,200);
            waitServer(context,world,level->{var p=actual(level);return p.pick(p.blockInteractionRange(),1,false) instanceof BlockHitResult hit&&hit.getBlockPos().equals(C);},100,"Actual native crouched server ray did not reach the original physical endpoint");
            context.getInput().pressKey(options->options.keyUse);
            context.waitFor(client->client.level.getBlockState(C).isAir()&&client.level.getBlockEntity(R)==null,300);
        }catch(AssertionError failure){
            var client=context.computeOnClient(mc->"pose="+mc.player.getPose()+", shift="+mc.player.isShiftKeyDown()+", eye="+mc.player.getEyePosition()+", ray="+mc.hitResult+", target="+mc.level.getBlockState(C));
            var state=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();var p=actual(level);return "pose="+p.getPose()+", shift="+p.isShiftKeyDown()+", eye="+p.getEyePosition()+", ray="+p.pick(p.blockInteractionRange(),1,false)+", tool="+p.getMainHandItem()+", target="+level.getBlockState(C);});
            throw new AssertionError("Actual crouched endpoint wrench did not complete; client=["+client+"], server=["+state+"]",failure);
        }finally{context.getInput().releaseKey(options->options.keyShift);}
        server.runOnServer(ignored->{var p=actual(world.getConnection().getServerLevel());if(count(p,AllBlocks.TRACK.asItem())!=129||count(p,AllBlocks.METAL_GIRDER.asItem())!=128)throw new AssertionError("Actual native source endpoint wrench did not conserve all128paid materials plus original physical center track");});
    }
    private static void build(ClientGameTestContext context,TestSingleplayerContext world,BlockPos target,AtomicInteger paid,boolean inspectPreview){
        var server=world.getServer();server.runCommand("tp @p 25021.5 10 15.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-25021.5)<.1,200);look(context,Vec3.atBottomCenterOf(C).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(C)&&hit.getType()==HitResult.Type.BLOCK,200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),200);
        server.runCommand("tp @p "+(target.getX()+.5)+" "+target.getY()+" 15.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-target.getX()-.5)<.1,200);look(context,Vec3.atBottomCenterOf(target).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(target),200);
        context.getInput().holdKey(options->options.keySprint);
        try{
            context.waitFor(client->TrackPlacementPreview.latest()!=null&&target.equals(TrackPlacementPreview.latest().target())&&TrackPlacementPreview.latest().valid()&&TrackPlacementPreview.latest().maximised()&&TrackPlacementPreview.latest().lines()>4&&TrackPlacementPreview.latest().hintQuads()>0,200);
            if(inspectPreview){context.waitTicks(45);screenshot(context,"create-vulkan-track-real-source-preview-native-rails-checker-hints-and-paid-costs");server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(level.getBlockEntity(C)!=null||level.getBlockEntity(target)!=null||count(actual(level),AllBlocks.TRACK.asItem())!=128||count(actual(level),AllBlocks.METAL_GIRDER.asItem())!=128)throw new AssertionError("Actual preview mutated native world or original inventory before input");});}
            var before=new AtomicInteger();server.runOnServer(ignored->before.set(count(actual(world.getConnection().getServerLevel()),AllBlocks.TRACK.asItem())));
            context.getInput().holdKey(options->options.keyUp);
            try{context.waitFor(client->client.player.isSprinting(),80);waitServer(context,world,level->actual(level).isSprinting(),80,"Actual player input never reached source server sprint state");context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity be&&be.getConnections().containsKey(target),250);}finally{context.getInput().releaseKey(options->options.keyUp);}
            server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(TrackBlockEntity)level.getBlockEntity(C);var turn=owner.getConnections().get(target);var p=actual(level);paid.set(before.get()-count(p,AllBlocks.TRACK.asItem()));if(turn==null||!turn.hasGirder||!turn.bePositions.getFirst().equals(C)||!turn.bePositions.getSecond().equals(target)||paid.get()<=0)throw new AssertionError("Native actual sprint/item input did not construct genuine paid endpoint curve");});
        }finally{context.getInput().releaseKey(options->options.keySprint);}
        context.waitFor(client->!client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),200);
    }
    private static Mesh mesh(Minecraft client){var state=client.level.getBlockState(C);var model=client.getModelManager().getBlockStateModelSet().get(state);if(!(model instanceof TrackModel))throw new AssertionError("Actual native track state is missing genuine source deformation wrapper");var mutable=Renderer.get().mutableMesh();model.emitQuads(mutable.emitter(),client.level,C,state,net.minecraft.util.RandomSource.create(31),side->false);var vertices=new ArrayList<Vertex>();double[] max={Double.NEGATIVE_INFINITY};mutable.immutableCopy().forEach(quad->{for(int i=0;i<4;i++){vertices.add(new Vertex(quad.x(i),quad.y(i),quad.z(i),quad.u(i),quad.v(i)));max[0]=Math.max(max[0],quad.y(i));if(quad.hasNormal(i)){double x=quad.normalX(i),y=quad.normalY(i),z=quad.normalZ(i);if(!Double.isFinite(x+y+z)||Math.abs(x*x+y*y+z*z-1)>.02)throw new AssertionError("Native source deformed normal is nonfinite or not normalized");}}});if(vertices.isEmpty())throw new AssertionError("Actual native source track quad mesh is empty");return new Mesh(List.copyOf(vertices),max[0],model,model.createGeometryKey(client.level,C,state,net.minecraft.util.RandomSource.create(31)));}
    private static void verify(ClientGameTestContext context,Mesh flat){context.waitFor(client->client.level.getBlockEntity(C) instanceof TrackBlockEntity owner&&client.getBlockEntityRenderDispatcher().tryExtractRenderState(owner,.5f,null,true) instanceof TrackRenderer.State,300);context.runOnClient(client->{var current=mesh(client);if(current.vertices().size()!=flat.vertices().size()||current.maxY()<flat.maxY()+.05)throw new AssertionError("Actual source angle failed to deform native block rail height with original face count");for(int i=0;i<flat.vertices().size();i++)if(current.vertices().get(i).u()!=flat.vertices().get(i).u()||current.vertices().get(i).v()!=flat.vertices().get(i).v())throw new AssertionError("Source deformation modified actual original model UVs");if(current.key()!=null&&current.key().equals(flat.key()))throw new AssertionError("Native geometry cache key omitted actual immutable slope data");var owner=(TrackBlockEntity)client.level.getBlockEntity(C);var low=owner.getConnections().get(L);var high=owner.getConnections().get(R);if(!owner.isTilted()||low.axes.getFirst().y>=0||high.axes.getFirst().y<=0||high.smoothing.getFirst()<=0)throw new AssertionError("Actual native source sloped rail data is missing");var state=(TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(owner,.5f,null,true);if(state.primaryCurves!=2||state.segments<2||state.pieces.size()!=state.segments*9||state.girderPieces!=state.segments*6)throw new AssertionError("Native actual rail/girder renderer did not consume both source smoothed curves");for(var piece:state.pieces)if(piece.model().isEmpty()||!piece.transform().isFinite()||piece.light()==0)throw new AssertionError("Source smoothed curve native mesh/frame/light snapshot invalid");});}
    private static Player actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static int count(Player p,Item item){int total=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))total+=p.getInventory().getItem(i).getCount();return total;}
    private static void giveWrench(TestSingleplayerContext world){world.getServer().runOnServer(ignored->{var p=actual(world.getConnection().getServerLevel());int slot=p.getInventory().getFreeSlot();if(slot<0)throw new AssertionError("No genuine empty stock fixture slot");p.getInventory().setItem(slot,p.getMainHandItem());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));p.containerMenu.broadcastChanges();});}
    private static void view(ClientGameTestContext context,TestSingleplayerContext world){world.getServer().runCommand("tp @p 25021.5 10 15.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-25021.5)<.1&&Math.abs(client.player.getZ()-15.5)<.1,200);look(context,Vec3.atBottomCenterOf(C).add(0,.2,0));}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int limit,String message){var ready=new AtomicBoolean();for(int i=0;i<limit;i++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(ready.get())return;context.waitTicks(1);}throw new AssertionError(message);}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var d=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
