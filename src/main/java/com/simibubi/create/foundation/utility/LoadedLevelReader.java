package com.simibubi.create.foundation.utility;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native shape/placement queries over available chunks, including indirect neighbor reads. */
public record LoadedLevelReader(Level level) implements LevelReader {
    @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) { return level.getChunkSource().getChunkNow(x, z); }
    @Override public boolean hasChunk(int x, int z) { return level.getChunkSource().getChunkNow(x, z) != null; }
    @Override public BlockState getBlockState(BlockPos pos) { return LoadedLevelAccess.blockState(level, pos); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return LoadedLevelAccess.blockEntity(level, pos); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinY() { return level.getMinY(); }
    @Override public int getHeight(Heightmap.Types type, int x, int z) {
        if (x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000) return getSeaLevel() + 1;
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk == null ? getMinY() : chunk.getHeight(type, x & 15, z & 15) + 1;
    }
    @Override public int getSkyDarken() { return level.getSkyDarken(); }
    @Override public BiomeManager getBiomeManager() { return level.getBiomeManager().withDifferentSource(this); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { var chunk = level.getChunkSource().getChunkNow(x >> 2, z >> 2); return chunk == null ? registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS) : chunk.getNoiseBiome(x, y, z); }
    @Override public boolean isClientSide() { return level.isClientSide(); }
    @Override public int getSeaLevel() { return level.getSeaLevel(); }
    @Override public DimensionType dimensionType() { return level.dimensionType(); }
    @Override public RegistryAccess registryAccess() { return level.registryAccess(); }
    @Override public FeatureFlagSet enabledFeatures() { return level.enabledFeatures(); }
    @Override public EnvironmentAttributeReader environmentAttributes() { return level.environmentAttributes(); }
    @Override public WorldBorder getWorldBorder() { return level.getWorldBorder(); }
    @Override public BlockGetter getChunkForCollisions(int x, int z) { return level.getChunkSource().getChunkNow(x, z); }
    @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB bounds) { return level.getEntityCollisions(entity, bounds); }
    @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
}
