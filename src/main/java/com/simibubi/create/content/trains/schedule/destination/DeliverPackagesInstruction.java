package com.simibubi.create.content.trains.schedule.destination;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

public class DeliverPackagesInstruction extends ScheduleInstruction {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(getSecondLineIcon(), ScheduleText.translate("schedule.instruction.package_delivery"));
	}

	@Override
	public ItemStack getSecondLineIcon() {
		return new ItemStack(AllBlocks.POSTBOXES.get(DyeColor.WHITE));
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleText.translate("schedule.instruction.package_delivery.summary").withStyle(ChatFormatting.GOLD),
			ScheduleText.translate("schedule.instruction.package_delivery.summary_1")
				.withStyle(ChatFormatting.GRAY),
			ScheduleText.translate("schedule.instruction.package_delivery.summary_2")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public Identifier getId() {
		return Create.asResource("package_delivery");
	}

	@Override
	public boolean supportsConditions() {
		return true;
	}

	@Override
	@Nullable
	public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
		boolean anyMatch = false;
		String firstPackage = null;
		ArrayList<GlobalStation> validStations = new ArrayList<>();
		Train train = runtime.train;

		if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
			train.status.missingConductor();
			runtime.startCooldown();
			return null;
		}

		for (Carriage carriage : train.carriages) {
			Storage<ItemVariant> carriageInventory = carriage.storage.getAllItems();
			if (carriageInventory == null)
				continue;

			// Export to station
			for (var view : carriageInventory) {
                if (view.isResourceBlank() || view.getAmount() == 0) continue;
                ItemStack stack = view.getResource().toStack();
				if (!PackageItem.isPackage(stack))
					continue;
				if (firstPackage == null)
					firstPackage = PackageItem.getAddress(stack);
				for (GlobalStation globalStation : train.graph.getPoints(com.simibubi.create.content.trains.station.StationEdgePoints.STATION)) {
					for (var port : globalStation.ports()) {
						if (!PackageItem.matchAddress(stack, port.data().address()))
							continue;
						anyMatch = true;
						validStations.add(globalStation);
						break;
					}
				}
			}
		}

		if (validStations.isEmpty()) {
			if (firstPackage != null) {
				train.status.failedPackageNoTarget(firstPackage);
				runtime.startCooldown();
			} else {
				runtime.state = State.PRE_TRANSIT;
				runtime.currentEntry++;
			}
			return null;
		}

		DiscoveredPath best = train.navigation.findPathTo(validStations, Double.MAX_VALUE);
		if (best == null) {
			if (anyMatch)
				train.status.failedNavigation();
			runtime.startCooldown();
			return null;
		}

		return best;
	}

}
