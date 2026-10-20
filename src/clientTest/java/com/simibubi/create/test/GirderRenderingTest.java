package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.girder.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.*;

/** Actual survival shaft operations/RPM, native connected UV/brackets/curve parts, reload and two-chunk return.
 * Fixtures16998..17024,z0..16; unload18000. STAGED, UNRUN. No train/curve construction claim.
 */
final class GirderRenderingTest {
    private static final BlockPos P=new BlockPos(17005,6,5),B=P.east(4),K=P.east(8);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 17010.0 10.0 10.0");context.waitFor(client->Math.abs(client.player.getX()-17010.0)<.1&&Math.abs(client.player.getZ()-10.0)<.1,300);context.waitFor(client->client.level.isLoaded(K)&&Math.abs(client.player.getX()-17010)<.1,500);server.runCommand("fill 16998 4 0 17024 4 16 minecraft:smooth_quartz");
        for(int y=-1;y<=1;y++)block(world,P.above(y),"create:metal_girder[axis=y,x=false,z=false]");block(world,B,"create:metal_girder[axis=x,x=true,z=false]");block(world,B.east(),"minecraft:stone");block(world,K,"create:metal_girder[axis=z,x=false,z=true]");
        block(world,K.west(),"create:creative_motor[facing=east]");block(world,K.east(),"create:shaft[axis=x]");block(world,K.south(2),"minecraft:smooth_quartz");block(world,B.south(2),"minecraft:smooth_quartz");
        server.runOnServer(ignored->((KineticBlockEntity)LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),K.west())).setGeneratedSpeed(64));
        server.runCommand("item replace entity @p hotbar.0 with create:shaft 2");server.runCommand("item replace entity @p hotbar.1 with create:wrench");server.runCommand("gamemode survival @p");server.runCommand("tp @p 17013.5 7 7.5");context.waitFor(client->Math.abs(client.player.getX()-17013.5)<.1&&Math.abs(client.player.getZ()-7.5)<.1,300);context.getInput().pressKey(InputConstants.KEY_1);context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.SHAFT.asItem()),200);
        int originalShafts=context.computeOnClient(client->inventory(client.player.getInventory(),AllBlocks.SHAFT.asItem()));use(context,K);
        waitServer(context,world,level->LoadedLevelAccess.blockState(level,K).is(AllBlocks.METAL_GIRDER_ENCASED_SHAFT)&&Math.abs(entity(level,K).getSpeed())==64&&Math.abs(entity(level,K.east()).getSpeed())==64,250,"Real survival insertion did not create source64RPM path");
        context.waitFor(client->inventory(client.player.getInventory(),AllBlocks.SHAFT.asItem())==originalShafts-1,200);context.waitFor(client->LoadedLevelAccess.blockState(client.level,K).is(AllBlocks.METAL_GIRDER_ENCASED_SHAFT)&&LoadedLevelAccess.blockEntity(client.level,K) instanceof KineticBlockEntity current&&current.getLevel()==client.level&&!current.isRemoved()&&Math.abs(current.getSpeed())==64&&LoadedLevelAccess.blockEntity(client.level,K.east()) instanceof KineticBlockEntity output&&output.getLevel()==client.level&&!output.isRemoved()&&Math.abs(output.getSpeed())==64,250);verifyKinetic(context);verifyGeometry(context);world.getConnection().waitForChunksRender();look(context,Vec3.atCenterOf(K).add(0,-.2,0));screenshot(context,"create-vulkan-native-girder-actual-shaft64rpm-and-source-connections");
        context.getInput().pressKey(InputConstants.KEY_2);context.waitFor(client->client.player.getMainHandItem().is(AllItems.WRENCH),150);use(context,K);waitServer(context,world,level->LoadedLevelAccess.blockState(level,K).is(AllBlocks.METAL_GIRDER)&&entity(level,K.east()).getSpeed()==0,250,"Native wrench removal did not disconnect actual graph");context.waitFor(client->inventory(client.player.getInventory(),AllBlocks.SHAFT.asItem())==originalShafts,200);
        var oldModel=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.METAL_GIRDER.defaultBlockState()));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(AllBlocks.METAL_GIRDER.defaultBlockState())==oldModel)throw new AssertionError("Native connected girder model retained stale reload identity");});verifyGeometry(context);
        var originalShaft=server.computeOnServer(ignored->entity(world.getConnection().getServerLevel(),K.east()));
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 18000 20 18000");waitServer(context,world,level->level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null&&level.getChunkSource().getChunkNow(K.getX()>>4,K.getZ()>>4)==null,800,"Both actual girder fixture chunks failed to unload");
        NativeFixtureChunkUnload.finish(context,world,originalShaft);
        server.runCommand("tp @p 17010.0 10.0 10.0");context.waitFor(client->Math.abs(client.player.getX()-17010.0)<.1&&Math.abs(client.player.getZ()-10.0)<.1,300);waitServer(context,world,level->LoadedLevelAccess.blockState(level,P).is(AllBlocks.METAL_GIRDER)&&LoadedLevelAccess.blockState(level,K).is(AllBlocks.METAL_GIRDER)&&entity(level,K.east())!=null&&entity(level,K.east())!=originalShaft&&entity(level,K.east()).getSpeed()==0,600,"Native chunk return lost source beam state or replayed removed shaft");context.waitFor(client->client.level.isLoaded(P)&&client.level.isLoaded(K),300);verifyGeometry(context);world.getConnection().waitForChunksRender();look(context,Vec3.atCenterOf(B));screenshot(context,"create-vulkan-native-girder-bracket-pole-sheet-after-true-chunk-return");
        server.runCommand("gamemode survival @p");server.runCommand("tp @p 17009.5 7 7.5");context.waitFor(client->Math.abs(client.player.getX()-17009.5)<.1&&Math.abs(client.player.getZ()-7.5)<.1,300);context.waitFor(client->!client.player.isSpectator(),200);int girders=context.computeOnClient(client->inventory(client.player.getInventory(),AllBlocks.METAL_GIRDER.asItem()));context.getInput().holdKey(options->options.keyShift);use(context,B);context.getInput().releaseKey(options->options.keyShift);waitServer(context,world,level->LoadedLevelAccess.blockState(level,B).isAir(),200,"Real sneak wrench did not retrieve source girder");context.waitFor(client->inventory(client.player.getInventory(),AllBlocks.METAL_GIRDER.asItem())==girders+1,200);
    }
    private static void verifyKinetic(ClientGameTestContext context){context.runOnClient(client->{
        try{
        var be=(KineticBlockEntity)LoadedLevelAccess.blockEntity(client.level,K);
        var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(be,.5f,null,true);
        KineticRenderState state=null;
        if(extracted instanceof com.simibubi.create.content.kinetics.NativeKineticsRenderer.State nativeState&&nativeState.usesLegacyRenderer()&&nativeState.getParts().isEmpty()){
            try{var field=com.simibubi.create.content.kinetics.NativeKineticsRenderer.State.class.getDeclaredField("legacy");field.setAccessible(true);state=(KineticRenderState)field.get(nativeState);}
            catch(ReflectiveOperationException observationFailure){throw new AssertionError("Cannot inspect actual native girder legacy kinetic state",observationFailure);}
        }
        if(state==null||state.axis!=Direction.Axis.X||!(state.model instanceof PartialModelRenderState model)||model.mesh==null||model.mesh.size()!=6)throw new AssertionError("Actual native kinetic extraction must contain only the rotating six-face shaft, not the girder body");
        }catch(RuntimeException|Error failure){
            try{System.err.println("CREATE_GIRDER_NATIVE_KINETIC_INPUT "+kineticObservation(client));}
            catch(RuntimeException|Error observationFailure){failure.addSuppressed(observationFailure);}
            throw failure;
        }
    });}
    private static String kineticObservation(net.minecraft.client.Minecraft client){
        var level=client.level;if(level==null)return "level=absent";
        var chunk=level.getChunkSource().getChunkNow(K.getX()>>4,K.getZ()>>4);var owner=chunk==null?null:chunk.getBlockEntities().get(K);
        var out=new StringBuilder("tick=").append(level.getGameTime()).append(" pos=").append(K).append(" player=").append(client.player==null?null:client.player.position()).append(" mainCamera=").append(client.gameRenderer.mainCamera().position()).append(" FULLnow=").append(chunk!=null).append(" worldState=").append(chunk==null?null:chunk.getBlockState(K)).append(" ownerClass=").append(owner==null?null:owner.getClass().getName());
        if(!(owner instanceof KineticBlockEntity be))return out.toString();
        var dispatcher=client.getBlockEntityRenderDispatcher();var renderer=dispatcher.getRenderer(be);
        out.append(" sameCanonicalLookup=").append(LoadedLevelAccess.blockEntity(level,K)==be).append(" removed=").append(be.isRemoved()).append(" hasLevel=").append(be.hasLevel()).append(" ownerLevelSame=").append(be.getLevel()==level).append(" cachedState=").append(be.getBlockState()).append(" type=").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())).append(" nativeTypeValid=").append(be.getType().isValid(be.getBlockState())).append(" rpm=").append(be.getSpeed()).append(" actualRotationAxis=").append(be.getBlockState().getBlock() instanceof IRotate rotate?rotate.getRotationAxis(be.getBlockState()):null).append(" rendererClass=").append(renderer==null?null:renderer.getClass().getName());
        if(renderer!=null){
            try{var field=net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class.getDeclaredField("cameraPos");field.setAccessible(true);var camera=(Vec3)field.get(dispatcher);out.append(" dispatcherCamera=").append(camera).append(" offscreenFlag=").append(renderer.shouldRenderOffScreen()).append(" viewDistance=").append(renderer.getViewDistance()).append(" nativeShouldRender=").append(renderer.shouldRender(be,camera));}
            catch(ReflectiveOperationException observationFailure){out.append(" dispatcherCameraObservationFailed=").append(observationFailure);}
            var ordinary=dispatcher.tryExtractRenderState(be,.5f,null,false);var offscreen=dispatcher.tryExtractRenderState(be,.5f,null,true);
            out.append(" nativePassFalse={").append(extractedObservation(ordinary)).append("} nativePassTrue={").append(extractedObservation(offscreen)).append('}');
        }
        return out.toString();
    }
    private static String extractedObservation(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state){
        if(state==null)return "null";
        var out=new StringBuilder("class=").append(state.getClass().getName()).append(" light=").append(state.lightCoords);
        if(state instanceof KineticRenderState kinetic)out.append(" kinetic={").append(kineticStateObservation(kinetic)).append('}');
        if(state instanceof com.simibubi.create.content.kinetics.NativeKineticsRenderer.State nativeState){
            out.append(" usesLegacyRenderer=").append(nativeState.usesLegacyRenderer()).append(" parts=").append(nativeState.getParts().size());
            for(var part:nativeState.getParts())out.append(" {axis=").append(part.axis()).append(" angle=").append(part.angle()).append(" facing=").append(part.modelFacing()).append(" vertical=").append(part.vertical()).append(" geometry=").append(partialObservation(part.model())).append('}');
            try{var field=com.simibubi.create.content.kinetics.NativeKineticsRenderer.State.class.getDeclaredField("legacy");field.setAccessible(true);out.append(" actualLegacy={").append(kineticStateObservation((KineticRenderState)field.get(nativeState))).append('}');}
            catch(ReflectiveOperationException observationFailure){out.append(" legacyObservationFailed=").append(observationFailure);}
        }
        return out.toString();
    }
    private static String kineticStateObservation(KineticRenderState state){return "axis="+state.axis+", angle="+state.angle+", modelClass="+state.model.getClass().getName()+", modelEmpty="+state.model.isEmpty()+", ports="+state.ports.size()+", partial="+(state.model instanceof PartialModelRenderState partial?partialObservation(partial):"absent");}
    private static String partialObservation(PartialModelRenderState state){
        if(state.mesh==null)return "mesh=null, empty="+state.isEmpty();
        var faces=new TreeMap<String,Integer>();var layers=new TreeMap<String,Integer>();float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};boolean[] finite={true};
        state.mesh.forEach(quad->{faces.merge(String.valueOf(quad.lightFace()),1,Integer::sum);layers.merge(String.valueOf(quad.chunkLayer()),1,Integer::sum);for(int vertex=0;vertex<4;vertex++){float x=quad.x(vertex),y=quad.y(vertex),z=quad.z(vertex);finite[0]&=Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z);bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.min(bounds[2],z);bounds[3]=Math.max(bounds[3],x);bounds[4]=Math.max(bounds[4],y);bounds[5]=Math.max(bounds[5],z);}});
        return "meshSize="+state.mesh.size()+", empty="+state.isEmpty()+", translucent="+state.translucent+", finite="+finite[0]+", bounds="+Arrays.toString(bounds)+", faces="+faces+", layers="+layers;
    }
    private static Mesh mesh(net.minecraft.client.Minecraft client,BlockPos pos){var result=Renderer.get().mutableMesh();var state=LoadedLevelAccess.blockState(client.level,pos);client.getModelManager().getBlockStateModelSet().get(state).emitQuads(result.emitter(),client.level,pos,state,RandomSource.create(42),side->false);return result.immutableCopy();}
    private static void verifyGeometry(ClientGameTestContext context){context.runOnClient(client->{
        var state=LoadedLevelAccess.blockState(client.level,P);if(ConnectedGirderModel.poleTile(client.level,P,state)!=3)throw new AssertionError("Actual vertical pole context did not connect both source neighbors");
        var pole=mesh(client,P);if(pole.size()==0)throw new AssertionError("Native pole geometry empty");var atlas=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);var sheet=atlas.getSprite(Create.asResource("block/girder_pole_side_connected"));var seen=new AtomicBoolean();
        pole.forEach(quad->{if(quad.lightFace().getAxis()==Direction.Axis.Y)return;boolean inside=true;for(int i=0;i<4;i++)inside&=quad.u(i)>=sheet.getU(.5f)-.00001&&quad.u(i)<=sheet.getU(1)+.00001&&quad.v(i)>=sheet.getV(.5f)-.00001&&quad.v(i)<=sheet.getV(1)+.00001;if(inside)seen.set(true);});if(!seen.get())throw new AssertionError("Actual native pole faces did not shift into source2x2 tile3");
        var beam=LoadedLevelAccess.blockState(client.level,B);if(!GirderBlock.isConnected(client.level,B,beam,Direction.EAST))throw new AssertionError("Actual solid endpoint lacks source bracket connection");var withBracket=mesh(client,B);var model=client.getModelManager().getModel(GirderModels.BRACKETS.get(Direction.EAST));var bracket=Renderer.get().mutableMesh();model.emitQuads(bracket.emitter(),client.level,B,beam,RandomSource.create(42),side->false);if(bracket.size()==0||withBracket.size()<=bracket.size())throw new AssertionError("Actual native connected model did not combine source beam and bracket");
        for(var key:List.of(GirderModels.SEGMENT_TOP,GirderModels.SEGMENT_MIDDLE,GirderModels.SEGMENT_BOTTOM)){var part=new PartialModelRenderState();AllPartialModels.extract(key,part);if(part.mesh==null||part.mesh.size()==0)throw new AssertionError("Real source curve girder part missing after bake/reload");}
        for(var block:List.of(AllBlocks.METAL_GIRDER,AllBlocks.METAL_GIRDER_ENCASED_SHAFT))for(var possible:block.getStateDefinition().getPossibleStates())if(client.getModelManager().getBlockStateModelSet().get(possible)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing native source girder model "+possible);
    });}
    private static int inventory(net.minecraft.world.entity.player.Inventory inventory,Item item){int count=0;for(int i=0;i<inventory.getContainerSize();i++){var stack=inventory.getItem(i);if(stack.is(item))count+=stack.getCount();}return count;}
    private static KineticBlockEntity entity(ServerLevel level,BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos)instanceof KineticBlockEntity entity?entity:null;}
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void use(ClientGameTestContext context,BlockPos pos){look(context,Vec3.atCenterOf(pos).add(0,.499,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),150);context.waitTicks(6);context.getInput().pressKey(options->options.keyUse);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
