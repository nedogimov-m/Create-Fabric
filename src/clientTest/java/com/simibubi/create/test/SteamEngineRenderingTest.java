package com.simibubi.create.test;

import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.steamEngine.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;

final class SteamEngineRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 410 9 17");context.waitFor(client->Math.abs(client.player.getX()-410.5)<0.1&&client.level.isLoaded(new BlockPos(415,3,6)),500);
        server.runCommand("fill 400 0 0 421 0 19 minecraft:smooth_quartz");server.runCommand("fill 410 4 5 411 4 6 create:fluid_tank");server.runCommand("fill 410 3 5 411 3 6 minecraft:magma_block");
        server.runCommand("setblock 411 3 6 create:blaze_burner[blaze=smouldering]");
        server.runCommand("setblock 407 4 5 create:shaft[axis=z]");server.runCommand("setblock 407 4 6 create:shaft[axis=z]");server.runCommand("setblock 409 4 5 create:steam_engine[face=wall,facing=west]");
        server.runCommand("setblock 413 4 5 create:creative_fluid_tank");server.runCommand("setblock 412 4 5 create:mechanical_pump[facing=west]");server.runCommand("setblock 412 5 5 create:cogwheel[axis=x]");server.runCommand("setblock 413 5 5 create:creative_motor[facing=west]");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var source=(CreativeFluidTankBlockEntity)level.getBlockEntity(new BlockPos(413,4,5));((CreativeFluidTankBlockEntity.CreativeSmartFluidTank)source.getTankInventory()).setContainedFluid(new com.simibubi.create.foundation.fluid.FluidStack(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),81000));
            ((com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity)level.getBlockEntity(new BlockPos(411,3,6))).tryFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL),false,false);
            ((com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(new BlockPos(413,5,5))).adjustGeneratedSpeed(false);
        });
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(407,4,5)) instanceof PoweredShaftBlockEntity shaft&&shaft.getSpeed()==64,300);context.waitTicks(10);
        check(context,new BlockPos(409,4,5));
        context.runOnClient(client->{
            var particle=client.particleEngine.createParticle(new SteamJetParticleData(1),409.5,5,5.5,0.5,1,0);
            if(!(particle instanceof SteamJetParticle steam))throw new AssertionError("Steam particle provider missing");
            var extracted=new net.minecraft.client.renderer.state.level.QuadParticleRenderState();steam.extract(extracted,client.gameRenderer.mainCamera(),0.5f);
            if(extracted.isEmpty()||!extracted.layers().contains(net.minecraft.client.particle.SingleQuadParticle.Layer.OPAQUE))throw new AssertionError("Steam quads missing from native particle extraction");
        });
        context.runOnClient(client->{var tank=(FluidTankBlockEntity)client.level.getBlockEntity(new BlockPos(410,4,5));var state=(FluidTankRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(tank,0.5f,null,true);if(state.gaugeSides.isEmpty()||state.gauge.mesh==null||state.dial.mesh==null||state.gaugeProgress<=0)throw new AssertionError("Boiler gauge mesh/progress missing");});
        context.getInput().lookAt(new BlockPos(409,5,5));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-steam-engine-boiler");
        server.runCommand("setblock 410 4 2 create:shaft[axis=x]");server.runCommand("setblock 410 4 4 create:steam_engine[face=wall,facing=north]");
        server.runCommand("setblock 410 7 5 create:shaft[axis=z]");server.runCommand("setblock 410 5 5 create:steam_engine[face=floor,facing=north]");
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(410,7,5)) instanceof PoweredShaftBlockEntity shaft&&shaft.getSpeed()!=0,300);context.waitTicks(10);
        check(context,new BlockPos(410,4,4));check(context,new BlockPos(410,5,5));
        context.getInput().lookAt(new BlockPos(410,5,5));context.takeScreenshot("create-vulkan-steam-engine-orientations");
        server.runCommand("setblock 412 4 5 minecraft:air");context.waitFor(client->client.level.getBlockEntity(new BlockPos(407,4,5)) instanceof PoweredShaftBlockEntity shaft&&shaft.getSpeed()==0,240);
        server.runCommand("setblock 409 4 5 minecraft:air");context.waitFor(client->client.level.getBlockState(new BlockPos(407,4,5)).is(com.simibubi.create.AllBlocks.SHAFT),100);
        chunkUnload(context,world);
    }
    private static void check(ClientGameTestContext context,BlockPos pos){
        context.runOnClient(client->{var engine=(SteamEngineBlockEntity)client.level.getBlockEntity(pos);var state=client.getBlockEntityRenderDispatcher().tryExtractRenderState(engine,0.5f,null,true);if(!(state instanceof SteamEngineRenderer.State steam)||!steam.visible||steam.piston.mesh==null||steam.linkage.mesh==null||steam.connector.mesh==null||!Float.isFinite(steam.pistonOffset))throw new AssertionError("Steam engine crank-slider extraction failed: "+pos);});
    }
    private static void chunkUnload(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var enginePos=new BlockPos(449,4,5);var output=new BlockPos(430,4,6);
        server.runCommand("tp @p 447 8 15");context.waitFor(client->Math.abs(client.player.getX()-447.5)<0.1&&client.level.isLoaded(new BlockPos(455,4,6)),500);
        server.runCommand("fill 450 4 5 451 4 6 create:fluid_tank");server.runCommand("fill 450 3 5 451 3 6 minecraft:magma_block");server.runCommand("setblock 450 3 5 create:blaze_burner[blaze=smouldering]");
        server.runCommand("setblock 447 4 5 create:shaft[axis=z]");server.runCommand("setblock 447 4 6 create:gearbox[axis=y]");server.runCommand("fill 430 4 6 446 4 6 create:shaft[axis=x]");server.runCommand("setblock 449 4 5 create:steam_engine[face=wall,facing=west]");
        server.runCommand("setblock 453 4 5 create:creative_fluid_tank");server.runCommand("setblock 452 4 5 create:mechanical_pump[facing=west]");server.runCommand("setblock 452 5 5 create:cogwheel[axis=x]");server.runCommand("setblock 453 5 5 create:creative_motor[facing=west]");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var source=(CreativeFluidTankBlockEntity)level.getBlockEntity(new BlockPos(453,4,5));((CreativeFluidTankBlockEntity.CreativeSmartFluidTank)source.getTankInventory()).setContainedFluid(new com.simibubi.create.foundation.fluid.FluidStack(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),81000));
            ((com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity)level.getBlockEntity(new BlockPos(450,3,5))).tryFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL),false,false);
            ((com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(new BlockPos(453,5,5))).adjustGeneratedSpeed(false);
            level.getChunkSource().addTicket(new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.FORCED,32),new net.minecraft.world.level.ChunkPos(26,0));
        });
        context.waitFor(client->client.level.getBlockEntity(output) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity shaft&&Math.abs(shaft.getSpeed())==64,300);
        server.runCommand("tp @p 5000 10 5000");var unloaded=new java.util.concurrent.atomic.AtomicBoolean();
        for(int attempt=0;attempt<120&&!unloaded.get();attempt++){context.waitTicks(5);server.runOnServer(ignored->unloaded.set(!world.getConnection().getServerLevel().isLoaded(enginePos)));}
        if(!unloaded.get())throw new AssertionError("Steam engine kept its unloaded source chunk alive");context.waitTicks(25);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var shaft=(com.simibubi.create.content.kinetics.base.KineticBlockEntity)com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,output);
            if(shaft==null||shaft.getSpeed()!=0||shaft.getStressCapacity()!=0||level.isLoaded(enginePos))throw new AssertionError("Unloaded steam engine still supplies remote loaded network");});
        server.runCommand("tp @p 447 8 15");context.waitFor(client->Math.abs(client.player.getX()-447.5)<0.1&&client.level.getBlockEntity(output) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity shaft&&Math.abs(shaft.getSpeed())==64,500);
        server.runOnServer(ignored->world.getConnection().getServerLevel().getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,new net.minecraft.world.level.ChunkPos(26,0),1));
        context.getInput().lookAt(new BlockPos(447,4,5));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-steam-engine-chunk-returned");
    }
}
