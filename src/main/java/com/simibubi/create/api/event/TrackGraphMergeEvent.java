package com.simibubi.create.api.event;

import com.simibubi.create.content.trains.graph.TrackGraph;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fired by real track propagation before the source graph transfers its nodes and points. */
public class TrackGraphMergeEvent {
  @FunctionalInterface
  public interface Listener {
    void onMerge(TrackGraphMergeEvent event);
  }

  public static final Event<Listener> EVENT =
      EventFactory.createArrayBacked(
          Listener.class,
          listeners -> event -> {
            for (var listener : listeners) listener.onMerge(event);
          });

  private final TrackGraph mergedInto;
  private final TrackGraph mergedFrom;

  public TrackGraphMergeEvent(TrackGraph from, TrackGraph into) {
    mergedInto = into;
    mergedFrom = from;
  }

  public TrackGraph getGraphMergedInto() {
    return mergedInto;
  }

  public TrackGraph getGraphMergedFrom() {
    return mergedFrom;
  }
}
