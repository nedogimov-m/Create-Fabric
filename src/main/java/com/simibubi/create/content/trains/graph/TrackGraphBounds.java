package com.simibubi.create.content.trains.graph;

import com.simibubi.create.content.trains.track.BezierConnection;
import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Real per-dimension graph bounds, including curve excursions beyond their endpoint box. */
public final class TrackGraphBounds {
  public AABB box;
  public final List<BezierConnection> beziers;

  public TrackGraphBounds(TrackGraph graph, ResourceKey<Level> dimension) {
    var curves = new ArrayList<BezierConnection>();
    for (var location : graph.getNodes()) {
      if (!Objects.equals(location.dimension, dimension)) continue;
      var node = graph.locateNode(location);
      var point = node.location.getLocation();
      var cell = new AABB(point, point);
      box = box == null ? cell : box.minmax(cell);
      for (var edge : graph.getConnectionsFrom(node).values())
        if (edge.isTurn() && edge.getTurn().isPrimary()) {
          curves.add(edge.getTurn());
          box = box.minmax(edge.getTurn().getBounds());
        }
    }
    if (box != null) box = box.inflate(2);
    beziers = List.copyOf(curves);
  }
}
