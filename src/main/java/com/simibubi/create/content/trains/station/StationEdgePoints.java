package com.simibubi.create.content.trains.station;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;

/** Registers the real complete station codec before native graph SavedData loads. */
public final class StationEdgePoints {
  public static final EdgePointType<GlobalStation> STATION =
      EdgePointType.register(Create.asResource("station"), GlobalStation::new, GlobalStation.CODEC);

  public static void register() {}

  private StationEdgePoints() {}
}
