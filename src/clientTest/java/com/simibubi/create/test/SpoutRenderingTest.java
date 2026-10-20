package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.spout.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import java.util.concurrent.atomic.AtomicBoolean;

final class SpoutRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 210 5 10");context.waitFor(client->client.player.getX()>209&&client.level.isLoaded(new BlockPos(212,4,5)),500);
        server.runCommand("fill 207 3 0 216 3 15 minecraft:smooth_quartz");server.runCommand("setblock 210 4 5 create:depot");server.runCommand("setblock 210 6 5 create:spout");server.runCommand("setblock 213 4 5 minecraft:cauldron");server.runCommand("setblock 213 6 5 create:spout");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){
            ((SpoutBlockEntity)level.getBlockEntity(new BlockPos(210,6,5))).tank.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx);
            ((SpoutBlockEntity)level.getBlockEntity(new BlockPos(213,6,5))).tank.insert(FluidVariant.of(Fluids.LAVA),FluidConstants.BUCKET,tx);
            ((DepotBlockEntity)level.getBlockEntity(new BlockPos(210,4,5))).inventory.automation.insert(ItemVariant.of(Items.GLASS_BOTTLE),4,tx);tx.commit();
        }});
        context.getInput().lookAt(new BlockPos(211,5,5));world.getConnection().waitForChunksRender();
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(210,6,5)) instanceof SpoutBlockEntity spout&&spout.processingTicks>=9&&spout.processingTicks<=14,200);
        context.runOnClient(client->{
            var spout=client.level.getBlockEntity(new BlockPos(210,6,5));var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(spout,0.5f,null,true);
            if(!(extracted instanceof SpoutRenderer.State state)||state.tank.mesh==null||state.stream.mesh==null||state.squeeze==0)throw new AssertionError("Spout fluid/stream animation missing");
            for(var bit:state.bits)if(bit.mesh==null||bit.mesh.size()==0)throw new AssertionError("Spout partial mesh missing");
        });context.takeScreenshot("create-vulkan-spout-filling");
        var finished=new AtomicBoolean();for(int i=0;i<60&&!finished.get();i++){context.waitTicks(3);server.runOnServer(ignored->{var depot=(DepotBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(210,4,5));finished.set(java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).filter(slot->slot.stack().is(Items.POTION)).mapToLong(slot->slot.getAmount()).sum()==4);});}
        if(!finished.get())throw new AssertionError("Spout did not fill all four bottles");
        context.waitFor(client->client.level.getBlockState(new BlockPos(213,4,5)).is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON));context.waitTicks(5);context.takeScreenshot("create-vulkan-spout-finished");
    }
}
