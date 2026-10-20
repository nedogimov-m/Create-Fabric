package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Proposed actual native item clicks -> real reciprocal turn -> source meshes -> reload -> native unload/return. */
final class TrackRenderingTest {
    private static final BlockPos P=new BlockPos(10308,5,4),Q=P.offset(8,0,8);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 10308.5 7 7.5");
        context.waitFor(client->client.level.isLoaded(Q)&&Math.abs(client.player.getX()-10308.5)<.1,400);
        context.waitFor(client->NativeScreenshotReadiness.skyReady(client,new BlockPos(10303,5,8),new BlockPos(10308,5,8),new BlockPos(10321,5,8)),300);
        server.runCommand("fill 10303 4 -1 10321 4 17 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(P,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO));level.setBlockAndUpdate(Q,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.ZO));});
        server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:track 64");server.runCommand("tp @p 10308.5 5 7.5");
        context.waitFor(client->client.player.onGround()&&client.player.getMainHandItem().is(AllBlocks.TRACK.asItem())&&client.level.getBlockState(P).is(AllBlocks.TRACK),200);
        look(context,Vec3.atBottomCenterOf(P).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),200);
        server.runCommand("tp @p 10316.5 5 15.5");context.waitFor(client->client.player.onGround()&&Math.abs(client.player.getX()-10316.5)<.1,200);
        look(context,Vec3.atBottomCenterOf(Q).add(0,.1,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(Q),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockEntity(P) instanceof TrackBlockEntity a&&a.getConnections().containsKey(Q)&&client.level.getBlockEntity(Q) instanceof TrackBlockEntity b&&b.getConnections().containsKey(P),300);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var a=(TrackBlockEntity)level.getBlockEntity(P);var b=(TrackBlockEntity)level.getBlockEntity(Q);var curve=a.getConnections().get(Q);var player=level.getServer().getPlayerList().getPlayers().getFirst();if(curve==null||!curve.primary||b.getConnections().get(P).primary||!curve.equalsSansMaterial(b.getConnections().get(P))||player.getMainHandItem().getCount()!=64-curve.getTrackItemCost()||player.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM))throw new AssertionError("Actual server item placement did not conserve source curve/world/inventory");});
        verify(context);look(context,Vec3.atBottomCenterOf(P.offset(4,0,4)));screenshot(context,world,"create-vulkan-track-real-item-turn-source-ties-rails-and-light");
        var old=context.computeOnClient(client->((TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true)).pieces.getFirst().model().mesh);
        var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();verify(context);
        context.runOnClient(client->{var now=((TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true)).pieces.getFirst().model().mesh;if(now==old)throw new AssertionError("Track curve retained stale native partial mesh after reload");});screenshot(context,world,"create-vulkan-track-native-resource-reload-rebakes-original-geometry");
        var originalTracks=server.computeOnServer(ignored->{var level=world.getConnection().getServerLevel();return new BlockEntity[]{LoadedLevelAccess.blockEntity(level,P),LoadedLevelAccess.blockEntity(level,Q)};});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 11100 16 11100");
        for(int i=0;i<800;i++){var absent=new java.util.concurrent.atomic.AtomicBoolean();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();absent.set(level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null&&level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)==null);});if(absent.get())break;if(i==799)throw new AssertionError("Actual source/target terrain did not unload");context.waitTicks(1);}
        NativeFixtureChunkUnload.finish(context,world,originalTracks);
        server.runCommand("tp @p 10316.5 8 15.5");context.waitFor(client->Math.abs(client.player.getX()-10316.5)<.1&&Math.abs(client.player.getZ()-15.5)<.1&&client.level.getBlockEntity(P) instanceof TrackBlockEntity a&&a.getConnections().containsKey(Q)&&client.level.getBlockEntity(Q) instanceof TrackBlockEntity b&&b.getConnections().containsKey(P),500);verify(context);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var a=(TrackBlockEntity)level.getBlockEntity(P);var b=(TrackBlockEntity)level.getBlockEntity(Q);if(a==originalTracks[0]||b==originalTracks[1]||!a.getConnections().get(Q).equalsSansMaterial(b.getConnections().get(P)))throw new AssertionError("Native save/unload/return changed reciprocal source curve");});
        look(context,Vec3.atBottomCenterOf(P.offset(4,0,4)));screenshot(context,world,"create-vulkan-track-native-save-and-real-two-endpoint-unload-return");
        // A native server block destruction must retire the reciprocal renderer without a ghost client BE.
        server.runOnServer(ignored->world.getConnection().getServerLevel().destroyBlock(P,false));
        context.waitFor(client->client.level.getBlockState(P).isAir()&&!client.level.getBlockState(Q).getValue(TrackBlock.HAS_BE)&&client.level.getBlockEntity(Q)==null,300);
    }
    private static void verify(ClientGameTestContext context){
        // Native distance admission follows the renderer camera, which can lag a teleport packet.
        try {
            context.waitFor(client->client.level.getBlockEntity(P) instanceof TrackBlockEntity first&&client.level.getBlockEntity(Q) instanceof TrackBlockEntity second&&trackNativeLightReady(client,first,null)&&client.getBlockEntityRenderDispatcher().tryExtractRenderState(first,.5f,null,true)!=null&&client.getBlockEntityRenderDispatcher().tryExtractRenderState(second,.5f,null,true)!=null,300);
        } catch(RuntimeException|Error failure) {
            try {context.runOnClient(client->{
                var level=client.level;var chunk=level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4);
                var entity=chunk==null?null:chunk.getBlockEntities().get(P);
                var observation=new StringBuilder("tick=").append(level.getGameTime()).append(" hasLightWork=").append(level.getLightEngine().hasLightWork()).append(" owner=").append(entity==null?"absent":entity.getClass().getName());
                if(entity instanceof TrackBlockEntity track)trackNativeLightReady(client,track,observation);
                System.err.println("CREATE_TRACK_NATIVE_LIGHT_WAIT "+observation);
            });} catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }
        context.runOnClient(client->{
        var a=(TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),.5f,null,true);var b=(TrackRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(Q),.5f,null,true);
        if(a==null||a.primaryCurves!=1||a.segments<1||a.pieces.size()!=a.segments*3||b==null||!b.pieces.isEmpty())throw new AssertionError("Native curve must render source ties/two rails only on its primary endpoint: first="+(a==null?"null":a.primaryCurves+"/"+a.segments+"/"+a.pieces.size())+", second="+(b==null?"null":b.primaryCurves+"/"+b.segments+"/"+b.pieces.size())+", player="+client.player.position());
        try {
            for(var piece:a.pieces){if(piece.model().mesh==null||piece.model().mesh.size()==0||piece.model().isEmpty()||!piece.transform().isFinite()||piece.light()==0)throw new AssertionError("Missing native track mesh/frame/world-light snapshot");}
        } catch(AssertionError failure) {
            try {System.err.println("CREATE_TRACK_NATIVE_SNAPSHOT "+trackObservation(client,a,b));}
            catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }
        var models=client.getModelManager().getBlockStateModelSet();for(var state:AllBlocks.TRACK.getStateDefinition().getPossibleStates())if(state.getValue(TrackBlock.SHAPE)!=TrackShape.NONE){var model=models.get(state);if(model==models.missingModel())throw new AssertionError("Missing track source variant "+state);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,P,state,net.minecraft.util.RandomSource.create(31),side->false);if(mesh.immutableCopy().size()==0)throw new AssertionError("Empty source track variant "+state);}
    });}
    /** Wait for this open-sky fixture's native light input, independently of renderer output. */
    private static boolean trackNativeLightReady(Minecraft client,TrackBlockEntity track,StringBuilder observation){
        var level=track.getLevel();
        if(observation!=null)observation.append(" ownerLevelIdentity=").append(level==client.level).append(" ownerRemoved=").append(track.isRemoved());
        if(level!=client.level||track.isRemoved())return false;
        boolean ready=true;
        for(var curve:track.getConnections().values()){
            if(!curve.isPrimary())continue;Vec3 previousLeft=null;
            for(var segment:curve){
                var left=segment.position.add(segment.normal.scale(.965f));var right=segment.position.subtract(segment.normal.scale(.965f));
                if(previousLeft==null){previousLeft=left;continue;}
                var middle=left.add(right).scale(.5);var sample=BlockPos.containing(middle).offset(track.getBlockPos());
                var chunk=client.level.getChunkSource().getChunkNow(sample.getX()>>4,sample.getZ()>>4);
                boolean column=level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(sample.getX()>>4,sample.getZ()>>4));
                int sky=level.getBrightness(net.minecraft.world.level.LightLayer.SKY,sample);
                boolean published=chunk!=null&&column&&sky==15;
                if(observation!=null)observation.append("; nativeSample segment=").append(segment.index).append(" position=").append(sample).append(" FULLnow=").append(chunk!=null).append(" lightOnInColumn=").append(column).append(" sky=").append(sky).append(" block=").append(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,sample)).append(" nativePacked=").append(net.minecraft.util.LightCoordsUtil.getLightCoords(level,sample)).append(" sampleState=").append(chunk==null?"absent":chunk.getBlockState(sample));
                if(!published){ready=false;if(observation==null)return false;}
                previousLeft=left;
            }
        }
        return ready;
    }
    private static String trackObservation(Minecraft client,TrackRenderer.State first,TrackRenderer.State second){
        var level=client.level;var chunk=level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4);
        var entity=chunk==null?null:chunk.getBlockEntities().get(P);
        var renderer=entity==null?null:client.getBlockEntityRenderDispatcher().getRenderer(entity);
        var result=new StringBuilder("tick=").append(level.getGameTime()).append(" player=").append(client.player.position()).append(" camera=").append(client.gameRenderer.mainCamera().position()).append(" hasLightWork=").append(level.getLightEngine().hasLightWork()).append(" renderer=").append(renderer==null?"absent":renderer.getClass().getName()).append(" state=").append(first.getClass().getName()).append(" stateOwnerLight=").append(first.lightCoords).append(" curves=").append(first.primaryCurves).append(" segments=").append(first.segments).append(" pieces=").append(first.pieces.size()).append(" secondPieces=").append(second.pieces.size());
        for(int index=0;index<first.pieces.size();index++){
            var piece=first.pieces.get(index);var model=piece.model();
            result.append("; piece[").append(index).append("] meshPresent=").append(model.mesh!=null).append(" meshSize=").append(model.mesh==null?-1:model.mesh.size()).append(" modelEmpty=").append(model.isEmpty()).append(" finite=").append(piece.transform().isFinite()).append(" packedLight=").append(piece.light()).append(" block=").append(net.minecraft.util.LightCoordsUtil.block(piece.light())).append(" sky=").append(net.minecraft.util.LightCoordsUtil.sky(piece.light()));
            if(!piece.transform().isFinite())result.append(" transform=").append(piece.transform());
        }
        if(entity instanceof TrackBlockEntity track){
            result.append("; owner=").append(track.getBlockPos()).append(" ownerState=").append(track.getBlockState()).append(" ownerLevelIdentity=").append(track.getLevel()==level).append(" ownerRemoved=").append(track.isRemoved()).append(" currentOwnerPackedLight=").append(net.minecraft.util.LightCoordsUtil.getLightCoords(level,track.getBlockPos()));
            int pieceIndex=0;
            for(var curve:track.getConnections().values()){
                if(!curve.isPrimary())continue;Vec3 previousLeft=null;
                for(var segment:curve){
                    // Exact TrackRenderer tie/rail world-light position; the first segment emits no pieces.
                    var left=segment.position.add(segment.normal.scale(.965f));var right=segment.position.subtract(segment.normal.scale(.965f));
                    if(previousLeft==null){previousLeft=left;continue;}
                    var middle=left.add(right).scale(.5);var sample=BlockPos.containing(middle).offset(track.getBlockPos());
                    var sampleChunk=level.getChunkSource().getChunkNow(sample.getX()>>4,sample.getZ()>>4);
                    result.append("; nativeSample segment=").append(segment.index).append(" pieceIndices=").append(pieceIndex).append(",").append(pieceIndex+1).append(",").append(pieceIndex+2).append(" position=").append(sample).append(" localMiddle=").append(middle).append(" FULLnow=").append(sampleChunk!=null).append(" lightOnInColumn=").append(level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(sample.getX()>>4,sample.getZ()>>4))).append(" sky=").append(level.getBrightness(net.minecraft.world.level.LightLayer.SKY,sample)).append(" block=").append(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,sample)).append(" nativePacked=").append(net.minecraft.util.LightCoordsUtil.getLightCoords(level,sample)).append(" sampleState=").append(sampleChunk==null?"absent":sampleChunk.getBlockState(sample));
                    pieceIndex+=3;previousLeft=left;
                }
            }
        }
        return result.toString();
    }
    private static void look(ClientGameTestContext context,Vec3 point){var angles=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angles.x,angles.y);}
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){NativeScreenshotReadiness.await(context,world);context.waitFor(client->client.gui.overlay()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
}
