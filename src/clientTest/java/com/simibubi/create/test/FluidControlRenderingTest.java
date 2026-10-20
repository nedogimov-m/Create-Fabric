package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.fluids.pipes.valve.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.base.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import java.util.concurrent.atomic.AtomicLong;

final class FluidControlRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 197 6 12");context.waitFor(client->client.player.getX()>196&&client.level.isLoaded(new BlockPos(201,4,5)),500);
        server.runCommand("fill 188 3 0 206 3 15 minecraft:smooth_quartz");server.runCommand("fill 190 4 5 190 5 5 create:fluid_tank");server.runCommand("fill 201 4 5 201 5 5 create:fluid_tank");
        for(int x=191;x<201;x++)server.runCommand("setblock "+x+" 4 5 "+(x==193?"create:mechanical_pump[facing=east]":x==195?"create:smart_fluid_pipe[facing=east,face=floor]":x==197?"create:fluid_valve[facing=up,axis_along_first=false]":x==194||x==196||x==198?"create:glass_fluid_pipe[axis=x]":"create:fluid_pipe[east=true,west=true]"));
        server.runCommand("setblock 193 5 5 create:cogwheel[axis=x]");server.runCommand("setblock 192 5 5 create:creative_motor[facing=east]");server.runCommand("setblock 197 4 4 create:creative_motor[facing=south]");
        server.runCommand("setblock 193 4 9 create:smart_fluid_pipe[facing=south,face=floor]");server.runCommand("setblock 196 4 9 create:smart_fluid_pipe[facing=west,face=wall]");server.runCommand("setblock 199 5 9 create:smart_fluid_pipe[facing=north,face=ceiling]");
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(190,4,5)) instanceof FluidTankBlockEntity tank&&tank.getHeight()==2);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){((FluidTankBlockEntity)level.getBlockEntity(new BlockPos(190,4,5))).storage().insert(FluidVariant.of(Fluids.WATER),12*FluidConstants.BUCKET,tx);tx.commit();}((SmartFluidPipeBlockEntity)level.getBlockEntity(new BlockPos(195,4,5))).setFilter(new ItemStack(Items.LAVA_BUCKET));var motor=(KineticBlockEntity)level.getBlockEntity(new BlockPos(192,5,5));motor.adjustGeneratedSpeed(false);motor.adjustGeneratedSpeed(false);for(var pos:java.util.List.of(new BlockPos(193,4,9),new BlockPos(196,4,9),new BlockPos(199,5,9)))((SmartFluidPipeBlockEntity)level.getBlockEntity(pos)).setFilter(new ItemStack(Items.WATER_BUCKET));});
        context.waitFor(client->client.level.getBlockState(new BlockPos(197,4,5)).getValue(FluidValveBlock.ENABLED)&&client.level.getBlockEntity(new BlockPos(195,4,5)) instanceof SmartFluidPipeBlockEntity pipe&&pipe.getFilter().is(Items.LAVA_BUCKET),300);
        server.runOnServer(ignored->{if(((FluidTankBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(201,4,5))).getTankInventory().getFluidAmount()!=0)throw new AssertionError("Mismatching filter leaked water");});
        context.getInput().lookAt(new BlockPos(196,4,5));world.getConnection().waitForChunksRender();context.waitTicks(5);
        context.runOnClient(client->{
            var valve=client.level.getBlockEntity(new BlockPos(197,4,5));var extracted=client.getBlockEntityRenderDispatcher().tryExtractRenderState(valve,0.5f,null,false);
            if(!(extracted instanceof KineticRenderState state)||state.valvePointer.mesh==null||state.valvePointer.mesh.size()==0||state.valveFacing==null)throw new AssertionError("Valve pointer render state missing");
            for(var pos:java.util.List.of(new BlockPos(195,4,5),new BlockPos(193,4,9),new BlockPos(196,4,9),new BlockPos(199,5,9))){var pipe=client.level.getBlockEntity(pos);var rendered=client.getBlockEntityRenderDispatcher().tryExtractRenderState(pipe,0.5f,null,false);if(!(rendered instanceof SmartFluidPipeRenderer.State filter)||filter.filter.isEmpty())throw new AssertionError("Filter icon render state missing: "+pos);}
        });
        context.takeScreenshot("create-vulkan-fluid-filter-blocked");
        server.runOnServer(ignored->((SmartFluidPipeBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(195,4,5))).setFilter(new ItemStack(Items.WATER_BUCKET)));
        context.waitFor(client->client.level.getBlockEntity(new BlockPos(201,4,5)) instanceof FluidTankBlockEntity tank&&tank.getTankInventory().getFluidAmount()>0,400);
        context.waitTicks(8);context.takeScreenshot("create-vulkan-fluid-filter-passing");
        server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(197,4,4))).adjustGeneratedSpeed(true));
        context.waitFor(client->!client.level.getBlockState(new BlockPos(197,4,5)).getValue(FluidValveBlock.ENABLED),200);
        var stopped=new AtomicLong();server.runOnServer(ignored->stopped.set(((FluidTankBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(201,4,5))).getTankInventory().getFluidAmount()));context.waitTicks(20);
        server.runOnServer(ignored->{if(((FluidTankBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(201,4,5))).getTankInventory().getFluidAmount()!=stopped.get())throw new AssertionError("Closed valve leaked through cached route");});
        context.takeScreenshot("create-vulkan-fluid-valve-closed");
        server.runCommand("tp @p 193.5 5 7.0");context.waitFor(client->Math.abs(client.player.getZ()-7)<0.1);context.getInput().lookAt(new BlockPos(193,4,9));context.waitTicks(3);context.takeScreenshot("create-vulkan-smart-pipe-floor");
        server.runCommand("tp @p 193 4 9.5");context.waitFor(client->Math.abs(client.player.getZ()-9.5)<0.1);context.getInput().lookAt(new BlockPos(196,4,9));context.waitTicks(3);context.takeScreenshot("create-vulkan-smart-pipe-wall");
        server.runCommand("tp @p 199.5 3 12.0");context.waitFor(client->Math.abs(client.player.getZ()-12)<0.1);context.getInput().lookAt(new BlockPos(199,5,9));context.waitTicks(3);context.takeScreenshot("create-vulkan-smart-pipe-ceiling");
    }
}
