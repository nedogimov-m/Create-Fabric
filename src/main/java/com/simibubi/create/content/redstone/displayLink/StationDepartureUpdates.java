package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Source status-change notification, resolved through actual native Train/station custody only. */
public final class StationDepartureUpdates {
    private StationDepartureUpdates() {}
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var train : TrainManager.get(server).trains().values()) {
                if (train.isRestoring() || !train.runtime.displayLinkUpdateRequested) continue;
                var point = train.getCurrentStation();
                if (point == null) continue;
                var level = server.getLevel(point.getBlockEntityDimension());
                if (level == null || !(LoadedLevelAccess.blockEntity(level, point.getBlockEntityPos())
                        instanceof StationBlockEntity station) || !point.ownedBy(station)
                        || station.getStation() != point || point.getPresentTrain() != train) continue;
                DisplayLinkBlock.notifyGatherers(level, station.getBlockPos());
                train.runtime.displayLinkUpdateRequested = false;
            }
        });
    }
}
