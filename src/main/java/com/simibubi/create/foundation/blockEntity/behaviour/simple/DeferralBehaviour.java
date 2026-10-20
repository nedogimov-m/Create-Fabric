package com.simibubi.create.foundation.blockEntity.behaviour.simple;

import java.util.function.Supplier;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Genuine source deferred callback and pending state on native value serialization. */
public class DeferralBehaviour extends BlockEntityBehaviour {
	public static final BehaviourType<DeferralBehaviour> TYPE = new BehaviourType<>();
	private boolean needsUpdate;
	private final Supplier<Boolean> callback;

	public DeferralBehaviour(SmartBlockEntity owner, Supplier<Boolean> callback) {
		super(owner);
		this.callback = callback;
	}

	@Override
	public boolean isSafeNBT() {
		return true;
	}

	@Override
	public void write(ValueOutput output, boolean clientPacket) {
		output.putBoolean("NeedsUpdate", needsUpdate);
		super.write(output, clientPacket);
	}

	@Override
	public void read(ValueInput input, boolean clientPacket) {
		needsUpdate = input.getBooleanOr("NeedsUpdate", false);
		super.read(input, clientPacket);
	}

	@Override
	public void tick() {
		super.tick();
		if (needsUpdate && callback.get())
			needsUpdate = false;
	}

	public void scheduleUpdate() {
		needsUpdate = true;
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}
}
