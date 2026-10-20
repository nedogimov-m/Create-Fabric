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
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.content.trains.schedule.ScheduleSerialization;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;


public class FluidThresholdCondition extends CargoThresholdCondition {

	private FilterItemStack compareStack = FilterItemStack.empty();

	@Override
	protected Component getUnit() {
		return Component.literal("b");
	}

	@Override
	protected ItemStack getIcon() {
		return compareStack.item();
	}

	@Override
	protected boolean test(Level level, Train train, CompoundTag context) {
		Ops operator = getOperator();
		int target = getThreshold();

        long foundFluid = 0;
        for (Carriage carriage : train.carriages) {
            for (var view : carriage.storage.getFluids()) {
                if (view.isResourceBlank() || view.getAmount() == 0) continue;
                var fluidInTank = new FluidStack(view.getResource(), view.getAmount());
                if (!compareStack.test(level, fluidInTank)) continue;
                foundFluid = Math.addExact(foundFluid, view.getAmount());
            }
        }

		requestStatusToUpdate((int) Math.min(Integer.MAX_VALUE, foundFluid / FluidConstants.BUCKET), context);
		return operator.test(foundFluid, (long) target * FluidConstants.BUCKET);
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.writeAdditional(registries, tag);
		tag.put("Bucket", ScheduleSerialization.writeItem(registries, compareStack.item()));
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.readAdditional(registries, tag);
		if (tag.contains("Bucket"))
			compareStack = FilterItemStack.of(ScheduleSerialization.readItem(registries, tag.getCompoundOrEmpty("Bucket")));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		return super.tickCompletion(level, train, context);
	}

	

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			ScheduleText.translate("schedule.condition.threshold.train_holds",
				ScheduleText.translate("schedule.condition.threshold." + getOperator().name().toLowerCase(java.util.Locale.ROOT))),
			ScheduleText.translate("schedule.condition.threshold.x_units_of_item", getThreshold(),
				ScheduleText.translate("schedule.condition.threshold.buckets"),
				compareStack.isEmpty() ? ScheduleText.translate("schedule.condition.threshold.anything")
					: compareStack.isFilterItem()
						? ScheduleText.translate("schedule.condition.threshold.matching_content")
						: FluidVariantAttributes.getName(compareStack.fluid(null).variant()))
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		compareStack = FilterItemStack.of(stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return compareStack.item();
	}

	@Override
	public Identifier getId() {
		return Create.asResource("fluid_threshold");
	}

	

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		int lastDisplaySnapshot = getLastDisplaySnapshot(tag);
		if (lastDisplaySnapshot == -1)
            return Component.empty();
		int offset = getOperator() == Ops.LESS ? -1 : getOperator() == Ops.GREATER ? 1 : 0;
		return ScheduleText.translate("schedule.condition.threshold.status", lastDisplaySnapshot,
			Math.max(0, getThreshold() + offset), ScheduleText.translate("schedule.condition.threshold.buckets"));
	}

}
