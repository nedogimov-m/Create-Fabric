package com.simibubi.create.content.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import com.simibubi.create.content.trains.schedule.ScheduleSerialization;

public class ItemThresholdCondition extends CargoThresholdCondition {

	private FilterItemStack stack = FilterItemStack.empty();

	@Override
	protected Component getUnit() {
		return Component.literal(inStacks() ? "\u25A4" : "");
	}

	@Override
	protected ItemStack getIcon() {
		return stack.item();
	}

	@Override
	protected boolean test(Level level, Train train, CompoundTag context) {
		Ops operator = getOperator();
		int target = getThreshold();
		boolean stacks = inStacks();

        long foundItems = 0;
        for (Carriage carriage : train.carriages) {
            Storage<ItemVariant> items = carriage.storage.getAllItems();
            for (var view : items) {
                if (view.isResourceBlank() || view.getAmount() == 0) continue;
                ItemStack stackInSlot = view.getResource().toStack();
                if (!stack.test(level, stackInSlot)) continue;
                if (stacks) foundItems += view.getAmount() == stackInSlot.getMaxStackSize() ? 1 : 0;
                else foundItems = Math.addExact(foundItems, view.getAmount());
            }
        }

		requestStatusToUpdate((int) Math.min(Integer.MAX_VALUE, foundItems), context);
		return operator.test(foundItems, target);
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.writeAdditional(registries, tag);
		tag.put("Item", ScheduleSerialization.writeItem(registries, stack.item()));
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.readAdditional(registries, tag);
		if (tag.contains("Item"))
			stack = FilterItemStack.of(ScheduleSerialization.readItem(registries, tag.getCompoundOrEmpty("Item")));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		return super.tickCompletion(level, train, context);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.stack = FilterItemStack.of(stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return stack.item();
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			ScheduleText.translate("schedule.condition.threshold.train_holds",
				ScheduleText.translate("schedule.condition.threshold." + getOperator().name().toLowerCase(java.util.Locale.ROOT))),
			ScheduleText.translate("schedule.condition.threshold.x_units_of_item", getThreshold(),
				ScheduleText.translate("schedule.condition.threshold." + (inStacks() ? "stacks" : "items")),
				stack.isEmpty() ? ScheduleText.translate("schedule.condition.threshold.anything")
					: stack.isFilterItem()
						? ScheduleText.translate("schedule.condition.threshold.matching_content")
						: stack.item().getHoverName())
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	private boolean inStacks() {
		return intData("Measure") == 1;
	}

	@Override
	public Identifier getId() {
		return Create.asResource("item_threshold");
	}

	

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		int lastDisplaySnapshot = getLastDisplaySnapshot(tag);
		if (lastDisplaySnapshot == -1)
            return Component.empty();
		int offset = getOperator() == Ops.LESS ? -1 : getOperator() == Ops.GREATER ? 1 : 0;
		return ScheduleText.translate("schedule.condition.threshold.status", lastDisplaySnapshot,
			Math.max(0, getThreshold() + offset),
			ScheduleText.translate("schedule.condition.threshold." + (inStacks() ? "stacks" : "items")));
	}
}
