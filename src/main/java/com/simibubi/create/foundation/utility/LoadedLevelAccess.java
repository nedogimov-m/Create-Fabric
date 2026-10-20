package com.simibubi.create.foundation.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Reads only immediately available chunks: no loading tickets and no wait on FULL conversion. */
public final class LoadedLevelAccess {
    public static boolean isLoaded(Level level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    public static int getBestNeighborSignal(Level level,BlockPos pos) {
        return new SignalView(level).getBestNeighborSignal(pos);
    }
    /** Vanilla signal rules can inspect further neighbors, including wire connections. */
    private record SignalView(Level level) implements SignalGetter {
        @Override public BlockState getBlockState(BlockPos pos) { return blockState(level,pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntity(level,pos); }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }
    public static BlockState blockState(BlockGetter reader,BlockPos pos){
        if(reader instanceof Level level){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk==null?Blocks.AIR.defaultBlockState():chunk.getBlockState(pos);}
        return reader.getBlockState(pos);
    }
    public static BlockEntity blockEntity(BlockGetter reader,BlockPos pos){
        if(reader instanceof Level level){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk==null?null:chunk.getBlockEntity(pos,LevelChunk.EntityCreationType.IMMEDIATE);}
        return reader.getBlockEntity(pos);
    }
    /** Equivalent to Level.setBlock(..., UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE), without its loading lookup. */
    public static boolean setBlockQuiet(Level level,BlockPos pos,BlockState state){
        if(!level.isInValidBounds(pos)||level.isClientSide()||level.isDebug())return false;
        var full=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(full==null)return false;
        // LevelChunk performs heightmaps, lighting, block/entity callbacks and dirty tracking.
        var previous=full.setBlockState(pos,state,18);if(previous==null)return false;
        var actual=full.getBlockState(pos);if(actual!=state)return true;
        if(previous!=actual)level.setBlocksDirty(pos,previous,actual);
        if(full.getFullStatus()!=null&&full.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.BLOCK_TICKING))level.sendBlockUpdated(pos,previous,state,18);
        level.updatePOIOnBlockStateChange(pos,previous,actual);return true;
    }
    private LoadedLevelAccess(){}
}
