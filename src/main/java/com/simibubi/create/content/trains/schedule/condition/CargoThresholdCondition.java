package com.simibubi.create.content.trains.schedule.condition;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class CargoThresholdCondition extends LazyTickedScheduleCondition {
	public static enum Ops {
		GREATER(">"), LESS("<"), EQUAL("=");

		public String formatted;

		private Ops(String formatted) {
			this.formatted = formatted;
		}

		public boolean test(long current, long target) {
			return switch (this) {
			case GREATER -> current > target;
			case EQUAL -> current == target;
			case LESS -> current < target;
			default -> throw new IllegalArgumentException("Unexpected value: " + this);
			};
		}

		public static List<? extends Component> translatedOptions() {
			return Arrays.stream(values())
				.map(op -> ScheduleText.translate("schedule.condition.threshold." + op.name().toLowerCase(java.util.Locale.ROOT)))
				.toList();
		}
	}

	public CargoThresholdCondition() {
		super(20);
		data.putString("Threshold", "10");
	}

	@Override
	public boolean lazyTickCompletion(Level level, Train train, CompoundTag context) {
		int lastChecked = context.contains("LastChecked") ? context.getIntOr("LastChecked", 0) : -1;
		int status = 0;
		for (Carriage carriage : train.carriages)
			status += carriage.storage.getVersion();
		if (status == lastChecked)
			return false;
		context.putInt("LastChecked", status);
		return test(level, train, context);
	}

	protected void requestStatusToUpdate(int amount, CompoundTag context) {
		context.putInt("CurrentDisplay", amount);
		super.requestStatusToUpdate(context);
	};

	protected int getLastDisplaySnapshot(CompoundTag context) {
		if (!context.contains("CurrentDisplay"))
			return -1;
		return context.getIntOr("CurrentDisplay", 0);
	}

	protected abstract boolean test(Level level, Train train, CompoundTag context);

	protected abstract Component getUnit();

	protected abstract ItemStack getIcon();

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(getIcon(), Component.literal(getOperator().formatted + " " + getThreshold()).append(getUnit()));
	}

	@Override
	public int slotsTargeted() {
		return 1;
	}

	public Ops getOperator() {
		return enumData("Operator", Ops.class);
	}

	public int getThreshold() {
		try {
			return Integer.valueOf(textData("Threshold"));
		} catch (NumberFormatException e) {
			data.putString("Threshold", "0");
		}
		return 0;
	}

	public int getMeasure() {
		return intData("Measure");
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleText.translate("schedule.condition.threshold.place_item"),
			ScheduleText.translate("schedule.condition.threshold.place_item_2")
				.withStyle(ChatFormatting.GRAY),
			ScheduleText.translate("schedule.condition.threshold.place_item_3")
				.withStyle(ChatFormatting.GRAY));
	}

	

}
