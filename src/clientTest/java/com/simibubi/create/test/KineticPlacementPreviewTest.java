package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmSettingsScreen;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.placement.KineticPlacementPreview;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Always runs the actual native preview/input/payment cases. Authored source-only, UNRUN. */
public final class KineticPlacementPreviewTest implements FabricClientGameTest {
    private static final Path REPORT=Path.of(System.getProperty("create.test.kineticPreviewReport","build/reports/create-kinetic-preview/native-client-runtime.json")).toAbsolutePath();
    private record Scene(String name,BlockState source,Block held,BlockPos offset,Direction.Axis expectedAxis,int shaftLine,InteractionHand hand,boolean competingOffhand) {}
    private record Phase(String name,KineticPlacementPreviewAudit.Receipt nativeFrame,String screenshot,String screenshotSHA256) {}
    private static final class Report {
        String status="RUNNING",backend,device,failure;boolean sodiumLoaded,actualReloadCompleted,actualSourceBakedModelRecreated;
        int actualNativePlacements,actualPaymentChecks,actualMainBeforeOffhandChecks;final List<Phase> phases=new ArrayList<>();
    }
    @Override public void runTest(ClientGameTestContext context){
        var report=new Report();var camera=new AtomicReference<CameraType>();var hidden=new AtomicReference<Boolean>();
        try(var world=context.worldBuilder().adjustSettings(settings->settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()){
            context.runOnClient(client->{
                var actual=RenderSystem.getDevice().getDeviceInfo();report.backend=actual.backendName();report.device=actual.name();report.sodiumLoaded=FabricLoader.getInstance().isModLoaded("sodium");
                require(report.backend.toLowerCase(Locale.ROOT).contains("vulkan")&&!report.sodiumLoaded,"Actual requested Vulkan backend and absent Sodium required");
                camera.set(client.options.getCameraType());client.options.setCameraType(CameraType.FIRST_PERSON);hidden.set(client.gui.hud.isHidden());if(client.gui.hud.isHidden())client.gui.hud.toggle();
            });write(report);
            var scenes=List.of(
                new Scene("small-cardinal",cog(false),AllBlocks.COGWHEEL,new BlockPos(0,0,1),Direction.Axis.Y,0,InteractionHand.MAIN_HAND,false),
                new Scene("mixed-diagonal-main-before-off",cog(false),AllBlocks.LARGE_COGWHEEL,new BlockPos(1,0,1),Direction.Axis.Y,0,InteractionHand.MAIN_HAND,true),
                new Scene("large-perpendicular",cog(true),AllBlocks.LARGE_COGWHEEL,new BlockPos(0,1,1),Direction.Axis.Z,0,InteractionHand.MAIN_HAND,false),
                new Scene("shaft-line",AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X),AllBlocks.SHAFT,new BlockPos(3,0,0),Direction.Axis.X,3,InteractionHand.MAIN_HAND,false),
                new Scene("empty-main-offhand",cog(false),AllBlocks.COGWHEEL,new BlockPos(0,0,1),Direction.Axis.Y,0,InteractionHand.OFF_HAND,false),
                new Scene("integrated-primary-action",AllBlocks.ANDESITE_ENCASED_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y),AllBlocks.LARGE_COGWHEEL,new BlockPos(1,0,1),Direction.Axis.Y,0,InteractionHand.MAIN_HAND,false));
            for(int i=0;i<scenes.size();i++)runScene(context,world,scenes.get(i),i,report);
            ordinaryMainPlacementPreventsOffhandGhostAndAction(context,world,report);
            emptyMainBlockSettingsPreventsOffhandGhostAndAction(context,world,report);
            require(report.actualNativePlacements==6&&report.actualPaymentChecks==6&&report.actualMainBeforeOffhandChecks==2&&report.phases.size()==7&&report.actualReloadCompleted&&report.actualSourceBakedModelRecreated,"All actual accepted targets/finite payments, native MAIN-before-OFF interactions and seven native preview screenshot phases are required");report.status="PASS";
        }catch(Throwable failure){report.status="FAIL";var text=new StringWriter();failure.printStackTrace(new PrintWriter(text));report.failure=text.toString();if(failure instanceof AssertionError assertion)throw assertion;throw new AssertionError(failure);}
        finally{context.getInput().releaseKey(options->options.keyShift);context.runOnClient(client->{KineticPlacementPreviewAudit.clear();if(camera.get()!=null)client.options.setCameraType(camera.get());if(hidden.get()!=null&&client.gui.hud.isHidden()!=hidden.get())client.gui.hud.toggle();});write(report);}
    }
    private static void ordinaryMainPlacementPreventsOffhandGhostAndAction(ClientGameTestContext context,TestSingleplayerContext world,Report report){
        var server=world.getServer();var source=new BlockPos(84,4,4);var target=source.south();var initial=cog(false);
        preparePriorityScene(context,world,source,initial,AllBlocks.SHAFT);
        look(context,Vec3.atLowerCornerOf(source).add(.85,.55,.85));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(source)&&hit.getDirection()==Direction.SOUTH,200);
        awaitNoOffhandPreview(context);
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            require(level.getBlockState(target).isAir()&&player.getMainHandItem().getCount()==8&&player.getOffhandItem().getCount()==8,"Suppressed offhand preview cannot mutate native world or either finite hand");
        });
        int[] before=new int[2];server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();before[0]=player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.SHAFT.asItem()));before[1]=player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.COGWHEEL.asItem()));});
        context.getInput().pressKey(options->options.keyUse);
        var expected=AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z).setValue(ShaftBlock.WATERLOGGED,false);
        context.waitFor(client->client.level.getBlockState(target)==expected&&client.player.getMainHandItem().getCount()==7,300);
        boolean authoritative=false;
        for(int tick=0;tick<300&&!authoritative;tick++){
            authoritative=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();return level.getBlockState(source)==initial&&level.getBlockState(target)==expected&&level.getBlockEntity(target)!=null&&player.getMainHandItem().getCount()==7&&player.getOffhandItem().getCount()==8;});
            if(!authoritative)context.waitTicks(1);
        }
        require(authoritative,"Actual authoritative MAIN ordinary placement/payment did not arrive within300 ticks");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            require(level.getBlockState(source)==initial&&level.getBlockState(target)==expected&&level.getBlockEntity(target)!=null,"Actual MAIN ordinary shaft placement wins before a competing OFF cog helper");
            require(player.getMainHandItem().is(AllBlocks.SHAFT.asItem())&&player.getMainHandItem().getCount()==7&&player.getOffhandItem().is(AllBlocks.COGWHEEL.asItem())&&player.getOffhandItem().getCount()==8,"Only actual ordinary MAIN placement pays one item; OFF stock remains untouched");
            require(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.SHAFT.asItem()))==before[0]+1&&player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.COGWHEEL.asItem()))==before[1],"Actual native full input awards one MAIN item-use statistic and no OFF statistic");
        });report.actualMainBeforeOffhandChecks++;write(report);
    }
    private static void emptyMainBlockSettingsPreventsOffhandGhostAndAction(ClientGameTestContext context,TestSingleplayerContext world,Report report){
        var server=world.getServer();var source=new BlockPos(96,4,4);var target=source.south();var initial=AllBlocks.MECHANICAL_ARM.defaultBlockState();
        preparePriorityScene(context,world,source,initial,null);
        look(context,Vec3.atLowerCornerOf(source).add(.5,3/16d,1));
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(source)&&ArmBlock.settingsHit(initial,source,hit),200);
        awaitNoOffhandPreview(context);
        context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.gui.screen() instanceof ArmSettingsScreen settings&&settings.position().equals(source),300);
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            require(level.getBlockState(source)==initial&&level.getBlockState(target).isAir()&&level.getBlockEntity(target)==null,"Actual empty-MAIN block settings action precedes OFF placement and preserves the empty target");
            require(player.getMainHandItem().isEmpty()&&player.getOffhandItem().is(AllBlocks.COGWHEEL.asItem())&&player.getOffhandItem().getCount()==8,"Actual server-owned settings action consumes no OFF cog stock");
        });
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,200);
        report.actualMainBeforeOffhandChecks++;write(report);
    }
    private static void preparePriorityScene(ClientGameTestContext context,TestSingleplayerContext world,BlockPos source,BlockState state,Block main){
        var server=world.getServer();server.runCommand("gamemode creative @p");server.runCommand("tp @p "+(source.getX()+.5)+" 5 7.5");
        context.waitFor(client->client.level.isLoaded(source.offset(-3,0,-3))&&client.level.isLoaded(source.offset(7,0,7)),300);
        server.runCommand("fill "+(source.getX()-3)+" 3 0 "+(source.getX()+7)+" 3 12 minecraft:smooth_quartz");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            require(level.setBlockAndUpdate(source,state)&&level.getBlockState(source.south()).isAir(),"Actual native priority fixture has one source and a genuinely empty OFF helper target");
            player.setItemInHand(InteractionHand.MAIN_HAND,main==null?ItemStack.EMPTY:new ItemStack(main,8));player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AllBlocks.COGWHEEL,8));player.containerMenu.broadcastChanges();
        });server.runCommand("tp @p "+(source.getX()+.5)+" 4 7.5");server.runCommand("gamemode survival @p");
        context.waitFor(client->client.player.onGround()&&!client.player.isCreative()&&Math.abs(client.player.getX()-source.getX()-.5)<.1&&client.level.getBlockState(source)==state
            &&(main==null?client.player.getMainHandItem().isEmpty():client.player.getMainHandItem().is(main.asItem())&&client.player.getMainHandItem().getCount()==8)
            &&client.player.getOffhandItem().is(AllBlocks.COGWHEEL.asItem())&&client.player.getOffhandItem().getCount()==8,300);
    }
    private static void awaitNoOffhandPreview(ClientGameTestContext context){
        context.waitTicks(20);context.runOnClient(client->require(KineticPlacementPreview.predict(client)==null&&KineticPlacementPreview.latest()==null,"Native MAIN block/item pipeline has priority; no misleading OFF helper ghost may be extracted"));
    }
    private static void runScene(ClientGameTestContext context,TestSingleplayerContext world,Scene scene,int index,Report report){
        var server=world.getServer();var source=new BlockPos(index*12,4,4);var target=source.offset(scene.offset);
        server.runCommand("gamemode creative @p");server.runCommand("tp @p "+(source.getX()+.5)+" 5 7.5");
        context.waitFor(client->client.level.isLoaded(source.offset(-3,0,-3))&&client.level.isLoaded(source.offset(7,0,7)),300);
        server.runCommand("fill "+(source.getX()-3)+" 3 0 "+(source.getX()+7)+" 3 12 minecraft:smooth_quartz");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            require(level.setBlockAndUpdate(source,scene.source),"Actual native source owner placement required");
            for(int n=1;n<scene.shaftLine;n++)level.setBlockAndUpdate(source.east(n),scene.source);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.setItemInHand(scene.hand,new ItemStack(scene.held,8));if(scene.competingOffhand)player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AllBlocks.COGWHEEL,4));player.containerMenu.broadcastChanges();
        });server.runCommand("tp @p "+(source.getX()+.5)+" 4 7.5");server.runCommand("gamemode survival @p");
        context.waitFor(client->client.player.onGround()&&!client.player.isCreative()&&Math.abs(client.player.getX()-source.getX()-.5)<.1
            &&client.level.getBlockState(source)==scene.source&&client.player.getItemInHand(scene.hand).is(scene.held.asItem())&&client.player.getItemInHand(scene.hand).getCount()==8,300);
        Vec3 aim=Vec3.atLowerCornerOf(source).add(scene.shaftLine>0?.70:.85,.55,scene.shaftLine>0?.65:.85);look(context,aim);
        context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(source),200);
        var expected=scene.held.defaultBlockState().setValue(ShaftBlock.AXIS,scene.expectedAxis).setValue(ShaftBlock.WATERLOGGED,false);
        awaitPrediction(context,source,target,expected,scene.hand);context.waitTicks(20);
        context.runOnClient(client->assertUnchanged(client,source,target,scene));
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();require(level.getBlockState(source)==scene.source&&level.getBlockState(target).isAir()&&level.getBlockEntity(target)==null&&player.getItemInHand(scene.hand).getCount()==8,"Actual preview must not mutate native source/target/block-entity ownership or finite inventory");});
        capture(context,source,target,expected,scene.hand,scene.name,report);
        if(index==0){
            var before=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(expected));
            var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();report.actualReloadCompleted=true;
            context.runOnClient(client->{report.actualSourceBakedModelRecreated=client.getModelManager().getBlockStateModelSet().get(expected)!=before;require(report.actualSourceBakedModelRecreated,"Actual reload must replace the original baked native source block model");assertUnchanged(client,source,target,scene);});
            awaitPrediction(context,source,target,expected,scene.hand);capture(context,source,target,expected,scene.hand,scene.name+"-actual-resource-reload",report);
        }
        context.getInput().holdKey(options->options.keyShift);
        try{context.waitFor(client->client.player.isShiftKeyDown()&&KineticPlacementPreview.latest()==null,200);context.waitTicks(3);context.runOnClient(client->assertUnchanged(client,source,target,scene));}
        finally{context.getInput().releaseKey(options->options.keyShift);}
        context.waitFor(client->!client.player.isShiftKeyDown()&&!client.player.isCrouching(),200);look(context,aim);awaitPrediction(context,source,target,expected,scene.hand);
        context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockState(target)==expected&&client.player.getItemInHand(scene.hand).getCount()==7,300);
        // Native client prediction can become visible before the real input packet reaches the server.
        boolean authoritative=false;
        for(int tick=0;tick<300&&!authoritative;tick++){
            authoritative=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();return level.getBlockState(source)==scene.source&&level.getBlockState(target)==expected&&level.getBlockEntity(target)!=null&&player.getItemInHand(scene.hand).getCount()==7;});
            if(!authoritative)context.waitTicks(1);
        }
        require(authoritative,"Real server did not accept predicted native placement/payment for "+scene.name+" within300 ticks");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            require(level.getBlockState(source)==scene.source&&level.getBlockState(target)==expected&&level.getBlockEntity(target)!=null,"Real native key interaction must place the exact predicted accepted state and actual owner");
            require(player.getItemInHand(scene.hand).is(scene.held.asItem())&&player.getItemInHand(scene.hand).getCount()==7,"Actual accepted native placement must consume exactly one real held item");
            if(scene.competingOffhand)require(player.getOffhandItem().is(AllBlocks.COGWHEEL.asItem())&&player.getOffhandItem().getCount()==4,"Actual main helper has priority without touching real competing offhand stack");
        });report.actualNativePlacements++;report.actualPaymentChecks++;write(report);
    }
    private static void assertUnchanged(Minecraft client,BlockPos source,BlockPos target,Scene scene){
        require(client.level.getBlockState(source)==scene.source&&client.level.getBlockState(target).isAir()&&client.level.getBlockEntity(target)==null&&client.player.getItemInHand(scene.hand).getCount()==8,"Actual native client preview must retain source/empty target/absent target owner/original finite count");
    }
    private static void awaitPrediction(ClientGameTestContext context,BlockPos source,BlockPos target,BlockState state,InteractionHand hand){
        context.waitFor(client->{var frame=KineticPlacementPreview.latest();return frame!=null&&frame.animationTicks()==10&&frame.prediction().source().equals(source)&&frame.prediction().target().equals(target)&&frame.prediction().state()==state&&frame.prediction().hand()==hand;},300);
    }
    private static void capture(ClientGameTestContext context,BlockPos source,BlockPos target,BlockState state,InteractionHand hand,String phase,Report report){
        var watch=context.computeOnClient(client->KineticPlacementPreviewAudit.expect(phase,source,target,state,hand));
        context.waitFor(client->watch.failure()!=null||watch.completed()!=null,300);if(watch.failure()!=null)throw new AssertionError(watch.failure());
        context.waitFor(client->client.gui.overlay()==null&&client.gui.screen()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.waitTicks(2);
        var screenshot=context.takeScreenshot("create-vulkan-kinetic-placement-"+phase);String hash;
        try(var input=Files.newInputStream(screenshot)){var digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)digest.update(bytes,0,n);hash=HexFormat.of().formatHex(digest.digest());}catch(Exception error){throw new IllegalStateException(error);}
        report.phases.add(new Phase(phase,watch.completed(),screenshot.toAbsolutePath().toString(),hash));write(report);
    }
    private static BlockState cog(boolean large){return (large?AllBlocks.LARGE_COGWHEEL:AllBlocks.COGWHEEL).defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y);}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void write(Report report){try{Files.createDirectories(REPORT.getParent());Files.writeString(REPORT,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(IOException error){throw new UncheckedIOException(error);}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
