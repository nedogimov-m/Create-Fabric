package com.simibubi.create.content.equipment.zapper.terrainzapper;

import java.util.Collection;



import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

public abstract class Brush {

	protected int param0;
	protected int param1;
	protected int param2;
	public final int amtParams;

	public Brush(int amtParams) {
		this.amtParams = amtParams;
	}

	public void set(int param0, int param1, int param2) {
		this.param0 = param0;
		this.param1 = param1;
		this.param2 = param2;
	}

	public TerrainTools[] getSupportedTools() {
		return TerrainTools.values();
	}

	public TerrainTools redirectTool(TerrainTools tool) {
		return tool;
	}

	public boolean hasPlacementOptions() {
		return true;
	}

	public boolean hasConnectivityOptions() {
		return false;
	}

	public int getMax(int paramIndex) {
		return Integer.MAX_VALUE;
	}

	public int getMin(int paramIndex) {
		return 0;
	}

	public Component getParamLabel(int paramIndex) {
		return Component.translatable("create."+(paramIndex == 0 ? "generic.width" : paramIndex == 1 ? "generic.height" : "generic.length"));
	}

	public int get(int paramIndex) {
		return paramIndex == 0 ? param0 : paramIndex == 1 ? param1 : param2;
	}

	public BlockPos getOffset(Vec3 ray, Direction face, PlacementOptions option) {
		return BlockPos.ZERO;
	}

	public abstract Collection<BlockPos> addToGlobalPositions(LevelAccessor world, BlockPos targetPos, Direction targetFace,
		Collection<BlockPos> affectedPositions, TerrainTools usedTool);

}
