package com.simibubi.create.test;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.glue.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

final class SuperGlueRenderingTest {
    private static final BlockPos P=new BlockPos(3003,4,5),Q=P.east(2),R=P.east(5);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());context.runOnClient(client->{if(client.gui.hud.isHidden())client.gui.hud.toggle();});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 3006.5 8 12.5");context.waitFor(client->Math.abs(client.player.getX()-3006.5)<.1,300);
        context.waitFor(client->client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(P.getX()>>4,0))&&client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(R.getX()>>4,0)),300);
        server.runCommand("fill 2998 3 0 3013 3 15 minecraft:smooth_quartz");server.runCommand("fill 3003 4 5 3005 4 5 minecraft:stone");server.runCommand("setblock 3008 4 5 minecraft:stone");
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.SUPER_GLUE));player.setHealth(player.getMaxHealth());player.resetFallDistance();player.inventoryMenu.broadcastFullState();});
        server.runCommand("tp @p 3003.5 4 9.5");server.runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&Math.abs(client.player.getZ()-9.5)<.1&&client.player.getMainHandItem().is(AllItems.SUPER_GLUE),200);
        click(context,P);context.waitFor(client->P.equals(SuperGlueSelectionHandler.first()),200);look(context,Vec3.atCenterOf(Q));
        context.waitFor(client->SuperGlueSelectionHandler.latest()!=null&&SuperGlueSelectionHandler.latest().valid()&&SuperGlueSelectionHandler.latest().cluster().size()==3,200);context.waitTicks(25);
        context.runOnClient(client->{if(client.player.getMainHandItem().getDamageValue()!=0)throw new AssertionError("Client glue preview damaged item");});
        context.waitFor(client->client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,P.above())==15&&client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,R.above())==15,300);
        context.runOnClient(client->assertHint(client,"click_to_confirm"));
        screenshot(context,"create-vulkan-super-glue-connected-preview");context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.player.getMainHandItem().getDamageValue()==1&&entities(client).stream().anyMatch(glue->glue.getBoundingBox().equals(SuperGlueEntity.span(P,Q))),200);
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();if(player.getMainHandItem().getDamageValue()!=1||world.getConnection().getServerLevel().getEntitiesOfClass(SuperGlueEntity.class,new AABB(P).inflate(8)).size()!=1)throw new AssertionError("Actual area input did not create one volume for one charge");var used=player.getMainHandItem().copy();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));player.setItemInHand(InteractionHand.OFF_HAND,used);player.inventoryMenu.broadcastFullState();});
        context.waitFor(client->client.player.getMainHandItem().is(Items.STONE)&&client.player.getOffhandItem().is(AllItems.SUPER_GLUE),200);
        look(context,Vec3.atCenterOf(P).add(0,0,.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.waitTicks(4);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockState(P.south()).is(Blocks.STONE)&&client.player.getOffhandItem().getDamageValue()==2&&entities(client).size()==2,200);
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();if(!SuperGlueEntity.isGlued(world.getConnection().getServerLevel(),P,Direction.SOUTH,null)||player.getMainHandItem().getCount()!=1)throw new AssertionError("Native block placement did not create offhand glue exactly once");player.setItemInHand(InteractionHand.MAIN_HAND,player.getOffhandItem().copy());player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastFullState();});
        context.waitFor(client->client.player.getMainHandItem().is(AllItems.SUPER_GLUE)&&client.player.getOffhandItem().isEmpty(),200);
        removeAndReject(context,world);
        reloadAndUnload(context,world);
        context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});
    }
    private static void removeAndReject(ClientGameTestContext context,TestSingleplayerContext world){
        look(context,Vec3.atCenterOf(Q));context.waitFor(client->SuperGlueSelectionHandler.latest()!=null&&SuperGlueSelectionHandler.latest().selected()!=null,200);
        screenshot(context,"create-vulkan-super-glue-area-and-offhand");context.getInput().holdKey(options->options.keyAttack);
        try{context.waitFor(client->entities(client).size()==1,200);context.waitTicks(6);}finally{context.getInput().releaseKey(options->options.keyAttack);}
        context.runOnClient(client->{for(int i=0;i<3;i++)if(!client.level.getBlockState(P.east(i)).is(Blocks.STONE))throw new AssertionError("Glue removal destroyed a block behind it");if(client.player.getMainHandItem().getDamageValue()!=2)throw new AssertionError("Glue removal consumed a charge");});
        world.getServer().runCommand("tp @p 3006.5 4 9.5");context.waitFor(client->Math.abs(client.player.getX()-3006.5)<.1&&Math.abs(client.player.getZ()-9.5)<.1,200);
        click(context,Q);context.waitFor(client->Q.equals(SuperGlueSelectionHandler.first()),200);look(context,Vec3.atCenterOf(R));
        context.waitFor(client->R.equals(SuperGlueSelectionHandler.latest().hovered())&&SuperGlueSelectionHandler.latest().bounds()!=null&&!SuperGlueSelectionHandler.latest().valid(),200);screenshot(context,"create-vulkan-super-glue-disconnected-preview");context.getInput().pressKey(options->options.keyUse);context.waitTicks(5);
        context.runOnClient(client->assertHint(client,"cannot_reach"));
        context.runOnClient(client->{if(!Q.equals(SuperGlueSelectionHandler.first())||entities(client).size()!=1||client.player.getMainHandItem().getDamageValue()!=2)throw new AssertionError("Disconnected preview accepted or consumed glue");});
        context.getInput().holdKey(options->options.keyShift);try{context.waitTicks(2);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->SuperGlueSelectionHandler.first()==null,200);}finally{context.getInput().releaseKey(options->options.keyShift);}
    }
    private static void reloadAndUnload(ClientGameTestContext context,TestSingleplayerContext world){
        var id=context.computeOnClient(client->entities(client).getFirst().getUUID());var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);
        context.runOnClient(client->{if(entities(client).size()!=1||!entities(client).getFirst().getBoundingBox().equals(SuperGlueEntity.span(P,P.south())))throw new AssertionError("Native metadata/volume lost across resource reload");});
        world.getServer().runCommand("gamemode spectator @p");world.getServer().runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();unloaded.set(level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null&&level.getEntity(id)==null);});}
        if(!unloaded.get())throw new AssertionError("Glue volume prevented its chunk/entity from unloading");
        world.getServer().runCommand("tp @p 3006.5 4 9.5");context.waitFor(client->Math.abs(client.player.getX()-3006.5)<.1&&entities(client).stream().anyMatch(glue->glue.getUUID().equals(id)),400);
        var returned=new AtomicBoolean();for(int i=0;i<300&&!returned.get();i++){world.getServer().runOnServer(ignored->{var entity=world.getConnection().getServerLevel().getEntity(id);returned.set(entity instanceof SuperGlueEntity);});if(!returned.get())context.waitTicks(1);}if(!returned.get())throw new AssertionError("Server glue did not return after actual unload");
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(!((SuperGlueEntity)level.getEntity(id)).getBoundingBox().equals(SuperGlueEntity.span(P,P.south()))||!SuperGlueEntity.isGlued(level,P,Direction.SOUTH,null))throw new AssertionError("Glue bounds/connections did not survive native chunk save/load");});
        world.getServer().runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator(),200);look(context,Vec3.atCenterOf(P));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-super-glue-chunk-return");
    }
    private static List<SuperGlueEntity> entities(Minecraft client){return client.level.getEntitiesOfClass(SuperGlueEntity.class,new AABB(P).inflate(10));}
    private static void click(ClientGameTestContext context,BlockPos pos){look(context,Vec3.atCenterOf(pos));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),200);context.waitTicks(4);context.getInput().pressKey(options->options.keyUse);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void assertHint(Minecraft client,String key){String id="create.super_glue."+key;var expected=net.minecraft.network.chat.Component.translatable(id).getString();if(expected.equals(id))throw new AssertionError("Missing original glue translation: "+id);try{var field=net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString");field.setAccessible(true);var text=(net.minecraft.network.chat.Component)field.get(client.gui.hud);if(text==null||!text.getString().equals(expected))throw new AssertionError("Actual glue HUD hint differs from original translated message");}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
}
