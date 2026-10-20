package com.simibubi.create.foundation.blockEntity.behaviour.scrollValue;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.function.IntSupplier;

public class ScrollValueBehaviour extends BlockEntityBehaviour implements ValueSettingsBehaviour {

	public static final BehaviourType<ScrollValueBehaviour> TYPE = new BehaviourType<>();

	ValueBoxTransform slotPositioning;
	Vec3 textShift;

	int min = 0;
	protected int max = 1;
	public int value;
	public Component label;
	Consumer<Integer> callback;
	Consumer<Integer> clientCallback;
	Function<Integer, String> formatter;
	private Supplier<Boolean> isActive;
    private IntSupplier dynamicMin, dynamicMax;
	boolean needsWrench;

	public ScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
		super(be);
		this.setLabel(label);
		slotPositioning = slot;
		callback = i -> {
		};
		clientCallback = i -> {
		};
		formatter = i -> Integer.toString(i);
		value = 0;
		isActive = () -> true;
	}

	@Override
	public boolean isSafeNBT() {
		return true;
	}

    @Override public void write(ValueOutput output,boolean clientPacket) {
        output.putInt("ScrollValue",value);
        super.write(output,clientPacket);
    }
    @Override public void read(ValueInput input,boolean clientPacket) {
        value=Mth.clamp(input.getIntOr("ScrollValue",value),minimum(),maximum());
        super.read(input,clientPacket);
    }
    /** Configuration bounds are read when used; the one actual setting remains public value. */
    public ScrollValueBehaviour withBounds(IntSupplier minimum,IntSupplier maximum) {
        dynamicMin=minimum;dynamicMax=maximum;return this;
    }
    public int minimum() { return dynamicMin==null?min:dynamicMin.getAsInt(); }
    public int maximum() { return dynamicMax==null?max:dynamicMax.getAsInt(); }

	public ScrollValueBehaviour withClientCallback(Consumer<Integer> valueCallback) {
		clientCallback = valueCallback;
		return this;
	}

	public ScrollValueBehaviour withCallback(Consumer<Integer> valueCallback) {
		callback = valueCallback;
		return this;
	}

	public ScrollValueBehaviour between(int min, int max) {
        if(min>max)throw new IllegalArgumentException("Reversed scroll range");
        dynamicMin=dynamicMax=null;
		this.min = min;
		this.max = max;
		return this;
	}

	public ScrollValueBehaviour requiresWrench() {
		this.needsWrench = true;
		return this;
	}

	public ScrollValueBehaviour withFormatter(Function<Integer, String> formatter) {
		this.formatter = formatter;
		return this;
	}

	public ScrollValueBehaviour onlyActiveWhen(Supplier<Boolean> condition) {
		isActive = condition;
		return this;
	}

	public void setValue(int value) {
		if (!isCurrentOwner())
			return;
		value = Mth.clamp(value, minimum(), maximum());
		if (value == this.value)
			return;
		this.value = value;
		callback.accept(value);
		if (!isCurrentOwner())
			return;
		blockEntity.setChanged();
		blockEntity.sendData();
	}

    /** Detached construction and virtual scenes are valid; removed world owners cannot publish. */
    protected boolean isCurrentOwner() {
        return !blockEntity.isRemoved() && (!blockEntity.hasLevel()
            || blockEntity.getBehaviour(getType()) == this && (blockEntity.isVirtual()
                || com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(getWorld(),getPos()) == blockEntity));
    }

	public int getValue() {
		return value;
	}

	public String formatValue() {
		return formatter.apply(value);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean isActive() {
		return isActive.get();
	}

	@Override
	public boolean testHit(Vec3 hit) {
		BlockState state = blockEntity.getBlockState();
		Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
		return slotPositioning.testHit(getWorld(), getPos(), state, localHit);
	}

	public void setLabel(Component label) {
		this.label = label;
	}

	public static class StepContext {
		public int currentValue;
		public boolean forward;
		public boolean shift;
		public boolean control;
	}

	@Override
	public ValueBoxTransform getSlotPositioning() {
		return slotPositioning;
	}

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		return new ValueSettingsBoard(label, maximum(), 10, ImmutableList.of(Component.literal("Value")),
			new ValueSettingsFormatter(ValueSettings::format));
	}

	@Override
	public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
		if (valueSetting.equals(getValueSettings()))
			return;
		setValue(valueSetting.value());
		playFeedbackSound(this);
	}

	@Override
	public ValueSettings getValueSettings() {
		return new ValueSettings(0, value);
	}

    @Override public void newSettingHovered(ValueSettings setting) { clientCallback.accept(setting.value()); }

	@Override
	public boolean onlyVisibleWithWrench() {
		return needsWrench;
	}

	@Override
	public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
		if (player instanceof FakePlayer)
			blockEntity.getBlockState()
				.useItemOn(player.getItemInHand(hand), getWorld(), player, hand, hitResult);
	}

}
