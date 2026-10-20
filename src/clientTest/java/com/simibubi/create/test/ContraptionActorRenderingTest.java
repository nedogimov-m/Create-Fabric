package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Actual actor work/storage menus/native animation/reload and occupied-storage entity unload. */
final class ContraptionActorRenderingTest {
    private static final BlockPos P=new BlockPos(3791,5,5),Q=P.south(8),LOCAL_BARREL=new BlockPos(1,0,0);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 3793.5 9 -1.5");
        context.waitFor(client->Math.abs(client.player.getX()-3793.5)<.1&&client.level.isLoaded(P)&&client.level.isLoaded(P.east(2)),400);
        server.runCommand("fill 3786 3 -1 3799 3 19 minecraft:smooth_quartz");
        setup(context,world,P,"create:mechanical_drill[facing=north]","minecraft:iron_block",true);
        var drill=new AtomicReference<UUID>();
        waitServer(context,world,level->{var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(P.below());var owner=bearing.getMovedContraption();if(owner==null||!owner.isStalled())return false;drill.set(owner.getUUID());return owner.getContraption().getActors().getFirst().right.data.getIntOr("Progress",0)>0;},300,"Actual drill never started breaking");
        context.waitFor(client->find(client.level,drill.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&owner.isStalled(),300);context.waitTicks(2);look(context,Vec3.atCenterOf(P.east(2)));
        var angle=context.computeOnClient(client->toolState(client,drill.get()).angle);context.waitTicks(2);
        context.runOnClient(client->{var state=toolState(client,drill.get());if(Math.abs(net.minecraft.util.Mth.wrapDegrees(state.angle-angle))<1)throw new AssertionError("Moving drill has a static native head");if(state.tool.mesh==null||state.tool.mesh.size()==0)throw new AssertionError("Actor head did not extract native geometry");});screenshot(context,world,"create-vulkan-actor-drill-breaking");
        var savedProgress=new AtomicInteger();var savedBreaker=new AtomicInteger();var savedAngle=new AtomicReference<Float>();
        server.runOnServer(ignored->{var owner=(ControlledContraptionEntity)world.getConnection().getServerLevel().getEntity(drill.get());var ctx=owner.getContraption().getActors().getFirst().right;savedProgress.set(ctx.data.getIntOr("Progress",0));savedBreaker.set(ctx.data.getIntOr("BreakerId",0));savedAngle.set(owner.getAngle(1));});
        server.runCommand("tp @p 5000 10 5000");waitServer(context,world,level->level.getEntity(drill.get())==null&&level.getChunkSource().getChunkNow(P.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(P.east(2).getX()>>4,0)==null,800,"Working drill retained its source chunks");
        server.runCommand("tp @p 3793.5 9 -1.5");waitServer(context,world,level->level.getEntity(drill.get()) instanceof ControlledContraptionEntity,500,"Working owner did not return with same UUID");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var owner=(ControlledContraptionEntity)level.getEntity(drill.get());var ctx=owner.getContraption().getActors().getFirst().right;
            if(!owner.isStalled()||!level.getBlockState(P.east(2).north()).is(net.minecraft.world.level.block.Blocks.IRON_BLOCK)||ctx.data.getIntOr("Progress",0)<savedProgress.get()||ctx.data.getIntOr("Progress",0)<1)throw new AssertionError("Native unload failed to retain unfinished work: "+ctx.data);
            if(ctx.data.getIntOr("BreakerId",0)==savedBreaker.get()||Math.abs(owner.getAngle(1)-savedAngle.get())>.001)throw new AssertionError("Reload lost stalled pose or renewed overlay identity");});
        context.waitFor(client->find(client.level,drill.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&owner.isStalled(),300);look(context,Vec3.atCenterOf(P.east(2)));screenshot(context,world,"create-vulkan-actor-unfinished-drill-unload-return");
        waitServer(context,world,level->{var owner=(ControlledContraptionEntity)level.getEntity(drill.get());return level.getBlockState(P.east(2).north()).isAir()&&!owner.isStalled()&&count(owner,Items.IRON_BLOCK)==1;},400,"Drill did not break, collect and resume");
        pause(world,P);context.waitTicks(8);look(context,Vec3.atCenterOf(P.east(2)));screenshot(context,world,"create-vulkan-actor-drill-work-complete");
        openMovingBarrel(context,world,drill.get());context.waitFor(client->client.player.containerMenu instanceof ChestMenu menu&&menu.getSlot(0).getItem().is(Items.IRON_BLOCK)&&menu.getSlot(0).getItem().getCount()==1&&menu.getSlot(4).getItem().is(Items.DIAMOND)&&menu.getSlot(4).getItem().getCount()==7,200);screenshot(context,world,"create-vulkan-actor-drill-collected-native-menu");context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,100);
        var oldMesh=context.computeOnClient(client->toolState(client,drill.get()).tool.mesh);var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone(),500);reload.join();context.waitTicks(3);context.runOnClient(client->{if(toolState(client,drill.get()).tool.mesh==oldMesh)throw new AssertionError("Actor retained stale geometry after resource reload");});screenshot(context,world,"create-vulkan-actor-drill-after-reload");
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 3793.5 9 8.5");context.waitFor(client->client.player.getZ()>8&&client.level.isLoaded(Q.east(2)),200);setup(context,world,Q,"create:mechanical_saw[facing=north]","minecraft:oak_log",false);server.runCommand("fill 3793 6 12 3793 8 12 minecraft:oak_log");
        var saw=new AtomicReference<UUID>();waitServer(context,world,level->{var owner=((MechanicalBearingBlockEntity)level.getBlockEntity(Q.below())).getMovedContraption();if(owner==null)return false;saw.set(owner.getUUID());return owner.isStalled();},300,"Actual moving saw never stalled on tree");
        context.waitFor(client->find(client.level,saw.get()) instanceof ControlledContraptionEntity owner&&owner.getContraption()!=null&&owner.isStalled(),200);look(context,Vec3.atCenterOf(Q.east(2).north().above()));context.runOnClient(client->{if(toolState(client,saw.get()).tool.mesh.size()==0)throw new AssertionError("Moving saw blade has no native geometry");});screenshot(context,world,"create-vulkan-actor-saw-felling");
        waitServer(context,world,level->{var owner=(ControlledContraptionEntity)level.getEntity(saw.get());for(int y=0;y<4;y++)if(!level.getBlockState(Q.east(2).north().above(y)).isAir())return false;return count(owner,Items.OAK_LOG)==4&&!owner.isStalled();},500,"Saw failed actual tree/collection");pause(world,Q);context.waitTicks(8);screenshot(context,world,"create-vulkan-actor-saw-tree-collected");
        server.runCommand("tp @p 5000 10 5000");waitServer(context,world,level->level.getEntity(drill.get())==null&&level.getEntity(saw.get())==null&&level.getChunkSource().getChunkNow(P.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(P.east(2).getX()>>4,0)==null,800,"Actor owners retained source chunks");
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 3793.5 9 8.5");context.waitFor(client->find(client.level,drill.get()) instanceof ControlledContraptionEntity a&&a.getContraption()!=null&&find(client.level,saw.get()) instanceof ControlledContraptionEntity b&&b.getContraption()!=null,500);world.getConnection().waitForChunksRender();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var a=(ControlledContraptionEntity)level.getEntity(drill.get());var b=(ControlledContraptionEntity)level.getEntity(saw.get());if(count(a,Items.IRON_BLOCK)!=1||count(a,Items.DIAMOND)!=7||count(b,Items.OAK_LOG)!=4)throw new AssertionError("Actor loot changed during actual save/unload/return");});look(context,Vec3.atCenterOf(Q));screenshot(context,world,"create-vulkan-actor-storage-unload-return");
        disassemble(context,world,P,drill.get(),Items.IRON_BLOCK,1);disassemble(context,world,Q,saw.get(),Items.OAK_LOG,4);screenshot(context,world,"create-vulkan-actor-collected-world-storage");server.runCommand("gamemode spectator @p");
    }
    private static void setup(ClientGameTestContext context,TestSingleplayerContext world,BlockPos p,String tool,String target,boolean diamonds){
        var server=world.getServer();server.runCommand("setblock "+xyz(p.below())+" create:mechanical_bearing[facing=up]");server.runCommand("setblock "+xyz(p)+" minecraft:stone");server.runCommand("setblock "+xyz(p.east())+" minecraft:barrel[facing=north]");server.runCommand("setblock "+xyz(p.east(2))+" "+tool);server.runCommand("setblock "+xyz(p.east(2).north())+" "+target);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(diamonds)((BarrelBlockEntity)level.getBlockEntity(p.east())).setItem(4,new ItemStack(Items.DIAMOND,7));((MechanicalBearingBlockEntity)level.getBlockEntity(p.below())).setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(p,p.east(2))));});
        server.runCommand("setblock "+xyz(p.below(2))+" create:creative_motor[facing=up]");
    }
    private static void pause(TestSingleplayerContext world,BlockPos p){world.getServer().runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(p.below(2))).setGeneratedSpeed(0));}
    private static void openMovingBarrel(ClientGameTestContext context,TestSingleplayerContext world,UUID id){
        var origin=context.computeOnClient(client->((ControlledContraptionEntity)find(client.level,id)).toGlobalVector(Vec3.atCenterOf(LOCAL_BARREL).add(0,0,-3),1));
        world.getServer().runCommand("tp @p "+origin.x+" 4 "+origin.z);world.getServer().runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&Math.abs(client.player.getX()-origin.x)<.1&&Math.abs(client.player.getZ()-origin.z)<.1,200);
        var target=context.computeOnClient(client->((ControlledContraptionEntity)find(client.level,id)).toGlobalVector(Vec3.atCenterOf(LOCAL_BARREL),1));look(context,target);context.waitTicks(2);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen,200);
    }
    private static void disassemble(ClientGameTestContext context,TestSingleplayerContext world,BlockPos p,UUID id,Item item,int count){
        var barrel=new AtomicReference<BlockPos>();world.getServer().runOnServer(ignored->{var entity=(ControlledContraptionEntity)world.getConnection().getServerLevel().getEntity(id);barrel.set(new StructureTransform(p,0,entity.getAngle(1),0).apply(LOCAL_BARREL));});
        world.getServer().runCommand("tp @p "+(p.getX()+.5)+" 4 "+(p.getZ()+4.5));world.getServer().runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&Math.abs(client.player.getZ()-p.getZ()-4.5)<.1,200);look(context,Vec3.atCenterOf(p.below()));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(p.below()),150);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->find(client.level,id)==null,200);
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(!(level.getBlockEntity(barrel.get()) instanceof BarrelBlockEntity storage))throw new AssertionError("Actual barrel not restored");int total=0;for(int i=0;i<storage.getContainerSize();i++)if(storage.getItem(i).is(item))total+=storage.getItem(i).getCount();if(total!=count)throw new AssertionError("Disassembly changed actor loot: "+total);});
    }
    private static CuttingToolRenderer.State toolState(net.minecraft.client.Minecraft client,UUID id){var owner=(ControlledContraptionEntity)find(client.level,id);var renderer=(ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(owner);var extracted=renderer.createRenderState(owner,.5f);if(extracted.actors.size()!=1)throw new AssertionError("Actor extraction count: "+extracted.actors.size());if(extracted.blockEntities.stream().anyMatch(be->be.pos().equals(new BlockPos(2,0,0))))throw new AssertionError("Ordinary BE rendering duplicated mounted tools");return ((CuttingToolActorRenderer.State)extracted.actors.getFirst().state()).state();}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String failure){var ready=new AtomicBoolean();for(int i=0;i<ticks&&!ready.get();i++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(failure);}
    private static net.minecraft.world.entity.Entity find(net.minecraft.client.multiplayer.ClientLevel level,UUID uuid){if(level==null)return null;for(var entity:level.entitiesForRendering())if(entity.getUUID().equals(uuid))return entity;return null;}
    private static long count(ControlledContraptionEntity owner,Item item){long count=0;for(var view:owner.getContraption().getStorage().getAllItems())if(view.getResource().isOf(item))count+=view.getAmount();return count;}
    private static String xyz(BlockPos p){return p.getX()+" "+p.getY()+" "+p.getZ();}
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){
        NativeScreenshotReadiness.await(context,world);
        context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);
    }
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var d=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angle.x,angle.y);}
}
