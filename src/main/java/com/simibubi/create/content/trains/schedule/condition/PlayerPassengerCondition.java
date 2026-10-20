package com.simibubi.create.content.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PlayerPassengerCondition extends ScheduleWaitCondition {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		int target = getTarget();
		return Pair.of(new ItemStack(AllBlocks.SEATS.get(DyeColor.YELLOW)),
			ScheduleText.translate("schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"), target));
	}

	@Override
	public Identifier getId() {
		return Create.asResource("player_count");
	}

	public int getTarget() {
		return intData("Count");
	}

	public boolean canOvershoot() {
		return intData("Exact") != 0;
	}

	@Override
	public List<Component> getTitleAs(String type) {
		int target = getTarget();
		return ImmutableList.of(ScheduleText.translate("schedule.condition.player_count.seated",
			ScheduleText.translate("schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"),
				Component.literal("" + target).withStyle(ChatFormatting.DARK_AQUA))));
	}

	

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		int prev = context.getIntOr("PrevPlayerCount", 0);
		int present = train.countPlayerPassengers();
		int target = getTarget();
		context.putInt("PrevPlayerCount", present);
		if (prev != present)
			requestStatusToUpdate(context);
		return canOvershoot() ? present >= target : present == target;
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		return ScheduleText.translate("schedule.condition.player_count.status", train.countPlayerPassengers(), getTarget());
	}

}
