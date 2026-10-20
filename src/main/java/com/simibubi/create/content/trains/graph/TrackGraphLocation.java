package com.simibubi.create.content.trains.graph;

import net.createmod.catnip.data.Couple;

/** Genuine graph edge coordinate, used by track targeting and future carriage assembly. */
public final class TrackGraphLocation {
  public TrackGraph graph;
  public Couple<TrackNodeLocation> edge;
  public double position;
}
