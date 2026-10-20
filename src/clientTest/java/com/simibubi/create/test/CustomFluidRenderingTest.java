package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.fluids.potion.*;
import com.simibubi.create.content.fluids.spout.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;

final class CustomFluidRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 350 7 19");context.waitFor(client->client.player.getX()>349&&client.level.isLoaded(new BlockPos(364,4,5)),500);
        server.runCommand("fill 334 3 0 368 3 17 minecraft:smooth_quartz");
        for(int x:new int[]{338,344,350,356,362})server.runCommand("fill "+x+" 4 5 "+x+" 5 5 create:fluid_tank");
        server.runCommand("fill 338 3 10 342 4 14 minecraft:stone");server.runCommand("fill 339 4 11 341 4 13 create:honey");
        server.runCommand("fill 345 3 10 349 4 14 minecraft:stone");server.runCommand("fill 346 4 11 348 4 13 create:chocolate");
        server.runCommand("setblock 355 4 12 create:depot");server.runCommand("setblock 355 6 12 create:spout");server.runCommand("setblock 362 4 12 create:depot");server.runCommand("setblock 362 6 12 create:spout");
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(338,4,5)) instanceof FluidTankBlockEntity tank&&tank.getHeight()==2);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var fluids=java.util.List.of(FluidVariant.of(AllFluids.HONEY),FluidVariant.of(AllFluids.CHOCOLATE),FluidVariant.of(AllFluids.MILK),FluidVariant.of(AllFluids.TEA),PotionFluid.of(1,new PotionContents(Potions.STRENGTH),PotionFluid.BottleType.SPLASH).variant());
            try(var tx=Transaction.openOuter()){
                for(int i=0;i<fluids.size();i++)((FluidTankBlockEntity)level.getBlockEntity(new BlockPos(338+6*i,4,5))).storage().insert(fluids.get(i),8*FluidConstants.BUCKET,tx);
                ((SpoutBlockEntity)level.getBlockEntity(new BlockPos(355,6,12))).tank.insert(FluidVariant.of(AllFluids.TEA),FluidConstants.BUCKET,tx);
                ((SpoutBlockEntity)level.getBlockEntity(new BlockPos(362,6,12))).tank.insert(fluids.getLast(),FluidConstants.BUCKET,tx);
                for(int x:new int[]{355,362})((DepotBlockEntity)level.getBlockEntity(new BlockPos(x,4,12))).inventory.automation.insert(ItemVariant.of(Items.GLASS_BOTTLE),4,tx);
                tx.commit();
            }
        });
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(362,4,5)) instanceof FluidTankBlockEntity tank&&tank.getTankInventory().getAmount()>0,300);context.waitTicks(10);
        context.runOnClient(client->{
            var missing=client.getModelManager().getFluidStateModelSet().get(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
            for(var fluid:java.util.List.of(AllFluids.HONEY,AllFluids.FLOWING_HONEY,AllFluids.CHOCOLATE,AllFluids.MILK,AllFluids.TEA,AllFluids.POTION)){
                var model=client.getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());if(model==missing)throw new AssertionError("Missing fluid model: "+fluid);
            }
            for(int x:new int[]{338,344,350,356,362}){
                var tank=(FluidTankBlockEntity)client.level.getBlockEntity(new BlockPos(x,4,5));var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(tank,0.5f,null,true);
                if(!(extracted instanceof FluidTankRenderer.State state)||state.mesh==null||state.mesh.size()==0)throw new AssertionError("Custom tank geometry missing: "+x);
                if(x==362){int expected=new PotionContents(Potions.STRENGTH).getColor()|0xFF000000;state.mesh.forEach(quad->{if(quad.color(0)!=expected)throw new AssertionError("Potion fluid tint lost");});}
            }
            for(var pos:java.util.List.of(new BlockPos(340,4,12),new BlockPos(347,4,12)))if(client.level.getBlockState(pos).getFluidState().isEmpty())throw new AssertionError("World liquid disappeared");
        });
        context.getInput().lookAt(new BlockPos(350,5,5));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-custom-fluid-tanks");
        context.getInput().lookAt(new BlockPos(347,4,12));context.takeScreenshot("create-vulkan-honey-chocolate-pools");
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(362,6,12)) instanceof SpoutBlockEntity spout&&spout.tank.isEmpty(),300);context.waitTicks(10);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int x:new int[]{355,362}){
            var depot=(DepotBlockEntity)level.getBlockEntity(new BlockPos(x,4,12));var slots=java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).toList();
            if(slots.stream().filter(slot->slot.stack().is(x==355?AllItems.BUILDERS_TEA:Items.SPLASH_POTION)).mapToLong(slot->slot.getAmount()).sum()!=4)throw new AssertionError("Four filled bottles missing: "+x);
            if(x==362)for(var slot:slots)if(slot.stack().is(Items.SPLASH_POTION)&&!slot.stack().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,PotionContents.EMPTY).is(Potions.STRENGTH))throw new AssertionError("Splash potion contents lost");
        }});
        context.getInput().lookAt(new BlockPos(359,5,12));context.takeScreenshot("create-vulkan-tea-and-potion-spouts");
        var old=new java.util.concurrent.atomic.AtomicReference<Object>();context.runOnClient(client->old.set(client.getModelManager().getFluidStateModelSet().get(AllFluids.POTION.defaultFluidState())));
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitTicks(5);
        context.runOnClient(client->{if(old.get()==client.getModelManager().getFluidStateModelSet().get(AllFluids.POTION.defaultFluidState()))throw new AssertionError("Custom fluid model not rebuilt on resource reload");});
        for(int x:new int[]{340,347}){
            server.runCommand("tp @p "+x+" 3.0 12");
            context.waitFor(client->Math.abs(client.player.getX()-(x+0.5))<0.1&&client.gameRenderer.mainCamera().blockPosition().equals(new BlockPos(x,4,12)),200);context.waitTicks(3);
            context.runOnClient(client->{
                try(var renderer=new net.minecraft.client.renderer.fog.FogRenderer()){
                    var fog=renderer.setupFog(client.gameRenderer.mainCamera(),8,client.getDeltaTracker(),0,client.level);
                    int expected=x==340?0xEAAE2F:0x622020;float distance=x==340?12:3;
                    if(Math.abs(fog.color.x-((expected>>16)&255)/255f)>0.001f||Math.abs(fog.color.y-((expected>>8)&255)/255f)>0.001f||Math.abs(fog.color.z-(expected&255)/255f)>0.001f||fog.environmentalStart!=-8||fog.environmentalEnd!=distance)
                        throw new AssertionError("Native fog extraction lost custom fluid properties: "+x+" "+fog.color+" / "+fog.environmentalEnd);
                }
            });
            context.takeScreenshot(x==340?"create-vulkan-submerged-honey":"create-vulkan-submerged-chocolate");
        }
        // Exercise real client input, including the LocalPlayer swim and sprint paths.
        server.runCommand("fill 371 1 9 377 7 15 minecraft:stone");server.runCommand("fill 372 2 10 376 6 14 create:honey");
        server.runCommand("gamemode survival @p");server.runCommand("tp @p 374 3.0 12 0 0");
        context.waitFor(client->Math.abs(client.player.getX()-374.5)<0.1&&!client.player.getAbilities().flying&&client.player.isInLiquid()&&com.simibubi.create.content.fluids.FluidPhysics.eyesImmersed(client.player),300);
        context.getInput().holdKey(options->options.keyJump);context.waitTicks(5);
        context.runOnClient(client->{if(client.player.getDeltaMovement().y<=0)throw new AssertionError("Jump input does not swim upwards in honey");});
        context.getInput().releaseKey(options->options.keyJump);
        context.getInput().holdKey(options->options.keyShift);context.waitTicks(5);
        context.runOnClient(client->{if(client.player.getDeltaMovement().y>=0)throw new AssertionError("Sneak input does not swim downwards in honey");});
        context.getInput().releaseKey(options->options.keyShift);
        context.getInput().holdKey(options->options.keyUp);context.getInput().holdKey(options->options.keySprint);
        context.waitFor(client->client.player.isSwimming(),60);context.takeScreenshot("create-vulkan-honey-swimming");
        context.getInput().releaseKey(options->options.keyUp);context.getInput().releaseKey(options->options.keySprint);
        server.runCommand("gamemode spectator @p");
    }
}
