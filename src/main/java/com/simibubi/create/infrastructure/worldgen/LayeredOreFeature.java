package com.simibubi.create.infrastructure.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.simibubi.create.infrastructure.worldgen.LayerPattern.Layer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import net.minecraft.world.level.levelgen.feature.BlockReplacement;

import net.minecraft.world.level.levelgen.synth.SimplexNoise;

public class LayeredOreFeature implements Feature {
	public static final com.mojang.serialization.MapCodec<LayeredOreFeature> CODEC=LayeredOreConfiguration.MAP_CODEC.xmap(LayeredOreFeature::new,feature->feature.configuration);
	public final LayeredOreConfiguration configuration;
	public LayeredOreFeature(LayeredOreConfiguration configuration){this.configuration=java.util.Objects.requireNonNull(configuration);}
	@Override public com.mojang.serialization.MapCodec<LayeredOreFeature> codec(){return CODEC;}

	private static final float MAX_LAYER_DISPLACEMENT = 1.75f;
	private static final float LAYER_NOISE_FREQUENCY = 0.125f;

	private static final float MAX_RADIAL_THRESHOLD_REDUCTION = 0.25f;
	private static final float RADIAL_NOISE_FREQUENCY = 0.125f;

	@Override
	public boolean place(WorldGenLevel worldGenLevel, net.minecraft.world.level.chunk.ChunkGenerator generator, RandomSource random, BlockPos origin) {
		LayeredOreConfiguration config=configuration;
		List<LayerPattern> patternPool = config.layerPatterns;

		if (patternPool.isEmpty())
			return false;

		LayerPattern layerPattern = patternPool.get(random.nextInt(patternPool.size()));
        if(!layerPattern.valid()||config.size<=0||config.size>64||!Float.isFinite(config.discardChanceOnAirExposure)||config.discardChanceOnAirExposure<0||config.discardChanceOnAirExposure>1)return false;

		int placedAmount = 0;
		int size = config.size + 1;
		float radius = config.size * 0.5f;
		int radiusBound = Mth.ceil(radius) - 1;
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();

		if (origin.getY() >= worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ()))
			return false;

		List<TemporaryLayerEntry> tempLayers = new ArrayList<>();
		float layerSizeTotal = 0.0f;
		LayerPattern.Layer current = null;
		while (layerSizeTotal < size) {
			Layer next = layerPattern.rollNext(current, random);
			float layerSize = Mth.randomBetween(random, next.minSize, next.maxSize);
			tempLayers.add(new TemporaryLayerEntry(next, layerSize));
			layerSizeTotal += layerSize;
			current = next;
		}

		List<ResolvedLayerEntry> resolvedLayers = new ArrayList<>(tempLayers.size());
		float cumulativeLayerSize = -(layerSizeTotal - size) * random.nextFloat();
		for (TemporaryLayerEntry tempLayerEntry : tempLayers) {
			float rampStartValue = resolvedLayers.size() == 0 ?
					Float.NEGATIVE_INFINITY :
					cumulativeLayerSize * (2.0f / size) - 1.0f;
			cumulativeLayerSize += tempLayerEntry.size();
			if (cumulativeLayerSize < 0)
				continue;
			float radialThresholdMultiplier = Mth.randomBetween(random, 0.5f, 1.0f);
			resolvedLayers.add(new ResolvedLayerEntry(tempLayerEntry.layer, radialThresholdMultiplier, rampStartValue));
		}

		// Choose stacking direction
		float gy = Mth.randomBetween(random, -1.0f, 1.0f);
		gy = (float) Math.cbrt(gy); // Make layer alignment tend towards horizontal more than vertical
		float xzRescale = Mth.sqrt(1.0f - gy * gy);
		float theta = random.nextFloat() * Mth.TWO_PI;
		float gx = Mth.cos(theta) * xzRescale;
		float gz = Mth.sin(theta) * xzRescale;

		SimplexNoise layerDisplacementNoise = new SimplexNoise(random,true);
		SimplexNoise radiusNoise = new SimplexNoise(random,true);

		MutableBlockPos mutablePos = new MutableBlockPos();
		BulkSectionAccess bulkSectionAccess = new BulkSectionAccess(worldGenLevel);

		try {

			for (int dzBlock = -radiusBound; dzBlock <= radiusBound; dzBlock++) {
				float dz = dzBlock * (1.0f / radius);
				if (dz * dz > 1)
					continue;

				for (int dxBlock = -radiusBound; dxBlock <= radiusBound; dxBlock++) {
					float dx = dxBlock * (1.0f / radius);
					if (dz * dz + dx * dx > 1)
						continue;

					for (int dyBlock = -radiusBound; dyBlock <= radiusBound; dyBlock++) {
						float dy = dyBlock * (1.0f / radius);
						float distanceSquared = dz * dz + dx * dx + dy * dy;
						if (distanceSquared > 1)
							continue;
						if (worldGenLevel.isOutsideBuildHeight(y0 + dyBlock))
							continue;

						int currentX = x0 + dxBlock;
						int currentY = y0 + dyBlock;
						int currentZ = z0 + dzBlock;

						float rampValue = gx * dx + gy * dy + gz * dz;
						rampValue += layerDisplacementNoise.get(
								currentX * LAYER_NOISE_FREQUENCY, currentY * LAYER_NOISE_FREQUENCY, currentZ * LAYER_NOISE_FREQUENCY
						) * (MAX_LAYER_DISPLACEMENT / size);

						int layerIndex = Collections.binarySearch(resolvedLayers, new ResolvedLayerEntry(null, 0, rampValue));
						if (layerIndex < 0) layerIndex = -2 - layerIndex; // Counter (-insertionIndex - 1) return result, where insertionIndex = layerIndex + 1
						ResolvedLayerEntry layerEntry = resolvedLayers.get(layerIndex);

						if (distanceSquared > layerEntry.radialThresholdMultiplier)
							continue;

						float thresholdNoiseValue = Mth.map(
								(float) radiusNoise.get(currentX * RADIAL_NOISE_FREQUENCY, currentY * RADIAL_NOISE_FREQUENCY, currentZ * RADIAL_NOISE_FREQUENCY),
								-1.0f, 1.0f, 1.0f - MAX_RADIAL_THRESHOLD_REDUCTION, 1.0f
						);

						if (distanceSquared > layerEntry.radialThresholdMultiplier * thresholdNoiseValue)
							continue;

						LayerPattern.Layer layer = layerEntry.layer;
						List<BlockReplacement> targetBlockStates = layer.rollBlock(random);

						mutablePos.set(currentX, currentY, currentZ);
						if (!worldGenLevel.ensureCanWrite(mutablePos))
							continue;
						LevelChunkSection levelChunkSection = bulkSectionAccess.getSection(mutablePos);
						if (levelChunkSection == null)
							continue;

						int localX = SectionPos.sectionRelative(currentX);
						int localY = SectionPos.sectionRelative(currentY);
						int localZ = SectionPos.sectionRelative(currentZ);
						BlockState blockState = levelChunkSection.getBlockState(localX, localY, localZ);
                        if(blockState.hasBlockEntity())continue;

						for (BlockReplacement targetBlockState : targetBlockStates) {
							if (!canPlaceOre(blockState, bulkSectionAccess::getBlockState, random, config,
								targetBlockState, mutablePos))
								continue;
							if (targetBlockState.state().isAir()||targetBlockState.state().hasBlockEntity())
								continue;
							levelChunkSection.setBlockState(localX, localY, localZ, targetBlockState.state(), false);
							++placedAmount;
							break;
						}

					}
				}
			}

		} catch (Throwable throwable1) {
			try {
				bulkSectionAccess.close();
			} catch (Throwable throwable) {
				throwable1.addSuppressed(throwable);
			}

			throw throwable1;
		}

		bulkSectionAccess.close();
		return placedAmount > 0;
	}

	public boolean canPlaceOre(BlockState pState, Function<BlockPos, BlockState> pAdjacentStateAccessor,
		RandomSource pRandom, LayeredOreConfiguration pConfig, BlockReplacement pTargetState,
		BlockPos.MutableBlockPos pMatablePos) {
		if (!pTargetState.target().test(pState, pMatablePos, pRandom))
			return false;
		if (shouldSkipAirCheck(pRandom, pConfig.discardChanceOnAirExposure))
			return true;

		return !net.minecraft.world.level.levelgen.feature.AbstractOreFeature.isAdjacentToAir(pAdjacentStateAccessor, pMatablePos);
	}

	protected boolean shouldSkipAirCheck(RandomSource pRandom, float pChance) {
		return pChance <= 0 ? true : pChance >= 1 ? false : pRandom.nextFloat() >= pChance;
	}

	private record TemporaryLayerEntry(Layer layer, float size) { }

	private record ResolvedLayerEntry(Layer layer, float radialThresholdMultiplier, float rampStartValue) implements Comparable<ResolvedLayerEntry> {
		@Override
		public int compareTo(LayeredOreFeature.ResolvedLayerEntry b) {
			return Float.compare(rampStartValue, b.rampStartValue);
		}
	}
}
