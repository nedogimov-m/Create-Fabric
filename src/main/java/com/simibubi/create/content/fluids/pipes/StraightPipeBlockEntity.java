package com.simibubi.create.content.fluids.pipes;

import java.util.List;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.PrerequisiteAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/** Genuine straight-pipe owner over the existing native pressure/flow and advancement authorities. */
public class StraightPipeBlockEntity extends SmartBlockEntity
		implements FluidTransportBehaviour.Provider, RenderDataBlockEntity, TransformableBlockEntity {

	public final PrerequisiteAdvancements.Owner sourceAdvancements =
		new PrerequisiteAdvancements.Owner(this, "water_supply", "cross_streams", "honey_drain");
	// Assigned by the source constructor hook, before subclass field initialization.
	private BracketedBlockEntityBehaviour bracket;
	private BlockState previousState;
	private boolean needsPropagation = true;

	public StraightPipeBlockEntity(BlockPos pos, BlockState state) {
		this(AllBlockEntityTypes.STRAIGHT_PIPE, pos, state);
	}

	public StraightPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(new StraightPipeFluidTransportBehaviour(this));
		bracket = new BracketedBlockEntityBehaviour(this, BracketedBlockEntityBehaviour::eligiblePipe);
		behaviours.add(bracket);
	}

	@Override
	public FluidTransportBehaviour transport() {
		return getBehaviour(FluidTransportBehaviour.TYPE);
	}

	public BracketedBlockEntityBehaviour bracketBehaviour() {
		return getBehaviour(BracketedBlockEntityBehaviour.TYPE);
	}

	@Override
	public Object getRenderData() {
		var transport = transport();
		return transport == null ? null : transport.getRenderData();
	}

	public void updateNetwork() {
		needsPropagation = true;
	}

	@Override
	public void tick() {
		if (level == null)
			super.tick();
		else
			tick(level, worldPosition, getBlockState(), this);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, StraightPipeBlockEntity pipe) {
		try (var scope = SmartBlockEntity.enterNativeTick(level, pos, state, pipe)) {
			if (!scope.shouldTick())
				return;
			if (pipe.previousState != state) {
				pipe.needsPropagation = true;
				pipe.previousState = state;
			}
			if (!level.isClientSide() && pipe.needsPropagation) {
				pipe.needsPropagation = false;
				FluidPropagator.propagateChangedPipe(level, pos, state);
			}
		}
	}

	@Override
	protected void read(ValueInput input, boolean clientPacket) {
		super.read(input, clientPacket);
		needsPropagation = true;
	}

	@Override
	protected AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {
		return sourceAdvancements;
	}

	@Override
	public void transform(BlockEntity owner, StructureTransform transform) {
		if (owner != this)
			throw new IllegalArgumentException("Straight-pipe transform must use its actual owner");
		var behaviour = bracketBehaviour();
		if (behaviour != null)
			behaviour.transformBracket(transform);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		var behaviour = bracketBehaviour();
		if (behaviour != null)
			behaviour.dropBracket();
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	public void setBlockState(BlockState next) {
		if (getBlockState() != next && bracket != null) {
			var behaviour = bracketBehaviour();
			if (behaviour != null)
				behaviour.dropBracket();
		}
		super.setBlockState(next);
	}

	public static class StraightPipeFluidTransportBehaviour extends FluidTransportBehaviour {
		public StraightPipeFluidTransportBehaviour(SmartBlockEntity owner) {
			super(owner);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return state.hasProperty(AxisPipeBlock.AXIS) && state.getValue(AxisPipeBlock.AXIS) == direction.getAxis();
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockGetter world, BlockPos pos, BlockState state,
				Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
			BlockState otherState = FluidPropagator.stateAt(world, pos.relative(direction));
			Direction.Axis axis = IAxisPipe.getAxisOf(state);
			Direction.Axis otherAxis = IAxisPipe.getAxisOf(otherState);
			if (attachment == AttachmentTypes.RIM && state.getBlock() instanceof FluidValveBlock)
				return AttachmentTypes.NONE;
			if (attachment == AttachmentTypes.RIM && !(state.getBlock() instanceof GlassFluidPipeBlock)
					&& otherState.getBlock() instanceof GlassFluidPipeBlock)
				return AttachmentTypes.PARTIAL_RIM;
			if (attachment == AttachmentTypes.RIM && FluidPipeBlock.isPipe(otherState))
				return AttachmentTypes.NONE;
			if (axis == otherAxis && axis != null)
				return AttachmentTypes.NONE;
			if (otherState.getBlock() instanceof FluidValveBlock
					&& FluidValveBlock.getPipeAxis(otherState) == direction.getAxis())
				return AttachmentTypes.NONE;
			return attachment.withoutConnector();
		}
	}
}
