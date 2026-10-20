package com.simibubi.create.content.trains.observer;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
public final class ObserverEdgePoints {
  public static final EdgePointType<TrackObserver> OBSERVER=EdgePointType.register(Create.asResource("observer"),TrackObserver::new,TrackObserver.CODEC);
  public static void register(){}
  private ObserverEdgePoints(){}
}
