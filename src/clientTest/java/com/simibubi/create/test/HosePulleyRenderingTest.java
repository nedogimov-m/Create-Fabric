package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.hosePulley.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.atomic.*;

final class HosePulleyRenderingTest {
    private static final BlockPos P=new BlockPos(271,10,8),TANK=new BlockPos(267,10,8),FAR=new BlockPos(311,5,8);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 274 11 17");context.waitFor(client->client.player.getX()>273&&client.level.isLoaded(FAR),500);
        server.runCommand("fill 264 2 4 314 2 14 minecraft:smooth_quartz");server.runCommand("fill 267 3 6 312 5 11 minecraft:stone");server.runCommand("fill 268 4 7 311 5 10 minecraft:water");
        server.runCommand("setblock 271 10 8 create:hose_pulley[facing=north]");server.runCommand("setblock 272 10 8 create:creative_motor[facing=west]");
        context.waitFor(client->client.level.getBlockEntity(P) instanceof HosePulleyBlockEntity);world.getConnection().waitForChunksRender();context.getInput().lookAt(new BlockPos(271,7,8));
        server.runOnServer(ignored->{var motor=(KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(P.east());motor.adjustGeneratedSpeed(true);for(int i=0;i<2;i++)motor.adjustGeneratedSpeed(false);});
        context.waitFor(client->client.level.getBlockEntity(P) instanceof HosePulleyBlockEntity pulley&&pulley.getOffset()>2&&pulley.getOffset()<5,200);
        context.runOnClient(client->{
            var rendered=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(P),0.5f,null,true);
            if(!(rendered instanceof HosePulleyRenderer.State state)||state.sections.size()<2||state.coil.mesh==null||state.coil.mesh.size()==0||state.shaft.mesh==null)throw new AssertionError("Hose pulley moving geometry missing");
            if(state.sections.stream().anyMatch(section->section.model().mesh==null||section.model().mesh.size()==0))throw new AssertionError("Empty hanging hose section");
            var atlas=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);var scroll=atlas.getSprite(Create.asResource("block/hose_pulley_coil_scroll"));
            if(scroll==atlas.getSprite(Create.asResource("missing_hose_sprite")))throw new AssertionError("Scrolling hose coil material not stitched");
        });context.takeScreenshot("create-vulkan-hose-lowering");
        context.waitFor(client->client.level.getBlockEntity(P) instanceof HosePulleyBlockEntity pulley&&!pulley.isMoving()&&pulley.getOffset()==6,200);server.runCommand("setblock 272 10 8 minecraft:air");
        server.runCommand("fill 267 10 8 267 17 8 create:fluid_tank");server.runCommand("setblock 268 10 8 create:mechanical_pump[facing=west]");server.runCommand("setblock 269 10 8 create:glass_fluid_pipe[axis=x]");server.runCommand("setblock 270 10 8 create:fluid_pipe[east=true,west=true]");
        server.runCommand("setblock 268 9 8 create:cogwheel[axis=x]");server.runCommand("setblock 267 9 8 create:creative_motor[facing=east]");
        server.runOnServer(ignored->{var motor=(KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(new BlockPos(267,9,8));for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);});
        context.waitFor(client->client.level.getBlockEntity(TANK) instanceof FluidTankBlockEntity tank&&tank.getTankInventory().getFluidAmount()>0,400);context.waitTicks(8);context.takeScreenshot("create-vulkan-hose-draining-reservoir");
        // A ticking chunk also retains its immediate border; the far end lies beyond that border.
        server.runOnServer(ignored->world.getConnection().getServerLevel().getChunkSource().addTicket(new Ticket(TicketType.FORCED,32),new ChunkPos(16,0)));
        server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int attempt=0;attempt<120&&!unloaded.get();attempt++){context.waitTicks(5);server.runOnServer(ignored->unloaded.set(!world.getConnection().getServerLevel().isLoaded(FAR)));}
        if(!unloaded.get()){
            server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var chunk=new ChunkPos(FAR.getX()>>4,FAR.getZ()>>4);Create.LOGGER.error("Hose reservoir chunk: {}, tickets: {}",level.getChunkSource().getChunkDebugData(chunk),level.getDataStorage().computeIfAbsent(net.minecraft.world.level.TicketStorage.TYPE).getTickets(chunk.pack()));});
            throw new AssertionError("Hose reservoir search kept the far chunk loaded");
        }
        context.waitTicks(25);var amount=new AtomicLong();server.runOnServer(ignored->amount.set(((FluidTankBlockEntity)world.getConnection().getServerLevel().getBlockEntity(TANK)).getTankInventory().getFluidAmount()));context.waitTicks(25);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(level.isLoaded(FAR))throw new AssertionError("Hose search reloaded the far chunk");if(((FluidTankBlockEntity)level.getBlockEntity(TANK)).getTankInventory().getFluidAmount()!=amount.get())throw new AssertionError("Hose consumed unloaded world sources");});
        server.runCommand("tp @p 274 11 17");context.waitFor(client->client.player.getX()<300&&client.level.isLoaded(FAR),500);context.waitTicks(100);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(((FluidTankBlockEntity)level.getBlockEntity(TANK)).getTankInventory().getFluidAmount()<=amount.get())throw new AssertionError("Hose did not resume draining after pool chunk returned");level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,new ChunkPos(16,0),1);});
        context.getInput().lookAt(new BlockPos(271,7,8));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-hose-pool-reloaded");
    }
}
