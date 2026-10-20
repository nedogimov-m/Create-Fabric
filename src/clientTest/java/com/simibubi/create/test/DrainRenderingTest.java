package com.simibubi.create.test;

import com.simibubi.create.content.fluids.drain.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.item.Items;

final class DrainRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 220 5 10");context.waitFor(client->client.player.getX()>219&&client.level.isLoaded(new BlockPos(224,4,5)),500);
        server.runCommand("fill 217 3 0 227 3 15 minecraft:smooth_quartz");server.runCommand("setblock 220 4 5 create:item_drain");server.runCommand("setblock 221 4 5 create:depot");server.runCommand("setblock 224 4 5 create:item_drain");server.runCommand("setblock 225 4 5 create:depot");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){
            ((ItemDrainBlockEntity)level.getBlockEntity(new BlockPos(220,4,5))).storage(Direction.WEST).insert(ItemVariant.of(Items.WATER_BUCKET),1,tx);
            ((ItemDrainBlockEntity)level.getBlockEntity(new BlockPos(224,4,5))).storage(Direction.WEST).insert(ItemVariant.of(Items.LAVA_BUCKET),1,tx);tx.commit();
        }});context.getInput().lookAt(new BlockPos(222,4,5));world.getConnection().waitForChunksRender();
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(220,4,5)) instanceof ItemDrainBlockEntity drain&&drain.processingTicks>=9&&drain.processingTicks<=14,200);
        context.runOnClient(client->{var drain=client.level.getBlockEntity(new BlockPos(220,4,5));var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(drain,0.5f,null,false);if(!(extracted instanceof ItemDrainRenderer.State state)||state.item.isEmpty()||state.stream.mesh==null)throw new AssertionError("Drain rotating item/stream missing");});context.takeScreenshot("create-vulkan-drain-emptying");
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(220,4,5)) instanceof ItemDrainBlockEntity drain&&drain.getHeldItemStack().isEmpty()&&drain.tank.getAmount()>0,300);
        context.runOnClient(client->{for(var pos:java.util.List.of(new BlockPos(220,4,5),new BlockPos(224,4,5))){var drain=client.level.getBlockEntity(pos);var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(drain,0.5f,null,false);if(!(extracted instanceof ItemDrainRenderer.State state)||state.tank.mesh==null||state.tank.mesh.size()==0)throw new AssertionError("Drain reservoir mesh missing");}});
        server.runOnServer(ignored->{for(var pos:java.util.List.of(new BlockPos(221,4,5),new BlockPos(225,4,5))){var depot=(DepotBlockEntity)world.getConnection().getServerLevel().getBlockEntity(pos);if(java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).filter(slot->slot.stack().is(Items.BUCKET)).mapToLong(slot->slot.getAmount()).sum()!=1)throw new AssertionError("Drain did not return empty bucket");}});
        context.waitTicks(5);context.takeScreenshot("create-vulkan-drain-reservoirs");
    }
}
