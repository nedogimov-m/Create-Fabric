package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

/** Genuine four item placements, two real shared boundaries, native mode input, physical forced-red,
 * native meshes/reload and actual four-chunk teardown/return. No Train/traffic success is fabricated.
 * Root-reserved37000..37064,z0..32; unload38400,20,38400. Runtime UNRUN.
 */
final class SignalRenderingTest {
    private static final BlockPos R=new BlockPos(37005,5,4),T=R.east(9),U=R.east(37);
    private static final BlockPos A=new BlockPos(37012,5,10),B=A.east(4),C=B.east(4),D=new BlockPos(37044,5,10);
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());
        if(hidden)context.runOnClient(client->client.gui.hud.toggle());
        try{runScene(context,world);}finally{context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});}
    }
    private static void runScene(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 37029.5 10.0 12.5");context.waitFor(client->client.level.isLoaded(R)&&client.level.isLoaded(R.east(48))&&Math.abs(client.player.getX()-37029.5)<.1&&Math.abs(client.player.getZ()-12.5)<.1,500);NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(37000, 4, 0), new BlockPos(37064, 4, 32), R, U, A, D);
        server.runCommand("fill 37000 4 0 37064 4 32 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(37000, 4, 0), new BlockPos(37064, 4, 32));
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);for(int x=0;x<=48;x++)level.setBlockAndUpdate(R.east(x),rail);for(int x=0;x<=48;x++)if(!TrackPropagator.reconcile(level,R.east(x),rail,false).complete())throw new AssertionError("Actual signal fixture rail discovery was incomplete");});
        slot(world,0,new ItemStack(AllBlocks.TRACK_SIGNAL,4));slot(world,1,ItemStack.EMPTY);place(context,world,T,A,true,3);place(context,world,T,B,true,2);place(context,world,T,C,false,1);place(context,world,U,D,true,0);
        waitServer(context,world,level->{var first=signal(level,A).getSignal();var other=signal(level,D).getSignal();return first!=null&&first==signal(level,B).getSignal()&&first==signal(level,C).getSignal()&&other!=null&&other!=first&&first.graph()==other.graph()&&first.owners(true).size()+first.owners(false).size()==3&&first.group(true)!=null&&first.group(false)!=null&&!first.group(true).equals(first.group(false));},350,"Actual native item placements failed to merge real owners or create genuine separated graph intervals");
        verifyGroupOverlay(context,world);
        context.getInput().pressKey(InputConstants.KEY_2);context.waitFor(client->client.player.getMainHandItem().isEmpty(),150);stand(context,world,A.south(2).below());use(context,A,Vec3.atCenterOf(A));context.waitFor(client->client.gui.screen() instanceof SignalScreen,200);clickMode(context);context.runOnClient(client->{if(((SignalScreen)client.gui.screen()).selectedMode()!=SignalBlock.SignalType.CROSS_SIGNAL)throw new AssertionError("Actual native signal mode button failed");});screenshot(context,"create-vulkan-signal-real-native-shared-boundary-mode-editor");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,200);
        waitServer(context,world,level->signal(level,A).getSignal().getTypeFor(signal(level,A).owner())==SignalBlock.SignalType.CROSS_SIGNAL&&LoadedLevelAccess.blockState(level,A).getValue(SignalBlock.TYPE)==SignalBlock.SignalType.CROSS_SIGNAL&&LoadedLevelAccess.blockState(level,B).getValue(SignalBlock.TYPE)==SignalBlock.SignalType.CROSS_SIGNAL&&LoadedLevelAccess.blockState(level,C).getValue(SignalBlock.TYPE)==SignalBlock.SignalType.ENTRY_SIGNAL,250,"Native confirmed mode failed to reach actual shared-side owners without rewriting opposite side");
        block(world,A.west(),"minecraft:redstone_block");waitServer(context,world,level->signal(level,A).getState()==SignalBlockEntity.SignalState.RED&&signal(level,B).getState()==SignalBlockEntity.SignalState.RED&&actualEmptyNativeTraffic(signal(level,C))&&actualEmptyNativeTraffic(signal(level,D))&&signal(level,C).getState()==SignalBlockEntity.SignalState.GREEN&&signal(level,D).getState()==SignalBlockEntity.SignalState.GREEN,250,"Real physical input failed to force only the shared side RED while the actual resolved empty native C/D groups are GREEN");
        awaitClient(context);verify(context);look(context,Vec3.atCenterOf(A).add(1,0,-2));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-signal-real-multi-owner-forced-red-and-dual-track-overlay");
        var model=context.computeOnClient(client->client.getModelManager().getModel(SignalModels.ON));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{if(client.getModelManager().getModel(SignalModels.ON)==model)throw new AssertionError("Actual signal partial retained stale native model after reload");});verify(context);
        var old=new AtomicReference<BlockEntity[]>();var firstId=new AtomicReference<UUID>();var farId=new AtomicReference<UUID>();var groups=new AtomicReference<List<UUID>>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();old.set(new BlockEntity[]{signal(level,A),signal(level,B),signal(level,C),signal(level,D)});var first=signal(level,A).getSignal();firstId.set(first.id);farId.set(signal(level,D).getSignal().id);groups.set(List.of(first.group(true),first.group(false)));});server.runCommand("save-all flush");server.runCommand("gamemode spectator @p");server.runCommand("tp @p 38400.5 20.0 38400.5");
        waitServer(context,world,level->{for(int x:new int[]{36992,37008,37024,37040})if(level.getChunkSource().getChunkNow(x>>4,0)!=null)return false;return true;},900,"All four native rail/owner fixture chunks failed to leave FULL availability");NativeFixtureChunkUnload.finish(context,world,old.get());server.runCommand("tp @p 37029.5 10.0 12.5");context.waitFor(client->Math.abs(client.player.getX()-37029.5)<.1&&Math.abs(client.player.getY()-10)<.1&&Math.abs(client.player.getZ()-12.5)<.1&&client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())<1,300);
        waitServer(context,world,level->{for(int i=0;i<4;i++){var pos=List.of(A,B,C,D).get(i);if(signal(level,pos)==null||signal(level,pos)==old.get()[i]||signal(level,pos).getSignal()==null)return false;}var first=signal(level,A).getSignal();return first.id.equals(firstId.get())&&signal(level,D).getSignal().id.equals(farId.get())&&first==signal(level,B).getSignal()&&first==signal(level,C).getSignal()&&first.owners(true).size()+first.owners(false).size()==3&&Objects.equals(first.group(true),groups.get().get(0))&&Objects.equals(first.group(false),groups.get().get(1))&&first.getTypeFor(signal(level,A).owner())==SignalBlock.SignalType.CROSS_SIGNAL&&signal(level,A).getState()==SignalBlockEntity.SignalState.RED;},700,"Actual native teardown/return lost point identity/shared owners/group IDs/mode/physical source input or duplicated points");awaitClient(context);verify(context);look(context,Vec3.atCenterOf(B));NativeScreenshotReadiness.await(context,world);screenshot(context,"create-vulkan-signal-real-owners-groups-and-mode-after-four-chunk-return");
        block(world,A.west(),"minecraft:air");waitServer(context,world,level->!LoadedLevelAccess.blockState(level,A).getValue(SignalBlock.POWERED)&&actualEmptyNativeTraffic(signal(level,A))&&actualEmptyNativeTraffic(signal(level,B))&&signal(level,A).getState()==SignalBlockEntity.SignalState.GREEN&&signal(level,B).getState()==SignalBlockEntity.SignalState.GREEN,200,"Actual delayed falling edge failed to restore the genuine resolved empty native shared side GREEN after reload");
        server.runOnServer(ignored->world.getConnection().getServerLevel().destroyBlock(A,false));waitServer(context,world,level->{var signal=signal(level,B);return signal!=null&&signal.getSignal()!=null&&signal.getSignal().id.equals(firstId.get())&&signal.getSignal().owners(true).size()+signal.getSignal().owners(false).size()==2;},200,"Removing one actual owner deleted the remaining shared boundary");context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,B) instanceof SignalBlockEntity signal&&signal.getSignal()!=null&&signal.getOverlay()==SignalBlockEntity.OverlayState.DUAL,300);look(context,Vec3.atCenterOf(B));screenshot(context,"create-vulkan-signal-actual-surviving-owner-takes-dual-overlay-after-removal");
    }
    /** The real server groups must reach the real client cache and native extracted geometry. */
    private static void verifyGroupOverlay(ClientGameTestContext context,TestSingleplayerContext world) {
        var expected=new AtomicReference<Map<UUID,EdgeGroupColor>>();
        var expectedGraphs=new AtomicReference<Set<UUID>>();
        world.getServer().runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();
            var traffic=com.simibubi.create.content.trains.TrainManager.get(level);
            var ids=new LinkedHashSet<UUID>();
            var graphIds=new LinkedHashSet<UUID>();
            for(var position:List.of(A,D)){var boundary=signal(level,position).getSignal();graphIds.add(boundary.graph().id);for(boolean side:new boolean[]{true,false})ids.add(boundary.group(side));}
            var colors=new LinkedHashMap<UUID,EdgeGroupColor>();
            for(var id:ids){var group=traffic.signalEdgeGroups().get(id);if(group==null||group.manager()!=traffic)throw new AssertionError("Actual fixture signal group owner is missing");colors.put(id,group.color);}
            expected.set(Map.copyOf(colors));
            expectedGraphs.set(Set.copyOf(graphIds));
        });
        boolean debug=context.computeOnClient(client->client.getDebugOverlay().showDebugScreen());
        if(debug){context.getInput().pressKey(InputConstants.KEY_F3);context.waitFor(client->!client.getDebugOverlay().showDebugScreen(),100);}
        try {
            slot(world,2,new ItemStack(AllBlocks.TRACK_SIGNAL));context.getInput().pressKey(InputConstants.KEY_3);
            try {
            boolean matched=false;
            for(int tick=0;tick<400&&!matched;tick++) {
                world.getServer().runOnServer(ignored->{
                    var level=world.getConnection().getServerLevel();var traffic=com.simibubi.create.content.trains.TrainManager.peekCurrent(level.getServer());var colors=new LinkedHashMap<UUID,EdgeGroupColor>();
                    for(var id:expected.get().keySet()){var group=traffic==null?null:traffic.signalEdgeGroups().get(id);if(group==null||group.manager()!=traffic)throw new AssertionError("Actual fixture signal group owner is missing");colors.put(id,group.color);}
                    expected.set(Map.copyOf(colors));
                });
                matched=context.computeOnClient(client->{
                if(!client.player.getMainHandItem().is(AllBlocks.TRACK_SIGNAL.asItem()))return false;
                var groups=SignalGroupClient.groups(client.level);
                for(var entry:expected.get().entrySet()){var group=groups.get(entry.getKey());if(group==null||group.color!=entry.getValue())return false;}
                var frame=com.simibubi.create.content.trains.graph.SignalGroupVisualizer.latest();
                for(var entry:expected.get().entrySet())if(frame.segments().stream().noneMatch(segment->segment.group().equals(entry.getKey())&&segment.color()==(0xFF000000|entry.getValue().rgb())&&segment.width()==1/8f))return false;
                return frame.graphs()>0;
                });
                if(!matched)context.waitTicks(1);
            }
            if(!matched)throw new AssertionError("Actual current server signal group colors did not reach the genuine native client overlay within 400 ticks");
            } catch(AssertionError failure) {
                try {world.getServer().runOnServer(ignored->Create.LOGGER.error("Native Signal group server: {}",signalGroupServerObservation(world.getConnection().getServerLevel(),expected.get(),expectedGraphs.get())));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
                try {context.runOnClient(client->Create.LOGGER.error("Native Signal group client: {}",signalGroupClientObservation(client,expected.get(),expectedGraphs.get())));}catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
                throw failure;
            }
            screenshot(context,"create-vulkan-signal-real-server-group-colors-and-native-held-signal-lines");
        } finally {
            context.getInput().pressKey(InputConstants.KEY_2);
            context.waitFor(client->client.player.getMainHandItem().isEmpty()&&com.simibubi.create.content.trains.graph.SignalGroupVisualizer.latest().segments().isEmpty(),150);
            if(debug){context.getInput().pressKey(InputConstants.KEY_F3);context.waitFor(client->client.getDebugOverlay().showDebugScreen(),100);}
        }
    }
    private static String signalGroupClientObservation(net.minecraft.client.Minecraft client,Map<UUID,EdgeGroupColor> expected,Set<UUID> expectedGraphs) {
        var groups=SignalGroupClient.groups(client.level);var frame=com.simibubi.create.content.trains.graph.SignalGroupVisualizer.latest();var view=com.simibubi.create.content.trains.graph.sync.RailwayGraphClient.view();
        boolean held=client.player.getMainHandItem().is(AllBlocks.TRACK_SIGNAL.asItem());
        boolean colors=expected.entrySet().stream().allMatch(entry->groups.get(entry.getKey())!=null&&groups.get(entry.getKey()).color==entry.getValue());
        boolean segments=expected.entrySet().stream().allMatch(entry->frame.segments().stream().anyMatch(segment->segment.group().equals(entry.getKey())&&segment.color()==(0xFF000000|entry.getValue().rgb())&&segment.width()==1/8f));
        var result=new StringBuilder("tick=").append(client.level.getGameTime()).append(" expectedGroups=").append(expected).append(" expectedGraphs=").append(expectedGraphs).append(" branches held=").append(held).append(" cacheColors=").append(colors).append(" nativeSegments=").append(segments).append(" graphsPositive=").append(frame.graphs()>0);
        result.append("; world=").append(client.level.dimension()).append("@").append(System.identityHashCode(client.level)).append(" player=").append(client.player.position()).append(" eye=").append(client.player.getEyePosition()).append(" camera=").append(client.gameRenderer.mainCamera().position()).append(" cameraEntity=").append(client.getCameraEntity()==null?null:client.getCameraEntity().getUUID()).append(" selected=").append(client.player.getInventory().getSelectedSlot()).append(" mainhand=").append(client.player.getMainHandItem()).append(" itemClass=").append(client.player.getMainHandItem().getItem().getClass().getName()).append(" offhand=").append(client.player.getOffhandItem()).append(" slot2=").append(client.player.getInventory().getItem(2));
        result.append("; debug=").append(client.getDebugOverlay().showDebugScreen()).append(" configuredGraphDebug=").append(com.simibubi.create.infrastructure.config.AllConfigs.client().showTrackGraphOnF3.get()).append(" signalTypeMatch=").append(client.player.getMainHandItem().getItem() instanceof TrackTargetingBlockItem targeting&&targeting.getType(client.player.getMainHandItem())==SignalEdgePoints.SIGNAL).append(" screen=").append(client.gui.screen()==null?null:client.gui.screen().getClass().getName()).append(" overlay=").append(client.gui.overlay()==null?null:client.gui.overlay().getClass().getName());
        result.append("; actualClientConnection=").append(System.identityHashCode(client.getConnection())).append(" groupConnectionCurrent=").append(signalObservationField(SignalGroupClient.class,"connection")==client.getConnection()).append(" graphConnectionCurrent=").append(signalObservationField(com.simibubi.create.content.trains.graph.sync.RailwayGraphClient.class,"connection")==client.getConnection());
        result.append("; cache ready=").append(view.ready()).append(" retryable=").append(view.retryable()).append(" problem=").append(view.problem()).append(" nonce=").append(view.nonce()).append(" epoch=").append(view.epoch()).append(" sequence=").append(view.sequence()).append(" graphIds=").append(view.graphs().keySet()).append(" completedSnapshots=").append(com.simibubi.create.content.trains.graph.sync.RailwayGraphClient.completedSnapshots());
        result.append("; actualGroupCount=").append(groups.size());for(var entry:expected.entrySet()){var actual=groups.get(entry.getKey());result.append("; group=").append(entry.getKey()).append(" expectedColor=").append(entry.getValue()).append(" actualColor=").append(actual==null?null:actual.color);}
        result.append("; latestNativeFrame=").append(System.identityHashCode(frame)).append(" graphs=").append(frame.graphs()).append(" segmentCount=").append(frame.segments().size()).append(" firstSegments=").append(frame.segments().stream().limit(12).toList());
        for(var graph:com.simibubi.create.content.trains.graph.sync.RailwayGraphClient.graphs(client.level))if(expectedGraphs.contains(graph.id)){
            result.append("; fixtureGraph=").append(graph.id).append(" revision=").append(graph.revision()).append(" nodes=").append(graph.getNodes().size());
            for(var location:graph.getNodes()){var node=graph.locateNode(location);result.append("; node=").append(location.getLocation()).append(" dimension=").append(location.dimension).append(" eyeDistance=").append(location.getLocation().distanceTo(client.player.getEyePosition())).append(" edges=").append(graph.getConnectionsFrom(node).size());}
            for(var boundary:graph.getPoints(SignalEdgePoints.SIGNAL))result.append("; clientBoundary=").append(boundary.id).append(" groupTrue=").append(boundary.group(true)).append(" groupFalse=").append(boundary.group(false));
        }
        return result.toString();
    }
    private static String signalGroupServerObservation(ServerLevel level,Map<UUID,EdgeGroupColor> expected,Set<UUID> expectedGraphs) {
        var result=new StringBuilder("tick=").append(level.getGameTime()).append(" expectedGroups=").append(expected).append(" expectedGraphs=").append(expectedGraphs);var player=level.getServer().getPlayerList().getPlayers().getFirst();
        result.append(" player=").append(player.position()).append(" eye=").append(player.getEyePosition()).append(" mainhand=").append(player.getMainHandItem()).append(" selected=").append(player.getInventory().getSelectedSlot()).append(" slot2=").append(player.getInventory().getItem(2));
        var manager=com.simibubi.create.content.trains.TrainManager.peekCurrent(level.getServer());result.append("; currentTrafficManager=").append(manager==null?null:System.identityHashCode(manager));
        for(var entry:expected.entrySet()){var actual=manager==null?null:manager.signalEdgeGroups().get(entry.getKey());result.append("; group=").append(entry.getKey()).append(" expectedColor=").append(entry.getValue()).append(" actualColor=").append(actual==null?null:actual.color).append(" canonicalManager=").append(actual!=null&&actual.manager()==manager);}
        for(var pos:List.of(A,B,C,D)){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var actual=chunk==null?null:chunk.getBlockEntities().get(pos);result.append("; pos=").append(pos).append(" FULL=").append(chunk!=null).append(" state=").append(chunk==null?null:chunk.getBlockState(pos)).append(" owner=").append(actual==null?null:actual.getClass().getName()+"@"+System.identityHashCode(actual));if(actual instanceof SignalBlockEntity signal){var boundary=signal.getSignal();result.append(" removed=").append(signal.isRemoved()).append(" levelCurrent=").append(signal.getLevel()==level).append(" signalState=").append(signal.getState()).append(" overlay=").append(signal.getOverlay()).append(" boundary=").append(boundary==null?null:boundary.id).append(" graph=").append(boundary==null||boundary.graph()==null?null:boundary.graph().id).append(" groups=").append(boundary==null?null:Arrays.asList(boundary.group(true),boundary.group(false)));}}
        return result.toString();
    }
    private static Object signalObservationField(Class<?> declaring,String name){try{var field=declaring.getDeclaredField(name);field.setAccessible(true);return field.get(null);}catch(ReflectiveOperationException|RuntimeException failure){return "unavailable:"+name+":"+failure.getClass().getSimpleName();}}
    /** Read actual resolved native traffic; no synthetic Train/group/state/readiness is installed. */
    private static boolean actualEmptyNativeTraffic(SignalBlockEntity owner){
        if(owner==null||!(owner.getLevel() instanceof ServerLevel level))return false;
        var boundary=owner.getSignal();if(boundary==null||boundary.graph()==null)return false;
        var traffic=com.simibubi.create.content.trains.TrainManager.get(level);
        if(!traffic.isReady()||boundary.graph().manager()!=com.simibubi.create.content.trains.RailwayGraphManager.get(level))return false;
        Boolean primary=null;
        if(boundary.owners(true).containsKey(owner.owner()))primary=true;
        else if(boundary.owners(false).containsKey(owner.owner()))primary=false;
        if(primary==null)return false;
        var group=traffic.signalEdgeGroups().get(boundary.group(primary));
        return group!=null&&group.manager()==traffic&&group.hasResolvedTopology()&&group.trains.isEmpty()&&group.reserved==null;
    }

    private static void place(ClientGameTestContext context,TestSingleplayerContext world,BlockPos target,BlockPos owner,boolean front,int remaining) {
        // Source axis selection is positive when the actual look vector opposes
        // the +X track axis, so a front-facing selection is made from the east.
        stand(context,world,(front?target.east(2):target.west(2)).south().below());context.getInput().pressKey(InputConstants.KEY_1);context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.TRACK_SIGNAL.asItem()),150);use(context,target,Vec3.atBottomCenterOf(target).add(0,.1,0));
        context.waitFor(client->{var data=client.player.getMainHandItem().get(AllDataComponents.TRACK_TARGETING_SELECTION);return data!=null&&data.target().pos().equals(target)&&data.front()==front;},200);stand(context,world,owner.south(2).below());use(context,owner.below(),Vec3.atLowerCornerOf(owner).add(.5,0,.5));
        waitServer(context,world,level->signal(level,owner)!=null&&signal(level,owner).getSignal()!=null&&signal(level,owner).targeting().selection().target().pos().equals(target),250,"Actual selected native signal item failed to place/bind source owner");context.waitFor(client->remaining==0?client.player.getMainHandItem().isEmpty():client.player.getMainHandItem().getCount()==remaining&&!client.player.getMainHandItem().has(AllDataComponents.TRACK_TARGETING_SELECTION),150);
    }
    private static void verify(ClientGameTestContext context) { context.waitFor(client->{if(client.level==null||client.player==null||client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())>=1)return false;for(var pos:List.of(A,B,C,D)){var entity=LoadedLevelAccess.blockEntity(client.level,pos);if(entity!=null&&client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,true)==null)return false;}return true;},300);context.runOnClient(client->{
        for(var block:AllBlocks.TRACK_SIGNAL.getStateDefinition().getPossibleStates())if(client.getModelManager().getBlockStateModelSet().get(block)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing actual native signal state model "+block);
        for(var pos:List.of(A,B,C,D)){var signal=(SignalBlockEntity)LoadedLevelAccess.blockEntity(client.level,pos);if(signal==null)continue;var state=(SignalRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(signal,.5f,null,true);if(state==null||state.indicator.isEmpty()||state.indicator.mesh.size()!=12)throw new AssertionError("Actual native signal source indicator geometry missing");if((pos.equals(A)||pos.equals(B))&&signal.getState()==SignalBlockEntity.SignalState.RED&&(!state.red||state.indicatorLight!=net.minecraft.util.LightCoordsUtil.FULL_BRIGHT))throw new AssertionError("Actual physically forced-red native indicator is not full bright");if(signal.getOverlay()!=SignalBlockEntity.OverlayState.SKIP){if(!state.hasOverlay||state.trackOverlay.isEmpty()||!state.transform.isFinite())throw new AssertionError("Actual native source track marker/frame missing");state.trackOverlay.mesh.forEach(quad->{if(quad.chunkLayer()!=net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT)throw new AssertionError("Actual source signal marker must use native CUTOUT");});}}
    }); }
    private static void awaitClient(ClientGameTestContext context) { context.waitFor(client->{for(var pos:List.of(A,B,C,D))if(!(LoadedLevelAccess.blockEntity(client.level,pos) instanceof SignalBlockEntity signal)||signal.getSignal()==null)return false;return true;},500); }
    private static void clickMode(ClientGameTestContext context) { var point=context.computeOnClient(client->{var button=((SignalScreen)client.gui.screen()).children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(value->value.getWidth()==204).findFirst().orElseThrow();float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2((button.getX()+button.getWidth()/2f)*scaleX,(button.getY()+10)*scaleY);});context.getInput().setCursorPos(point.x,point.y);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); }
    private static void stand(ClientGameTestContext context,TestSingleplayerContext world,BlockPos support) { block(world,support.above(),"minecraft:air");block(world,support.above(2),"minecraft:air");block(world,support,"minecraft:smooth_quartz");world.getServer().runCommand("gamemode survival @p");world.getServer().runCommand("tp @p "+(support.getX()+.5)+" "+(support.getY()+1d)+" "+(support.getZ()+.5));context.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&Math.abs(client.player.getX()-(support.getX()+.5))<.1&&Math.abs(client.player.getY()-(support.getY()+1))<.1&&Math.abs(client.player.getZ()-(support.getZ()+.5))<.1&&client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())<1,250); }
    private static void use(ClientGameTestContext context,BlockPos pos,Vec3 point) { look(context,point);context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),200);context.waitTicks(6);context.getInput().pressKey(options->options.keyUse); }
    private static void look(ClientGameTestContext context,Vec3 point) { var angles=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angles.x,angles.y); }
    private static void slot(TestSingleplayerContext world,int slot,ItemStack stack) { world.getServer().runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.getInventory().setItem(slot,stack.copy());player.connection.send(player.getInventory().createInventoryUpdatePacket(slot));}); }
    private static SignalBlockEntity signal(ServerLevel level,BlockPos pos) { return LoadedLevelAccess.blockEntity(level,pos)instanceof SignalBlockEntity signal?signal:null; }
    private static void block(TestSingleplayerContext world,BlockPos pos,String state) { world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state); }
    private static void screenshot(ClientGameTestContext context,String name) { context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name); }
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int ticks,String message) { var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message); }
}
