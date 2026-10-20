package com.simibubi.create.infrastructure.worldgen;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;



public class LayeredOreConfiguration {
	public static final com.mojang.serialization.MapCodec<LayeredOreConfiguration> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> {
		return instance.group(
			Codec.list(LayerPattern.CODEC)
				.fieldOf("layer_patterns")
				.forGetter(config -> config.layerPatterns),
			Codec.intRange(0, 64)
				.fieldOf("size")
				.forGetter(config -> config.size),
			Codec.floatRange(0.0F, 1.0F)
				.fieldOf("discard_chance_on_air_exposure")
				.forGetter(config -> config.discardChanceOnAirExposure)
		).apply(instance, LayeredOreConfiguration::new);
	});

	public static final Codec<LayeredOreConfiguration> CODEC=MAP_CODEC.codec();
	public final List<LayerPattern> layerPatterns;
	public final int size;
	public final float discardChanceOnAirExposure;

	public LayeredOreConfiguration(List<LayerPattern> layerPatterns, int size, float discardChanceOnAirExposure) {
		this.layerPatterns = layerPatterns;
		this.size = size;
		this.discardChanceOnAirExposure = discardChanceOnAirExposure;
	}
}
