package com.simibubi.create.content.equipment.zapper.terrainzapper;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.equipment.zapper.ZapperItem;





import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.netty.buffer.ByteBuf;

import org.jetbrains.annotations.NotNull;

public enum TerrainTools implements StringRepresentable {
	Fill("I_FILL"),
	Place("I_PLACE"),
	Replace("I_REPLACE"),
	Clear("I_CLEAR"),
	Overlay("I_OVERLAY"),
	Flatten("I_FLATTEN");

	public static final Codec<TerrainTools> CODEC = StringRepresentable.fromValues(TerrainTools::values);
	public static final StreamCodec<ByteBuf, TerrainTools> STREAM_CODEC = com.simibubi.create.content.equipment.zapper.ZapperCodecs.enumCodec(TerrainTools.class);
	public String translationKey;
	public final String iconKey;

	TerrainTools(String icon) {
		this.translationKey = name().toLowerCase(java.util.Locale.ROOT);
		this.iconKey = icon;
	}

	@Override
	public @NotNull String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public boolean requiresSelectedBlock() {
		return this != Clear && this != Flatten;
	}

	public void run(Level world, List<BlockPos> targetPositions, Direction facing, @Nullable BlockState paintedState, @Nullable CompoundTag data, Player player) {
		switch (this) {
		case Clear:
			targetPositions.forEach(p -> com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.write(world,player,p, Blocks.AIR.defaultBlockState()));
			break;
		case Fill:
			targetPositions.forEach(p -> {
				BlockState toReplace = com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.read(world,p);
				if (!isReplaceable(toReplace))
					return;
				if (com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.writeForPainting(world,player,p, paintedState))
					ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		case Flatten:
			FlattenTool.apply(world, targetPositions, facing,player);
			break;
		case Overlay:
			targetPositions.forEach(p -> {
				BlockState toOverlay = com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.read(world,p);
				if (isReplaceable(toOverlay))
					return;
				if (toOverlay == paintedState)
					return;

				p = p.above();

				BlockState toReplace = com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.read(world,p);
				if (!isReplaceable(toReplace))
					return;
				if (com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.writeForPainting(world,player,p, paintedState))
					ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		case Place:
			targetPositions.forEach(p -> {
				if (com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.writeForPainting(world,player,p, paintedState))
					ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		case Replace:
			targetPositions.forEach(p -> {
				BlockState toReplace = com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.read(world,p);
				if (isReplaceable(toReplace))
					return;
				if (com.simibubi.create.content.equipment.zapper.ZapperWorldAccess.writeForPainting(world,player,p, paintedState))
					ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		}
	}

	public static boolean isReplaceable(BlockState toReplace) {
		return toReplace.canBeReplaced();
	}
}
