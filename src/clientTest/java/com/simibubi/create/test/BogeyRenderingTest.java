package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Native bogey-only scene, not a train/assembly test. Fixtures use actual source blocks; no motion is fabricated.
 * Actual survival wrench, full source mesh/UV/pivot checks, resource reload and genuine two-chunk persistence.
 * Proposed region14998..15024,z0..16; unload16000. STAGED, UNRUN.
 */
final class BogeyRenderingTest {
    private static final BlockPos S=new BlockPos(15005,6,5),L=S.east(6);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 15008.0 10.0 10.0");context.waitFor(client->Math.abs(client.player.getX()-15008.0)<.1&&Math.abs(client.player.getZ()-10.0)<.1,300);context.waitFor(client->client.level.isLoaded(L)&&Math.abs(client.player.getX()-15008)<.1,500);
        server.runCommand("fill 14998 4 0 15024 4 16 minecraft:smooth_quartz");
        block(world,S.below(),"create:track[shape=zo]");block(world,L.below(),"create:track[shape=xo]");block(world,S,"create:small_bogey[axis=z,waterlogged=false]");block(world,L,"create:large_bogey[axis=x,waterlogged=false]");block(world,S.south(2),"minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var pos:List.of(S,L)){var data=entity(level,pos).getBogeyData().copy();data.putString("NativeMarker","Exact source bogey payload");entity(level,pos).setBogeyData(data);}});
        server.runCommand("item replace entity @p weapon.mainhand with create:wrench");server.runCommand("gamemode survival @p");server.runCommand("tp @p 15005.5 7 7.5");context.waitFor(client->Math.abs(client.player.getX()-15005.5)<.1&&Math.abs(client.player.getZ()-7.5)<.1,300);context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(AllItems.WRENCH),200);
        use(context,S);waitServer(context,world,level->LoadedLevelAccess.blockState(level,S).is(AllBlocks.LARGE_BOGEY),200,"Real survival wrench did not change small to large");
        use(context,S);waitServer(context,world,level->LoadedLevelAccess.blockState(level,S).is(AllBlocks.SMALL_BOGEY)&&entity(level,S).getBogeyData().getStringOr("NativeMarker","").equals("Exact source bogey payload"),200,"Real wrench did not retain source data through both size changes");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,S)instanceof StandardBogeyBlockEntity&&LoadedLevelAccess.blockEntity(client.level,L)instanceof StandardBogeyBlockEntity,200);
        verifyWorldStates(context);verifySourceGeometry(context);world.getConnection().waitForChunksRender();look(context,Vec3.atCenterOf(S).add(3,-.5,0));screenshot(context,"create-vulkan-native-small-large-bogeys-real-source-wheels-shafts");
        var before=context.computeOnClient(client->extract(context,S).bogey.parts.get(2).model().mesh);
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();
        context.runOnClient(client->{if(extract(context,S).bogey.parts.get(2).model().mesh==before)throw new AssertionError("Native bogey cutout cache retained stale mesh through real resource reload");});verifyWorldStates(context);verifySourceGeometry(context);
        var oldSmall=new AtomicReference<StandardBogeyBlockEntity>();var oldLarge=new AtomicReference<StandardBogeyBlockEntity>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();oldSmall.set(entity(level,S));oldLarge.set(entity(level,L));});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 16000 20 16000");
        waitServer(context,world,level->level.getChunkSource().getChunkNow(S.getX()>>4,S.getZ()>>4)==null&&level.getChunkSource().getChunkNow(L.getX()>>4,L.getZ()>>4)==null,800,"Both actual bogey fixture chunks failed to unload");
        NativeFixtureChunkUnload.finish(context,world,oldSmall.get(),oldLarge.get());
        server.runCommand("tp @p 15008.0 10.0 10.0");context.waitFor(client->Math.abs(client.player.getX()-15008.0)<.1&&Math.abs(client.player.getZ()-10.0)<.1,300);waitServer(context,world,level->{var small=entity(level,S);var large=entity(level,L);return small!=null&&large!=null&&small!=oldSmall.get()&&large!=oldLarge.get()&&small.getStyle()==AllBogeyStyles.STANDARD&&large.getStyle()==AllBogeyStyles.STANDARD&&small.getBogeyData().getStringOr("NativeMarker","").equals("Exact source bogey payload")&&large.getBogeyData().getStringOr("NativeMarker","").equals("Exact source bogey payload");},600,"Actual two-chunk return lost/reused source bogey BE/data");
        context.waitFor(client->LoadedLevelAccess.blockEntity(client.level,S) instanceof StandardBogeyBlockEntity&&LoadedLevelAccess.blockEntity(client.level,L) instanceof StandardBogeyBlockEntity,300);verifyWorldStates(context);verifySourceGeometry(context);world.getConnection().waitForChunksRender();look(context,Vec3.atCenterOf(L));screenshot(context,"create-vulkan-native-bogeys-source-meshes-after-true-chunk-return");
        server.runCommand("gamemode survival @p");server.runCommand("tp @p 15005.5 7 7.5");context.waitFor(client->Math.abs(client.player.getX()-15005.5)<.1&&Math.abs(client.player.getZ()-7.5)<.1,300);context.waitFor(client->!client.player.isSpectator(),200);
        int originalCasings=context.computeOnClient(client->{int count=0;for(int i=0;i<client.player.getInventory().getContainerSize();i++){var stack=client.player.getInventory().getItem(i);if(stack.is(AllBlocks.RAILWAY_CASING.asItem()))count+=stack.getCount();}return count;});
        context.getInput().holdKey(options->options.keyShift);use(context,S);context.getInput().releaseKey(options->options.keyShift);
        waitServer(context,world,level->LoadedLevelAccess.blockState(level,S).isAir(),200,"Actual survival sneak wrench did not retrieve source bogey");
        context.waitFor(client->{int count=0;for(int i=0;i<client.player.getInventory().getContainerSize();i++){var stack=client.player.getInventory().getItem(i);if(stack.is(AllBlocks.RAILWAY_CASING.asItem()))count+=stack.getCount();}return count==originalCasings+1;},200);
    }
    private static BogeyBlockEntityRenderer.State extract(ClientGameTestContext context,BlockPos pos){
        var client=net.minecraft.client.Minecraft.getInstance();var entity=(StandardBogeyBlockEntity)LoadedLevelAccess.blockEntity(client.level,pos);return(BogeyBlockEntityRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(entity,.5f,null,true);
    }
    private static void verifyWorldStates(ClientGameTestContext context){context.runOnClient(client->{
        var small=extract(context,S);var large=extract(context,L);if(small==null||large==null||small.yaw!=0||large.yaw!=90||small.bogey.parts.size()!=5||large.bogey.parts.size()!=9)throw new AssertionError("Actual source world BE renderer lost horizontal axes or concrete parts");
        for(var block:new StandardBogeyBlock[]{AllBlocks.SMALL_BOGEY,AllBlocks.LARGE_BOGEY})for(var state:block.getStateDefinition().getPossibleStates())if(client.getModelManager().getBlockStateModelSet().get(state)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing actual native bogey state "+state);
    });}
    private static void verifySourceGeometry(ClientGameTestContext context){context.runOnClient(client->{
        var sourceData=((StandardBogeyBlockEntity)LoadedLevelAccess.blockEntity(client.level,S)).getBogeyData();
        var small=new BogeyRenderState();BogeyRenderers.extract(AllBogeyStyles.STANDARD,BogeySizes.SMALL,sourceData,0,1,small,false);
        requirePart(small,"frame",258);requirePart(small,"small_wheels_1",276);requirePart(small,"small_wheels_-1",276);
        point(part(small,"small_wheels_1"),new Vector3f(),new Vector3f(0,.75f,1));point(part(small,"small_wheels_-1"),new Vector3f(),new Vector3f(0,.75f,-1));
        var zero=new BogeyRenderState();BogeyRenderers.extract(AllBogeyStyles.STANDARD,BogeySizes.LARGE,sourceData,0,1,zero,false);
        var quarter=new BogeyRenderState();BogeyRenderers.extract(AllBogeyStyles.STANDARD,BogeySizes.LARGE,sourceData,90,1,quarter,false);
        var negative=new BogeyRenderState();BogeyRenderers.extract(AllBogeyStyles.STANDARD,BogeySizes.LARGE,sourceData,270,1,negative,false);
        requirePart(quarter,"drive",181);requirePart(quarter,"drive_belt",44);requirePart(quarter,"drive_piston",152);requirePart(quarter,"large_wheels",352);requirePart(quarter,"drive_pin",20);
        if(Math.abs(quarter.pistonOffset-.25f)>.00001||Math.abs(negative.pistonOffset+.25f)>.00001)throw new AssertionError("Real source piston does not have quarter-block sinusoidal stroke");
        point(part(quarter,"drive_pin"),new Vector3f(),new Vector3f(0,1,.25f));point(part(quarter,"drive_pin"),new Vector3f(0,1,0),new Vector3f(0,2,.25f));
        point(part(quarter,"secondary_shaft_0"),new Vector3f(.5f,.5f,.5f),new Vector3f(0,.75f,1));point(part(quarter,"secondary_shaft_1"),new Vector3f(.5f,.5f,.5f),new Vector3f(0,.75f,-1));
        var oldV=new ArrayList<Float>();var newV=new ArrayList<Float>();part(zero,"drive_belt").model().mesh.forEach(quad->oldV.add(quad.v(0)));part(quarter,"drive_belt").model().mesh.forEach(quad->newV.add(quad.v(0)));
        var sprite=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getSprite(Create.asResource("block/bogey/belt_scroll"));float expected=(5/16f*(float)Math.toRadians(90))*(sprite.getV1()-sprite.getV0())*.5f;
        for(int i=0;i<oldV.size();i++)if(Math.abs(newV.get(i)-oldV.get(i)-expected)>.00001)throw new AssertionError("Actual native belt UV did not follow pinned5px-radius scrolling");
    });}
    private static BogeyRenderState.Part part(BogeyRenderState state,String name){return state.parts.stream().filter(part->part.name().equals(name)).findFirst().orElseThrow();}
    private static void requirePart(BogeyRenderState state,String name,int quads){var part=part(state,name);if(part.model().mesh==null||part.model().mesh.size()!=quads)throw new AssertionError("Pinned OBJ face count lost in actual native mesh "+name);part.model().mesh.forEach(quad->{if(quad.chunkLayer()!=net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT)throw new AssertionError("Bogey source cutout layer lost "+name);});}
    private static void point(BogeyRenderState.Part part,Vector3f point,Vector3f expected){part.transform().transformPosition(point);if(point.distance(expected)>.00001)throw new AssertionError("Actual source part pivot "+part.name()+" expected "+expected+" got "+point);}
    private static StandardBogeyBlockEntity entity(ServerLevel level,BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos)instanceof StandardBogeyBlockEntity entity?entity:null;}
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void use(ClientGameTestContext context,BlockPos pos){look(context,Vec3.atCenterOf(pos).add(0,.499,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),150);context.waitTicks(6);context.getInput().pressKey(options->options.keyUse);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
