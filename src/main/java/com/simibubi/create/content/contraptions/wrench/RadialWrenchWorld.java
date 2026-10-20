package com.simibubi.create.content.contraptions.wrench;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.*;

/** Genuine native clip, survival and six-neighbor shape calls, with no loading reads or rejected scheduled writes. */
final class RadialWrenchWorld implements LevelReader,ScheduledTickAccess {
    private static final List<Direction> SHAPE_ORDER=List.of(Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH,Direction.DOWN,Direction.UP);
    private final ServerLevel level;
    private boolean known=true;
    private final List<Runnable> pending=new ArrayList<>();
    RadialWrenchWorld(ServerLevel level){this.level=level;}
    boolean known(){return known;}
    private boolean loaded(BlockPos pos){boolean loaded=LoadedLevelAccess.isLoaded(level,pos);known&=loaded;return loaded;}
    BlockHitResult nativeRay(ServerPlayer player){
        double range=player.blockInteractionRange();if(!Double.isFinite(range)||range<0)return null;
        var start=player.getEyePosition(1);var result=clip(new ClipContext(start,start.add(player.getViewVector(1).scale(range)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return known?result:null;
    }
    /** Exact native Block.updateFromNeighbourShapes order; updateShape splits LevelReader and tick access in26.3. */
    BlockState updateFromNeighbourShapes(BlockState state,BlockPos pos){
        for(var side:SHAPE_ORDER){var neighbor=pos.relative(side);state=state.updateShape(this,this,pos,side,neighbor,getBlockState(neighbor),level.getRandom());}
        return state;
    }
    void commitTicks(){pending.forEach(Runnable::run);pending.clear();}
    @Override public <T>ScheduledTick<T> createTick(BlockPos pos,T type,int delay,TickPriority priority){loaded(pos);return level.createTick(pos,type,delay,priority);}
    @Override public <T>ScheduledTick<T> createTick(BlockPos pos,T type,int delay){return createTick(pos,type,delay,TickPriority.NORMAL);}
    private <T>LevelTickAccess<T> deferred(LevelTickAccess<T> actual){return new LevelTickAccess<>(){
        @Override public void schedule(ScheduledTick<T> tick){if(loaded(tick.pos()))pending.add(()->actual.schedule(tick));}
        @Override public boolean hasScheduledTick(BlockPos pos,T type){loaded(pos);return actual.hasScheduledTick(pos,type);}
        @Override public boolean willTickThisTick(BlockPos pos,T type){loaded(pos);return actual.willTickThisTick(pos,type);}
        @Override public int count(){return actual.count();}
    };}
    @Override public LevelTickAccess<Block> getBlockTicks(){return deferred(level.getBlockTicks());}
    @Override public LevelTickAccess<Fluid> getFluidTicks(){return deferred(level.getFluidTicks());}
    @Override public ChunkAccess getChunk(int x,int z,ChunkStatus status,boolean create){var chunk=level.getChunkSource().getChunkNow(x,z);known&=chunk!=null;return chunk;}
    @Override public boolean hasChunk(int x,int z){var chunk=level.getChunkSource().getChunkNow(x,z);known&=chunk!=null;return chunk!=null;}
    @Override public BlockState getBlockState(BlockPos pos){loaded(pos);return LoadedLevelAccess.blockState(level,pos);}
    @Override public BlockEntity getBlockEntity(BlockPos pos){loaded(pos);return LoadedLevelAccess.blockEntity(level,pos);}
    @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
    @Override public int getHeight(){return level.getHeight();}
    @Override public int getMinY(){return level.getMinY();}
    @Override public int getHeight(Heightmap.Types type,int x,int z){var chunk=getChunk(x>>4,z>>4,ChunkStatus.FULL,false);return chunk==null?getMinY():chunk.getHeight(type,x&15,z&15)+1;}
    @Override public int getSkyDarken(){return level.getSkyDarken();}
    @Override public BiomeManager getBiomeManager(){return level.getBiomeManager().withDifferentSource(this);}
    @Override public Holder<Biome> getUncachedNoiseBiome(int x,int y,int z){var chunk=getChunk(x>>2,z>>2,ChunkStatus.FULL,false);return chunk==null?registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS):chunk.getNoiseBiome(x,y,z);}
    @Override public boolean isClientSide(){return false;}
    @Override public int getSeaLevel(){return level.getSeaLevel();}
    @Override public DimensionType dimensionType(){return level.dimensionType();}
    @Override public RegistryAccess registryAccess(){return level.registryAccess();}
    @Override public FeatureFlagSet enabledFeatures(){return level.enabledFeatures();}
    @Override public EnvironmentAttributeReader environmentAttributes(){return level.environmentAttributes();}
    @Override public WorldBorder getWorldBorder(){return level.getWorldBorder();}
    @Override public BlockGetter getChunkForCollisions(int x,int z){return getChunk(x,z,ChunkStatus.FULL,false);}
    @Override public List<VoxelShape> getEntityCollisions(Entity entity,AABB bounds){return level.getEntityCollisions(entity,bounds);}
    @Override public LevelLightEngine getLightEngine(){return level.getLightEngine();}
}
