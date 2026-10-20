package com.simibubi.create.content.trains.station;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

public interface StationMapData {
    boolean toggleStation(LevelAccessor level,BlockPos pos,StationBlockEntity station);
    void addStationMarker(StationMarker marker);
    List<StationMarker> create$stationMarkers();
}
