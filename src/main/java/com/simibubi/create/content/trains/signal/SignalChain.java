package com.simibubi.create.content.trains.signal;

import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.createmod.catnip.data.Couple;

/** Genuine source directional rail walk to the next physical boundaries on every reachable branch. */
public final class SignalChain {
  public record Paths(Map<UUID,Boolean> signals,boolean complete){public Paths{signals=Map.copyOf(signals);}}
  private record Step(TrackNode current,TrackNode previous){}
  public static Paths collect(TrackGraph graph,SignalBoundary signal,boolean front){
    var result=new LinkedHashMap<UUID,Boolean>();
    if(signal.graph()!=graph)return new Paths(result,false);
    var first=graph.locateNode(signal.edgeLocation.get(front));
    var second=graph.locateNode(signal.edgeLocation.get(!front));
    if(first==null||second==null)return new Paths(result,false);
    var start=graph.getConnectionsFrom(first).get(second);
    if(start==null)return new Paths(result,false);
    var immediate=start.getEdgeData().next(SignalEdgePoints.SIGNAL,signal.getLocationOn(start));
    if(immediate!=null){result.put(immediate.id,!immediate.isPrimary(first));return new Paths(result,true);}
    var visited=Collections.newSetFromMap(new IdentityHashMap<TrackEdge,Boolean>());
    var frontier=new ArrayDeque<Step>();frontier.add(new Step(second,first));
    boolean complete=true;
    while(!frontier.isEmpty()){
      var step=frontier.removeFirst();
      var incoming=graph.getConnectionsFrom(step.previous).get(step.current);
      if(incoming==null){complete=false;continue;}
      for(var entry:graph.getConnectionsFrom(step.current).entrySet()){
        var next=entry.getKey();var edge=entry.getValue();
        if(next==step.previous||!incoming.canTravelTo(edge)||!visited.add(edge))continue;
        var opposite=graph.getConnectionsFrom(next).get(step.current);
        if(opposite==null){complete=false;continue;}
        visited.add(opposite);
        var boundary=edge.getEdgeData().next(SignalEdgePoints.SIGNAL,0);
        if(boundary==null)boundary=opposite.getEdgeData().next(SignalEdgePoints.SIGNAL,0);
        if(boundary!=null){result.put(boundary.id,!boundary.isPrimary(step.current));continue;}
        frontier.addLast(new Step(next,step.current));
      }
    }
    return new Paths(result,complete);
  }
  private SignalChain(){}
}
