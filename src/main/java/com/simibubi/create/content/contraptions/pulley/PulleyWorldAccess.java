package com.simibubi.create.content.contraptions.pulley;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Native rope mutation never forces chunks or invokes its own length-break side effects. */
final class PulleyWorldAccess {
    static boolean transferAllowed(){return net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getLifecycle()==net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.Lifecycle.NONE;}
    static void requireTransfer(){if(!transferAllowed())throw new IllegalStateException("Cannot transfer rope world ownership during a Fabric transaction");}
    static BlockState required(Level level,BlockPos pos)throws AssemblyException{
        if(!level.isInValidBounds(pos)||!LoadedLevelAccess.isLoaded(level,pos))throw AssemblyException.entitiesNotReady(pos);
        LoadedEntityAccess.requireReady(level,pos);return LoadedLevelAccess.blockState(level,pos);
    }
    static void requirePerimeter(Level level,BlockPos pos)throws AssemblyException{required(level,pos);for(var side:Direction.values())if(!LoadedLevelAccess.isLoaded(level,pos.relative(side)))throw AssemblyException.entitiesNotReady(pos.relative(side));}
    static void setMoving(ServerLevel level,BlockPos pos,BlockState state){
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)throw new IllegalStateException("Validated rope chunk vanished");var before=chunk.getBlockState(pos);if(before==state)return;
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_MOVE_BY_PISTON|Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        chunk.setBlockState(pos,state,flags);if(chunk.getBlockState(pos)!=state)throw new IllegalStateException("Could not transfer rope cell "+pos);
        level.setBlocksDirty(pos,before,state);level.sendBlockUpdated(pos,before,state,flags);level.updatePOIOnBlockStateChange(pos,before,state);
    }
    private PulleyWorldAccess(){}
}
