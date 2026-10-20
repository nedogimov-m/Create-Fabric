package com.simibubi.create.content.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import com.simibubi.create.content.trains.schedule.ScheduleSerialization;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RedstoneLinkCondition extends ScheduleWaitCondition {

	public Couple<Frequency> freq;

	public RedstoneLinkCondition() {
		freq = Couple.create(() -> Frequency.EMPTY);
	}

	@Override
	public int slotsTargeted() {
		return 2;
	}

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(new ItemStack(AllBlocks.REDSTONE_LINK),
			lowActivation() ? ScheduleText.translate("schedule.condition.redstone_link_off")
				: ScheduleText.translate("schedule.condition.redstone_link_on"));
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleText.translate(slot == 0 ? "logistics.firstFrequency" : "logistics.secondFrequency")
			.withStyle(ChatFormatting.RED));
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			ScheduleText.translate("schedule.condition.redstone_link.frequency_" + (lowActivation() ? "unpowered" : "powered")),
			Component.literal(" #1 ").withStyle(ChatFormatting.GRAY)
				.append(freq.getFirst()
					.getStack()
					.getHoverName()
					.copy()
					.withStyle(ChatFormatting.DARK_AQUA)),
			Component.literal(" #2 ").withStyle(ChatFormatting.GRAY)
				.append(freq.getSecond()
					.getStack()
					.getHoverName()
					.copy()
					.withStyle(ChatFormatting.DARK_AQUA)));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		int lastChecked = context.contains("LastChecked") ? context.getIntOr("LastChecked", 0) : -1;
		int status = Create.REDSTONE_LINK_NETWORK_HANDLER.globalPowerVersion.get();
		if (status == lastChecked)
			return false;
		context.putInt("LastChecked", status);
		return Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(level.getServer(), freq) != lowActivation();
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		freq.set(slot == 0, Frequency.of(stack));
		super.setItem(slot, stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return freq.get(slot == 0)
			.getStack();
	}

	@Override
	public Identifier getId() {
		return Create.asResource("redstone_link");
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		var list = new ListTag();
        for (Frequency frequency : freq) list.add(ScheduleSerialization.writeItem(registries, frequency.getStack()));
        tag.put("Frequency", list);
	}

	public boolean lowActivation() {
		return intData("Inverted") == 1;
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		if (tag.contains("Frequency"))
			{
            ListTag list = tag.getListOrEmpty("Frequency");
            if (list.size() != 2) throw new IllegalArgumentException("Schedule link requires two real frequencies");
            freq = Couple.create(Frequency.of(ScheduleSerialization.readItem(registries, list.getCompoundOrEmpty(0))),
                    Frequency.of(ScheduleSerialization.readItem(registries, list.getCompoundOrEmpty(1))));
        }
	}

	

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		return ScheduleText.translate("schedule.condition.redstone_link.status");
	}

}
