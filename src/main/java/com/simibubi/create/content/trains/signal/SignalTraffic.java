package com.simibubi.create.content.trains.signal;

import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import java.util.*;

/** Native current-frame occupancy and real chain dependencies; unresolved topology is never free. */
public final class SignalTraffic {
  private record Side(SignalBoundary boundary,boolean primary){}
  public static SignalState evaluate(TrackGraph graph,SignalBoundary boundary,boolean primary,TrainManager traffic){
    if(graph.manager()==null||graph.manager().server()!=traffic.server()||!traffic.isReady())return SignalState.INVALID;
    var root=new Side(boundary,primary);
    var states=new LinkedHashMap<Side,SignalState>();
    var dependencies=new LinkedHashMap<Side,List<Side>>();
    var seen=new HashSet<Side>();var pending=new ArrayDeque<Side>();pending.add(root);
    while(!pending.isEmpty()){
      var side=pending.removeFirst();if(!seen.add(side))continue;
      var signal=side.boundary;
      var groupId=signal.group(side.primary);
      var group=traffic.signalEdgeGroups().get(groupId);
      if(signal.graph()!=graph||groupId==null||Objects.equals(groupId,signal.group(!side.primary))||group==null||!group.hasResolvedTopology()){
        states.put(side,SignalState.INVALID);continue;
      }
      if(signal.isForcedRed(side.primary)||group.isOccupiedUnless(signal)){states.put(side,SignalState.RED);continue;}
      if(signal.type(side.primary)!=SignalType.CROSS_SIGNAL){states.put(side,SignalState.GREEN);continue;}
      var paths=SignalChain.collect(graph,signal,side.primary);
      if(!paths.complete()){states.put(side,SignalState.INVALID);continue;}
      var reached=new ArrayList<Side>();
      boolean complete=true;
      for(var entry:paths.signals().entrySet()){
        var next=graph.getPoint(SignalEdgePoints.SIGNAL,entry.getKey());
        if(next==null){complete=false;break;}
        if(next.owners(entry.getValue()).isEmpty())continue;
        var dependency=new Side(next,entry.getValue());reached.add(dependency);pending.addLast(dependency);
      }
      if(!complete)states.put(side,SignalState.INVALID);
      else if(reached.isEmpty())states.put(side,SignalState.GREEN);
      else dependencies.put(side,List.copyOf(reached));
    }
    boolean changed;
    do{
      changed=false;
      for(var entry:dependencies.entrySet()){
        if(states.containsKey(entry.getKey()))continue;
        boolean complete=true,allFree=true,allRed=true,invalid=false;
        for(var dependency:entry.getValue()){
          var state=states.get(dependency);
          if(state==null){complete=false;continue;}
          invalid|=state==SignalState.INVALID;allFree&=state==SignalState.GREEN;allRed&=state==SignalState.RED;
        }
        if(invalid||complete){states.put(entry.getKey(),invalid?SignalState.INVALID:allFree?SignalState.GREEN:allRed?SignalState.RED:SignalState.YELLOW);changed=true;}
      }
    }while(changed);
    return states.getOrDefault(root,SignalState.INVALID);
  }
  private SignalTraffic(){}
}
