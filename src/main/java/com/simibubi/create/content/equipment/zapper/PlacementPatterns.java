package com.simibubi.create.content.equipment.zapper;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import com.google.common.base.Predicates;
import com.mojang.serialization.Codec;
import com.simibubi.create.AllDataComponents;


import io.netty.buffer.ByteBuf;


import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

public enum PlacementPatterns implements StringRepresentable {
	Solid("I_PATTERN_SOLID"),
	Checkered("I_PATTERN_CHECKERED"),
	InverseCheckered("I_PATTERN_CHECKERED_INVERSED"),
	Chance25("I_PATTERN_CHANCE_25"),
	Chance50("I_PATTERN_CHANCE_50"),
	Chance75("I_PATTERN_CHANCE_75");

	public static final Codec<PlacementPatterns> CODEC = StringRepresentable.fromValues(PlacementPatterns::values);
	public static final StreamCodec<ByteBuf, PlacementPatterns> STREAM_CODEC = com.simibubi.create.content.equipment.zapper.ZapperCodecs.enumCodec(PlacementPatterns.class);

	public final String translationKey;
	public final String iconKey;

	private PlacementPatterns(String icon) {
		this.translationKey = name().toLowerCase(java.util.Locale.ROOT);
		this.iconKey = icon;
	}

	public static void applyPattern(List<BlockPos> blocksIn, ItemStack stack, RandomSource random) {
		PlacementPatterns pattern = stack.getOrDefault(AllDataComponents.PLACEMENT_PATTERN, Solid);
		Predicate<BlockPos> filter = Predicates.alwaysFalse();

		switch (pattern) {
		case Chance25:
			filter = pos -> random.nextBoolean() || random.nextBoolean();
			break;
		case Chance50:
			filter = pos -> random.nextBoolean();
			break;
		case Chance75:
			filter = pos -> random.nextBoolean() && random.nextBoolean();
			break;
		case Checkered:
			filter = pos -> (pos.getX() + pos.getY() + pos.getZ()) % 2 == 0;
			break;
		case InverseCheckered:
			filter = pos -> (pos.getX() + pos.getY() + pos.getZ()) % 2 != 0;
			break;
		case Solid:
		default:
			break;
		}

		blocksIn.removeIf(filter);
	}

	@Override
	public @NotNull String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
