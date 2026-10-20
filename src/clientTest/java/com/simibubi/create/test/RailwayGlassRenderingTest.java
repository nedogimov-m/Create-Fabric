package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.palettes.RailwayGlassCTModel;
import com.simibubi.create.foundation.block.connected.OmnidirectionalCT;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.*;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.*;

/** Actual manual application, native shifted UVs/cache invalidation, fluid overlay, reload and chunk return. UNRUN. */
final class RailwayGlassRenderingTest {
    private static final BlockPos P=new BlockPos(12415,6,5),G=new BlockPos(12415,6,10),TOP=new BlockPos(12423,5,5),W=G.south();
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 12415.5 5 8.5");context.waitFor(client->Math.abs(client.player.getX()-12415.5)<.1&&client.level.isLoaded(G.east())&&client.level.isLoaded(TOP.east()),500);context.waitFor(client->NativeScreenshotReadiness.skyReady(client,new BlockPos(12412,5,8),new BlockPos(12425,5,8)),300);server.runCommand("fill 12410 4 0 12428 4 16 minecraft:smooth_quartz");block(world,P,"create:brass_casing");
        server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:sturdy_sheet 2");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(AllItems.STURDY_SHEET),150);look(context,Vec3.atCenterOf(P));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),150);context.getInput().pressKey(options->options.keyUse);
        waitServer(context,world,level->LoadedLevelAccess.blockState(level,P).is(AllBlocks.RAILWAY_CASING),150,"Native use of actual sturdy sheet failed to make source railway casing");context.waitFor(client->LoadedLevelAccess.blockState(client.level,P).is(AllBlocks.RAILWAY_CASING)&&client.player.getMainHandItem().getCount()==1,150);
        screenshot(context,world,"create-vulkan-source-railway-casing-real-sturdy-sheet-application");
        server.runCommand("fill 12414 5 5 12416 7 5 create:railway_casing");server.runCommand("fill 12414 5 10 12416 7 10 create:framed_glass");server.runCommand("fill 12422 5 4 12424 5 6 create:railway_casing");
        context.waitFor(client->LoadedLevelAccess.blockState(client.level,G.above().east()).is(AllBlocks.FRAMED_GLASS)&&LoadedLevelAccess.blockState(client.level,P.above().east()).is(AllBlocks.RAILWAY_CASING)&&LoadedLevelAccess.blockState(client.level,TOP.north().east()).is(AllBlocks.RAILWAY_CASING),200);world.getConnection().waitForChunksRender();
        context.runOnClient(client->{checkFace(client,P,Direction.SOUTH,"railway_casing_side_connected",54,false);checkFace(client,TOP,Direction.UP,"railway_casing_connected",54,false);checkFace(client,G,Direction.SOUTH,"palettes/framed_glass_connected",54,true);});
        look(context,new Vec3(12416,6.5,9));screenshot(context,world,"create-vulkan-source-casing-layered-sheets-and-framed-glass-connected-boundary");
        var modelKey=new AtomicReference<Object>();context.runOnClient(client->{var state=client.level.getBlockState(G);var model=client.getModelManager().getBlockStateModelSet().get(state);modelKey.set(model.createGeometryKey(client.level,G,state,RandomSource.create(42)));});
        block(world,G.above().west(),"minecraft:air");context.waitFor(client->LoadedLevelAccess.blockState(client.level,G.above().west()).isAir(),150);context.runOnClient(client->{checkFace(client,G,Direction.SOUTH,"palettes/framed_glass_connected",52,true);var state=client.level.getBlockState(G);var next=client.getModelManager().getBlockStateModelSet().get(state).createGeometryKey(client.level,G,state,RandomSource.create(42));if(modelKey.get()!=null&&next!=null&&modelKey.get().equals(next))throw new AssertionError("Real diagonal removal kept stale native CT geometry key");});
        look(context,Vec3.atCenterOf(G));screenshot(context,world,"create-vulkan-source-framed-glass-real-diagonal-concave-tile52");
        // Native water tesselation must actually select its overlay against connected glass.
        for(var side:List.of(Direction.DOWN,Direction.EAST,Direction.WEST,Direction.SOUTH))block(world,W.relative(side),"minecraft:stone");block(world,W,"minecraft:water[level=0]");context.waitFor(client->LoadedLevelAccess.blockState(client.level,W).getFluidState().isSource(),150);context.runOnClient(RailwayGlassRenderingTest::checkWaterOverlay);
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 12415.5 6 14");look(context,Vec3.atCenterOf(G));world.getConnection().waitForChunksRender();screenshot(context,world,"create-vulkan-source-framed-glass-native-water-overlay");
        var old=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.FRAMED_GLASS.defaultBlockState()));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();context.runOnClient(client->{var models=client.getModelManager().getBlockStateModelSet();if(models.get(AllBlocks.FRAMED_GLASS.defaultBlockState())==old)throw new AssertionError("Actual resource reload retained stale connected-glass model wrapper");checkFace(client,P,Direction.SOUTH,"railway_casing_side_connected",54,false);checkFace(client,TOP,Direction.UP,"railway_casing_connected",54,false);checkFace(client,G,Direction.SOUTH,"palettes/framed_glass_connected",52,true);checkWaterOverlay(client);});
        server.runCommand("tp @p 14000 14 14000");waitServer(context,world,level->level.getChunkSource().getChunkNow(P.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(P.east().getX()>>4,0)==null,800,"Real casing/glass boundary chunks did not unload");server.runCommand("tp @p 12415.5 6 14");
        context.waitFor(client->LoadedLevelAccess.blockState(client.level,P).is(AllBlocks.RAILWAY_CASING)&&LoadedLevelAccess.blockState(client.level,G).is(AllBlocks.FRAMED_GLASS)&&LoadedLevelAccess.blockState(client.level,G.above().west()).isAir(),500);world.getConnection().waitForChunksRender();context.runOnClient(client->{checkFace(client,P,Direction.SOUTH,"railway_casing_side_connected",54,false);checkFace(client,G,Direction.SOUTH,"palettes/framed_glass_connected",52,true);});look(context,Vec3.atCenterOf(G));screenshot(context,world,"create-vulkan-source-railway-glass-real-chunk-return-connected-state");
    }
    private static void checkFace(Minecraft client,BlockPos pos,Direction face,String sheet,int tile,boolean glass){
        var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);if(!(model instanceof RailwayGlassCTModel))throw new AssertionError("Actual source block did not install native CT model wrapper at "+pos);
        if(OmnidirectionalCT.build(client.level,pos,state,face).textureIndex()!=tile)throw new AssertionError("Real world source context mismatch at "+pos+" face="+face+" expected tile="+tile);
        var target=client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Create.asResource("block/"+sheet));if(!target.contents().name().equals(Create.asResource("block/"+sheet)))throw new AssertionError("Native connected sheet was not stitched: "+sheet);
        var mesh=Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,pos,state,RandomSource.create(42),side->false);var count=new AtomicInteger();mesh.immutableCopy().forEach(quad->{if(quad.lightFace()!=face)return;count.incrementAndGet();if(glass&&quad.chunkLayer()!=ChunkSectionLayer.CUTOUT)throw new AssertionError("Source framed glass must retain actual cutout geometry");for(int vertex=0;vertex<4;vertex++){float u=(quad.u(vertex)-target.getU0())/(target.getU1()-target.getU0())*8,v=(quad.v(vertex)-target.getV0())/(target.getV1()-target.getV0())*8;if(u<tile%8-.001||u>tile%8+1.001||v<tile/8-.001||v>tile/8+1.001)throw new AssertionError("Native quad UVs did not enter exact source CT sheet tile: "+sheet+" tile="+tile+" u="+u+" v="+v);}});if(count.get()==0)throw new AssertionError("Native source CT model emitted no real face geometry");
    }
    private static void checkWaterOverlay(Minecraft client){
        if(!FluidRenderingRegistry.isBlockTransparent(AllBlocks.FRAMED_GLASS))throw new AssertionError("Source fluid overlay behavior was not registered through native Fabric API");var overlay=client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Identifier.withDefaultNamespace("block/water_overlay"));if(!overlay.contents().name().equals(Identifier.withDefaultNamespace("block/water_overlay")))throw new AssertionError("Native water overlay sprite is missing");var uv=new ArrayList<Vec2>();
        var output=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);if(method.getName().equals("setUv"))uv.add(new Vec2((float)args[0],(float)args[1]));if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->"NativeFluidUvCollector";};return proxy;});
        var state=client.level.getBlockState(W);new FluidRenderer(client.getModelManager().getFluidStateModelSet()).tesselate(client.level,W,layer->output,state,state.getFluidState());long overlayVertices=uv.stream().filter(point->point.x>=overlay.getU0()-.000001&&point.x<=overlay.getU1()+.000001&&point.y>=overlay.getV0()-.000001&&point.y<=overlay.getV1()+.000001).count();if(overlayVertices<4)throw new AssertionError("Actual native water tesselator did not produce the source glass overlay face; collected="+uv.size()+", overlayVertices="+overlayVertices);
    }
    private static void block(TestSingleplayerContext world,BlockPos pos,String state){world.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void screenshot(ClientGameTestContext context,TestSingleplayerContext world,String name){NativeScreenshotReadiness.await(context,world);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void look(ClientGameTestContext context,Vec3 target){var angle=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(predicate.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
