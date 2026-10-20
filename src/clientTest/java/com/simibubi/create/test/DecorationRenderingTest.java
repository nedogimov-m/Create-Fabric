package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.*;
import com.simibubi.create.content.decoration.bracket.*;
import com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer;
import com.simibubi.create.content.kinetics.base.KineticRenderState;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;

/** Real finite survival bracket input, static meshes/UV, reload and owner retirement. Entire route UNRUN.
 * Reservation X76000..76064/Z0..16; unload77400/77400.
 */
final class DecorationRenderingTest {
    private static final BlockPos SHAFT=new BlockPos(76005,6,5),PIPE=new BlockPos(76013,6,5),GLASS=new BlockPos(76021,6,5),SCAFFOLD=new BlockPos(76029,6,5),TRAPDOOR=new BlockPos(76037,6,5),CASING=new BlockPos(76045,6,5);
    private static final List<BlockPos> OWNERS=List.of(SHAFT,PIPE,GLASS);
    static void run(ClientGameTestContext c,TestSingleplayerContext w){
        c.runOnClient(client->{if(!RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"))throw new AssertionError("Native decoration route requires actual Vulkan");});
        var server=w.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 76028 10 10");
        c.waitFor(client->List.of(SHAFT,PIPE,GLASS,SCAFFOLD,TRAPDOOR,CASING).stream().allMatch(client.level::isLoaded),500);
        server.runCommand("fill 76000 4 0 76064 4 16 minecraft:smooth_quartz");
        block(w,SHAFT.west(),"create:creative_motor[facing=east]");block(w,SHAFT,"create:shaft[axis=x]");
        block(w,PIPE,"create:fluid_pipe[east=true,west=true]");block(w,GLASS,"create:glass_fluid_pipe[axis=x]");
        block(w,SCAFFOLD,"create:andesite_scaffolding[bottom=true]");block(w,SCAFFOLD.east(),"create:andesite_scaffolding[bottom=true]");
        block(w,TRAPDOOR,"create:framed_glass_trapdoor[half=bottom,open=false]");block(w,TRAPDOOR.east(),"create:framed_glass_trapdoor[half=bottom,open=false]");
        block(w,CASING,"create:shadow_steel_casing");block(w,CASING.east(),"create:shadow_steel_casing");block(w,CASING.south(3),"create:refined_radiance_casing");
        server.runCommand("clear @p");server.runCommand("item replace entity @p hotbar.0 with create:metal_bracket 3");server.runCommand("item replace entity @p hotbar.1 with create:wrench");server.runCommand("gamemode survival @p");
        teleport(c,w,SHAFT.getX()+.5,6,8);c.getInput().pressKey(InputConstants.KEY_2);c.waitFor(client->client.player.getInventory().getSelectedSlot()==1,200);c.getInput().pressKey(InputConstants.KEY_1);
        c.waitFor(client->client.player.getMainHandItem().is(AllBlocks.METAL_BRACKET.asItem())&&client.player.getMainHandItem().getCount()==3,250);
        for(var pos:OWNERS){teleport(c,w,pos.getX()+.5,6,8);use(c,pos);await(c,w,level->present(level,pos),250,"Physical original bracket did not reach actual native owner");}
        await(c,w,level->OWNERS.stream().allMatch(pos->present(level,pos))&&count(level,AllBlocks.METAL_BRACKET.asItem())==0,200,"Original finite three bracket stock is not conserved");
        c.waitFor(client->OWNERS.stream().allMatch(pos->client.level.getBlockEntity(pos)!=null&&component(client.level.getBlockEntity(pos)).isBracketPresent()),250);
        verify(c);w.getConnection().waitForChunksRender();c.takeScreenshot("create-vulkan-decoration21-static-shaft-and-pipe-brackets-source-scaffold-trapdoor-legacy-ct");
        var before=c.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(SCAFFOLD)));
        var reload=c.computeOnClient(client->client.reloadResourcePacks());c.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);reload.join();
        c.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(SCAFFOLD))==before)throw new AssertionError("Source native connected model retained old reload identity");});verify(c);c.takeScreenshot("create-vulkan-decoration21-after-native-resource-reload");
        var old=server.computeOnServer(ignored->OWNERS.stream().map(pos->LoadedLevelAccess.blockEntity(w.getConnection().getServerLevel(),pos)).toList());
        server.runCommand("gamemode spectator @p");teleport(c,w,77400,20,77400);
        await(c,w,level->OWNERS.stream().allMatch(pos->level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null),800,"Actual decoration chunks must become unavailable");
        NativeFixtureChunkUnload.finish(c,w,old.toArray(net.minecraft.world.level.block.entity.BlockEntity[]::new));
        teleport(c,w,76028,10,10);await(c,w,level->OWNERS.stream().allMatch(pos->present(level,pos)&&old.stream().noneMatch(previous->LoadedLevelAccess.blockEntity(level,pos)==previous)),600,"Actual fresh native owners did not recover the original three paid bracket states");
        c.waitFor(client->OWNERS.stream().allMatch(pos->client.level.getBlockEntity(pos)!=null&&component(client.level.getBlockEntity(pos)).isBracketPresent()),400);verify(c);w.getConnection().waitForChunksRender();c.takeScreenshot("create-vulkan-decoration21-after-true-owner-retirement-and-return");
        server.runCommand("gamemode survival @p");teleport(c,w,SHAFT.getX()+.5,6,8);c.getInput().pressKey(InputConstants.KEY_2);c.waitFor(client->client.player.getMainHandItem().is(AllItems.WRENCH),200);
        for(var pos:OWNERS){teleport(c,w,pos.getX()+.5,6,8);use(c,pos);await(c,w,level->!present(level,pos),250,"Physical native wrench did not remove original bracket");}
        await(c,w,level->count(level,AllBlocks.METAL_BRACKET.asItem())==3&&OWNERS.stream().allMatch(pos->!present(level,pos)),200,"Actual wrench must return exactly the original three paid brackets");c.takeScreenshot("create-vulkan-decoration21-exact-three-original-bracket-refund");
    }
    private static BracketedBlockEntityBehaviour component(net.minecraft.world.level.block.entity.BlockEntity be){return com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(be,BracketedBlockEntityBehaviour.TYPE);}
    private static boolean present(ServerLevel level,BlockPos pos){var b=component(LoadedLevelAccess.blockEntity(level,pos));return b!=null&&b.isBracketPresent();}
    private static int count(ServerLevel level,Item item){var player=level.getServer().getPlayerList().getPlayers().getFirst();return player.getInventory().countItem(item);}
    private static void verify(ClientGameTestContext c){c.runOnClient(client->{
        for(var block:blocks())for(var state:block.getStateDefinition().getPossibleStates())if(client.getModelManager().getBlockStateModelSet().get(state)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing actual decoration source model "+state);
        var shaft=(KineticRenderState)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(SHAFT),.5f,null,false);
        var pipe=(BracketRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(PIPE),.5f,null,false);
        var glass=(TransparentStraightPipeRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(GLASS),.5f,null,false);
        if(shaft==null||pipe==null||glass==null||shaft.attachedBracket.mesh==null||shaft.attachedBracket.mesh.size()==0||pipe.bracket.mesh==null||pipe.bracket.mesh.size()==0||glass.bracket.mesh==null||glass.bracket.mesh.size()==0)throw new AssertionError("Static source bracket meshes missing from one of the real native host renderer states");
        for(var partial:List.of(shaft.attachedBracket,pipe.bracket,glass.bracket))partial.mesh.forEach(quad->{for(int i=0;i<4;i++)if(!Float.isFinite(quad.x(i))||!Float.isFinite(quad.y(i))||!Float.isFinite(quad.z(i)))throw new AssertionError("Nonfinite actual source bracket vertex");});
        verifyUV(client,SCAFFOLD,Direction.NORTH,"scaffold/andesite_scaffold_connected",2,2);
        verifyUV(client,TRAPDOOR,Direction.UP,"palettes/framed_glass_connected",8,16);
        verifyUV(client,CASING,Direction.UP,"shadow_steel_casing_connected",8,16);
    });}
    private static void verifyUV(net.minecraft.client.Minecraft client,BlockPos pos,Direction face,String name,int size,int tile){
        var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);var mesh=Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,pos,state,RandomSource.create(42),direction->false);
        var sprite=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getSprite(Create.asResource("block/"+name));var found=new AtomicBoolean();
        mesh.forEach(quad->{if(quad.lightFace()!=face)return;boolean inside=true;for(int i=0;i<4;i++)inside&=quad.u(i)>=sprite.getU((tile%size)/(float)size)-.00001&&quad.u(i)<=sprite.getU((tile%size+1)/(float)size)+.00001&&quad.v(i)>=sprite.getV((tile/size)/(float)size)-.00001&&quad.v(i)<=sprite.getV((tile/size+1)/(float)size)+.00001;if(inside)found.set(true);});
        if(!found.get())throw new AssertionError("Actual native emitted decoration UVs did not enter source tile "+tile+" at "+pos);
    }
    private static List<Block> blocks(){return List.of(AllBlocks.ANDESITE_ALLOY_BLOCK,AllBlocks.BOUND_CARDBOARD_BLOCK,AllBlocks.EXPERIENCE_BLOCK,AllBlocks.ROSE_QUARTZ_BLOCK,AllBlocks.ROSE_QUARTZ_TILES,AllBlocks.SMALL_ROSE_QUARTZ_TILES,AllBlocks.WOODEN_BRACKET,AllBlocks.METAL_BRACKET,AllBlocks.SHADOW_STEEL_CASING,AllBlocks.REFINED_RADIANCE_CASING,AllBlocks.TRAIN_TRAPDOOR,AllBlocks.FRAMED_GLASS_TRAPDOOR,AllBlocks.ANDESITE_LADDER,AllBlocks.BRASS_LADDER,AllBlocks.COPPER_LADDER,AllBlocks.ANDESITE_BARS,AllBlocks.BRASS_BARS,AllBlocks.COPPER_BARS,AllBlocks.ANDESITE_SCAFFOLD,AllBlocks.BRASS_SCAFFOLD,AllBlocks.COPPER_SCAFFOLD);}
    private static void block(TestSingleplayerContext w,BlockPos pos,String state){w.getServer().runCommand("setblock "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+state);}
    private static void teleport(ClientGameTestContext c,TestSingleplayerContext w,double x,double y,double z){w.getServer().runCommand("tp @p "+x+" "+y+" "+z);c.waitFor(client->Math.abs(client.player.getX()-x)<1&&Math.abs(client.player.getZ()-z)<1,400);}
    private static void use(ClientGameTestContext c,BlockPos pos){
        c.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&Math.abs(client.player.getY()-5)<.01,200);
        c.waitTicks(4);
        var angles=c.computeOnClient(client->{var bounds=LoadedLevelAccess.blockState(client.level,pos).getShape(client.level,pos).bounds();var target=new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,pos.getY()+bounds.maxY-.001,pos.getZ()+(bounds.minZ+bounds.maxZ)/2);var diff=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-diff.x,diff.z)),(float)-Math.toDegrees(Math.atan2(diff.y,Math.hypot(diff.x,diff.z))));});
        c.getInput().lookAt(angles.x,angles.y);
        c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),200);
        c.getInput().pressKey(options->options.keyUse);
    }
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> predicate,int ticks,String failure){var ok=new AtomicBoolean();for(int tick=0;tick<ticks;tick++){w.getServer().runOnServer(ignored->ok.set(predicate.test(w.getConnection().getServerLevel())));if(ok.get())return;c.waitTicks(1);}throw new AssertionError(failure);}
}
