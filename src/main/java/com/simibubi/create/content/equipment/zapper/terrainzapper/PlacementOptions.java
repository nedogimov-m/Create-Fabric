package com.simibubi.create.content.equipment.zapper.terrainzapper;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;


import io.netty.buffer.ByteBuf;


import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum PlacementOptions implements StringRepresentable {
	Merged("I_CENTERED"),
	Attached("I_ATTACHED"),
	Inserted("I_INSERTED");

	public static final Codec<PlacementOptions> CODEC = StringRepresentable.fromValues(PlacementOptions::values);
	public static final StreamCodec<ByteBuf, PlacementOptions> STREAM_CODEC = com.simibubi.create.content.equipment.zapper.ZapperCodecs.enumCodec(PlacementOptions.class);

	public final String translationKey;
	public final String iconKey;

	PlacementOptions(String icon) {
		this.translationKey = name().toLowerCase(java.util.Locale.ROOT);
		this.iconKey = icon;
	}

	@Override
	public @NotNull String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
