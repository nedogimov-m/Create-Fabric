package com.simibubi.create.content.trains.graph;

import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.List;
import net.createmod.catnip.data.Couple;

/** Actual discovered route, cost, direction and genuine global station. */
public final class DiscoveredPath {
    public List<Couple<TrackNode>> path;
    public GlobalStation destination;
    public double distance,cost;
    public DiscoveredPath(double distance,double cost,List<Couple<TrackNode>> path,GlobalStation destination){if(!Double.isFinite(distance)||!Double.isFinite(cost))throw new IllegalArgumentException("Nonfinite discovered train route");this.distance=distance;this.cost=cost;this.path=new java.util.ArrayList<>(path);this.destination=java.util.Objects.requireNonNull(destination);}
}
