package com.simibubi.create.content.kinetics.simpleRelays;

import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BracketedKineticBlockEntity extends SimpleKineticBlockEntity implements TransformableBlockEntity {

	public BracketedKineticBlockEntity(BlockPos pos, BlockState state) {
		this(com.simibubi.create.AllBlockEntityTypes.BRACKETED_KINETIC, pos, state);
	}

	public BracketedKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void transform(BlockEntity be, StructureTransform transform) {
		if (be != this)
			throw new IllegalArgumentException("Bracket transform must use its actual block entity owner");
		// KineticBlockEntity publishes the existing bracket owner; never create another store.
		BracketedBlockEntityBehaviour bracketBehaviour = getBehaviour(BracketedBlockEntityBehaviour.TYPE);
		if (bracketBehaviour != null) {
			bracketBehaviour.transformBracket(transform);
		}
	}

}
