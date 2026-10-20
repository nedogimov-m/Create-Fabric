package com.simibubi.create.content.trains.schedule.destination;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.createmod.catnip.data.Glob;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DestinationInstruction extends TextScheduleInstruction {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(new ItemStack(AllBlocks.TRACK_STATION), Component.literal(getLabelText()));
	}

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	public Identifier getId() {
		return Create.asResource("destination");
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return new ItemStack(AllBlocks.TRACK_STATION);
	}

	public String getFilter() {
		return getLabelText();
	}

	public String getFilterForRegex() {
		return Glob.toRegexPattern(getFilter(), "");
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleText.translate("schedule.instruction.filter_edit_box"),
			ScheduleText.translate("schedule.instruction.filter_edit_box_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleText.translate("schedule.instruction.filter_edit_box_2")
				.withStyle(ChatFormatting.DARK_GRAY),
			ScheduleText.translate("schedule.instruction.filter_edit_box_3")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	

	@Override
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		String regex = getFilterForRegex();
		boolean anyMatch = false;
		ArrayList<GlobalStation> validStations = new ArrayList<>();
		Train train = runtime.train;

		if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
			train.status.missingConductor();
			runtime.startCooldown();
			return null;
		}


		for (GlobalStation globalStation : train.graph.getPoints(com.simibubi.create.content.trains.station.StationEdgePoints.STATION)) {
			if (!globalStation.name().matches(regex))
				continue;
			anyMatch = true;
			validStations.add(globalStation);
		}

		DiscoveredPath best = train.navigation.findPathTo(validStations, Double.MAX_VALUE);
		if (best == null) {
			if (anyMatch)
				train.status.failedNavigation();
			else
				train.status.failedNavigationNoTarget(getFilter());
			runtime.startCooldown();
			return null;
		}

		return best;
	}

}
