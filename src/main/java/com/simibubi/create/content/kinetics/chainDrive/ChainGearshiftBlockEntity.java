package com.simibubi.create.content.kinetics.chainDrive;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ChainGearshiftBlockEntity extends KineticBlockEntity {

	int signal;
	boolean signalChanged;

	public ChainGearshiftBlockEntity(BlockPos pos, BlockState state) {
		this(com.simibubi.create.AllBlockEntityTypes.ADJUSTABLE_CHAIN_GEARSHIFT, pos, state);
	}

	public ChainGearshiftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		signal = 0;
		setLazyTickRate(40);
	}

	@Override
	public void write(ValueOutput output, boolean clientPacket) {
		output.putInt("Signal", signal);
		super.write(output, clientPacket);
	}

	@Override
	protected void read(ValueInput input, boolean clientPacket) {
		signal = Math.clamp(input.getIntOr("Signal", 0), 0, 15);
		super.read(input, clientPacket);
	}

	public float getModifier() {
		return getModifierForSignal(signal);
	}

	public void neighbourChanged() {
		if (!hasLevel())
			return;
		int power = com.simibubi.create.foundation.utility.LoadedLevelAccess.getBestNeighborSignal(level, worldPosition);
		if (power != signal)
			signalChanged = true;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		neighbourChanged();
	}

	@Override
	public void tick() {
		super.tick();
		applyPendingSignal();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ChainGearshiftBlockEntity owner) {
		try (var scope = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, owner)) {
			if (!scope.shouldTick())
				return;
			KineticBlockEntity.tick(level, pos, state, owner);
			owner.applyPendingSignal();
		}
	}

	private void applyPendingSignal() {
		if (level == null || level.isClientSide())
			return;
		if (signalChanged) {
			signalChanged = false;
			analogSignalChanged(com.simibubi.create.foundation.utility.LoadedLevelAccess.getBestNeighborSignal(level, worldPosition));
		}
	}

	protected void analogSignalChanged(int newSignal) {
		detachKinetics();
		removeSource();
		signal = Math.clamp(newSignal, 0, 15);
		attachKinetics();
		setChanged();
		sendData();
	}

	protected float getModifierForSignal(int newPower) {
		if (newPower == 0)
			return 1;
		return 1 + ((newPower + 1) / 16f);
	}

}
