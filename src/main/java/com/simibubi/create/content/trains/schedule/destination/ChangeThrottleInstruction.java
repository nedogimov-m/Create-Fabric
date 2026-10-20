package com.simibubi.create.content.trains.schedule.destination;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ChangeThrottleInstruction extends ScheduleInstruction {

	public ChangeThrottleInstruction() {
		super();
		data.putInt("Value", 100);
	}

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(icon(), formatted());
	}

	private MutableComponent formatted() {
		return Component.literal(intData("Value") + "%");
	}

	@Override
	public Identifier getId() {
		return Create.asResource("throttle");
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return icon();
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleText
			.translate("schedule." + type + "." + getId().getPath() + ".summary",
				formatted().withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GOLD));
	}

	

	public float getThrottle() {
		return intData("Value") / 100f;
	}

	private ItemStack icon() {
		return new ItemStack(AllBlocks.TRAIN_CONTROLS);
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleText.translate("schedule.instruction.throttle_edit_box"),
			ScheduleText.translate("schedule.instruction.throttle_edit_box_1")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		runtime.train.throttle = getThrottle();
		runtime.state = State.PRE_TRANSIT;
		runtime.currentEntry++;
		return null;
	}

}
