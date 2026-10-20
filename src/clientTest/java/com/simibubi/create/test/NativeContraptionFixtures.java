package com.simibubi.create.test;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/** Preparation for scripted captures; actual controller/retry and delayed-load tests keep their own clocks. */
final class NativeContraptionFixtures {
    static void awaitEntityReady(ClientGameTestContext context,TestSingleplayerContext world,int timeout,BlockPos... positions){
        var chunks=new LinkedHashMap<Long,BlockPos>();
        for(var pos:positions)chunks.putIfAbsent(ChunkPos.pack(pos),pos.immutable());
        var ready=new AtomicBoolean();var diagnostic=new AtomicReference<String>("no server sample");
        for(int tick=0;tick<timeout&&!ready.get();tick++){
            world.getServer().runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var pending=new ArrayList<String>();
                for(var pos:chunks.values())if(!LoadedEntityAccess.isReady(level,pos))pending.add(pos+" entityTicking="+level.isPositionEntityTicking(pos));
                ready.set(pending.isEmpty());diagnostic.set("tick="+level.getGameTime()+", exactChunks="+chunks.keySet()+", pending="+pending);
            });
            if(!ready.get())context.waitTicks(1);
        }
        if(!ready.get())throw new AssertionError("Fixture native entity data not ready before spawn/seating: "+diagnostic.get());
    }
    static SeatEntity sitDown(ServerLevel level,BlockPos pos,Entity passenger){
        if(!SeatBlock.trySitDown(level,pos,passenger)||!(passenger.getVehicle() instanceof SeatEntity seat)
            ||!seat.blockPosition().equals(pos)||!seat.hasPassenger(passenger)
            ||level.getEntity(passenger.getUUID())!=passenger||level.getEntity(seat.getUUID())!=seat)
            throw new AssertionError("Fixture seating refused: pos="+pos+", nativeReady="+LoadedEntityAccess.isReady(level,pos)
                +", entityTicking="+level.isPositionEntityTicking(pos)+", originalUuid="+passenger.getUUID()
                +", state="+level.getBlockState(pos)+", actualVehicle="+passenger.getVehicle());
        return seat;
    }
    static boolean captureReady(ServerLevel level,BlockPos... sources){
        var checked=new HashSet<Long>();
        try{
            for(var pos:sources){LoadedEntityAccess.requireReady(level,pos);LoadedEntityAccess.requireGlueQueriesReady(level,pos,checked);}
            return true;
        }catch(AssemblyException pending){return false;}
    }
    static void awaitCaptureReady(ClientGameTestContext context,TestSingleplayerContext world,int timeout,BlockPos... sources){
        var ready=new AtomicBoolean();
        for(int i=0;i<timeout&&!ready.get();i++){
            world.getServer().runOnServer(ignored->ready.set(captureReady(world.getConnection().getServerLevel(),sources)));
            if(!ready.get())context.waitTicks(1);
        }
        if(!ready.get())throw new AssertionError("Scripted source block/entity/glue queries did not become ready");
    }
    private NativeContraptionFixtures(){}
}
