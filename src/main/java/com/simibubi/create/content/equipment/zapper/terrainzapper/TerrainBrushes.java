package com.simibubi.create.content.equipment.zapper.terrainzapper;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;


import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum TerrainBrushes implements StringRepresentable {
	Cuboid(CuboidBrush::new),
	Sphere(SphereBrush::new),
	Cylinder(CylinderBrush::new),
	Surface(()->new DynamicBrush(true)),
	Cluster(()->new DynamicBrush(false));

	public static final Codec<TerrainBrushes> CODEC = StringRepresentable.fromValues(TerrainBrushes::values);
	public static final StreamCodec<ByteBuf, TerrainBrushes> STREAM_CODEC = com.simibubi.create.content.equipment.zapper.ZapperCodecs.enumCodec(TerrainBrushes.class);

	private final ThreadLocal<Brush> brush;

	TerrainBrushes(java.util.function.Supplier<Brush> brush) {
		this.brush = ThreadLocal.withInitial(brush);
	}

	public Brush get() {
		return brush.get();
	}

	@Override
	public @NotNull String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
