package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.processing.burner.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

final class LitBurnerRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var pos=new BlockPos(640,4,5);server.runCommand("gamemode spectator @p");server.runCommand("tp @p 640 7 9");context.waitFor(client->Math.abs(client.player.getX()-640.5)<0.1&&client.level.isLoaded(pos),500);
        server.runCommand("fill 635 3 1 651 3 12 minecraft:smooth_quartz");server.runCommand("setblock 640 4 5 create:blaze_burner[blaze=none]");server.runCommand("tp @p 640 4 9");server.runCommand("gamemode creative @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:flint_and_steel");
        context.waitFor(client->client.player.getMainHandItem().is(Items.FLINT_AND_STEEL)&&!client.player.isSpectator()&&client.player.getY()<4.1,200);look(context,pos);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockState(pos).is(AllBlocks.LIT_BLAZE_BURNER),200);check(context,pos,LitBlazeBurnerBlock.FlameType.REGULAR);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-empty-burner-regular-fire");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:soul_sand");context.waitFor(client->client.player.getMainHandItem().is(Items.SOUL_SAND),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockState(pos).getValue(LitBlazeBurnerBlock.FLAME_TYPE)==LitBlazeBurnerBlock.FlameType.SOUL,200);check(context,pos,LitBlazeBurnerBlock.FlameType.SOUL);context.takeScreenshot("create-vulkan-empty-burner-soul-fire");
        var oldModel=new AtomicReference<Object>();context.runOnClient(client->oldModel.set(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(pos))));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitTicks(5);check(context,pos,LitBlazeBurnerBlock.FlameType.SOUL);context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(pos))==oldModel.get())throw new AssertionError("Lit burner reused stale model after reload");});
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_shovel");context.waitFor(client->client.player.getMainHandItem().is(Items.IRON_SHOVEL),200);look(context,pos);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockState(pos).is(AllBlocks.BLAZE_BURNER)&&client.level.getBlockState(pos).getValue(BlazeBurnerBlock.HEAT_LEVEL)==BlazeBurnerBlock.HeatLevel.NONE,200);context.takeScreenshot("create-vulkan-empty-burner-extinguished");server.runCommand("gamemode spectator @p");
    }
    private static void look(ClientGameTestContext context,BlockPos pos){context.getInput().lookAt(pos);context.waitFor(client->client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(pos),200);}
    private static void check(ClientGameTestContext context,BlockPos pos,LitBlazeBurnerBlock.FlameType flame){context.runOnClient(client->{
        var state=client.level.getBlockState(pos);var model=client.getModelManager().getBlockStateModelSet().get(state);var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();model.emitQuads(mesh.emitter(),client.level,pos,state,net.minecraft.util.RandomSource.create(),face->false);
        if(mesh.immutableCopy().size()!=42||state.getValue(LitBlazeBurnerBlock.FLAME_TYPE)!=flame||client.level.getBlockEntity(pos)!=null)throw new AssertionError("Original lit burner OBJ/fire mesh missing or incorrect flame state");
    });}
}
