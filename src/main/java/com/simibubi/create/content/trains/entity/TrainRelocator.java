package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Source backward wheel tracing and collision checks, applied to the same actual native Train. */
public final class TrainRelocator {
    public record Preview(boolean valid, List<Vec3> positions) {
        public Preview { positions = List.copyOf(positions); }
    }
    private record Wheel(TravellingPoint actual, TrackNode first, TrackNode second,
                         TrackEdge edge, double position, boolean upsideDown) {
        Wheel(TravellingPoint actual) {
            this(actual,actual.node1,actual.node2,actual.edge,actual.position,actual.upsideDown);
        }
        boolean unchanged() {
            return actual.node1==first&&actual.node2==second&&actual.edge==edge
                &&Double.doubleToLongBits(actual.position)==Double.doubleToLongBits(position)
                &&actual.upsideDown==upsideDown;
        }
    }
    private record Location(TrackNode first, TrackNode second, TrackEdge edge, double position) {}
    private record Plan(Train train, TrackGraph graph, long revision, List<Wheel> wheels,
                        List<Location> locations, Preview preview) {}

    public static Preview preview(Train train, Level level, BlockPos pos,
            BezierTrackPointLocation bezier, boolean direction, Vec3 look) {
        var plan=plan(train,level,pos,bezier,direction,look);
        return plan==null?new Preview(false,List.of()):
            new Preview(plan.preview.valid&&current(plan,level),plan.preview.positions);
    }

    public static boolean relocate(Train train, Level level, BlockPos pos,
            BezierTrackPointLocation bezier, boolean direction, Vec3 look, boolean simulate) {
        if(level.isClientSide()&&!simulate)return false;
        var plan=plan(train,level,pos,bezier,direction,look);
        if(plan==null||!plan.preview.valid||!current(plan,level))return false;
        if(simulate)return true;
        var manager=train.trafficManager();
        if(!(level instanceof ServerLevel)||manager==null||manager.get(train.id)!=train)return false;
        manager.validateCustody();
        // All target wheels resolve before any old station, graph, cargo or passenger owner changes.
        train.leaveStation();
        if(!current(plan,level))return false;
        train.derailed=false;
        train.navigation.waitingForSignal=null;
        train.occupiedSignalBlocks.clear();
        train.graph=plan.graph;
        train.speed=0;
        train.migratingPoints.clear();
        train.cancelStall();
        if(train.navigation.destination!=null)train.navigation.cancelNavigation();
        for(int i=0;i<plan.wheels.size();i++) {
            var wheel=plan.wheels.get(i).actual;
            var location=plan.locations.get(plan.locations.size()-1-i);
            wheel.node1=location.first;wheel.node2=location.second;
            wheel.edge=location.edge;wheel.position=location.position;
        }
        for(var carriage:train.carriages)carriage.updateContraptionAnchors();
        train.status.successfulMigration();
        train.collectInitiallyOccupiedSignalBlocks();
        manager.markDirty();
        return true;
    }

    private static boolean owned(Train train, Level level) {
        if(train==null||train.invalid||train.carriages.isEmpty()
                ||train.carriages.size()>Train.MAX_CARRIAGES||train.isRestoring())return false;
        if(level.isClientSide())return TrainClientRegistry.get(level,train.id)==train;
        return level instanceof ServerLevel server&&train.trafficManager()==TrainManager.get(server)
            &&train.trafficManager().get(train.id)==train;
    }

    private static boolean current(Plan plan, Level level) {
        if(!owned(plan.train,level)||plan.graph.revision()!=plan.revision
                ||TrackGraphHelper.getGraph(level,plan.graph.id)!=plan.graph)return false;
        var currentWheels=new ArrayList<TravellingPoint>();
        plan.train.forEachTravellingPoint(currentWheels::add);
        if(currentWheels.size()!=plan.wheels.size())return false;
        for(int i=0;i<currentWheels.size();i++)
            if(currentWheels.get(i)!=plan.wheels.get(i).actual||!plan.wheels.get(i).unchanged())return false;
        for(var location:plan.locations)
            if(plan.graph.locateNode(location.first.getLocation())!=location.first
                    ||plan.graph.locateNode(location.second.getLocation())!=location.second
                    ||plan.graph.getConnectionsFrom(location.first).get(location.second)!=location.edge
                    ||!Double.isFinite(location.position)||location.position<0
                    ||location.position>location.edge.getLength()+1e-6)return false;
        return true;
    }

    private static Plan plan(Train train, Level level, BlockPos pos,
            BezierTrackPointLocation bezier, boolean direction, Vec3 look) {
        if(!owned(train,level)||look==null||!Double.isFinite(look.lengthSqr())
                ||look.lengthSqr()<1e-12||!LoadedLevelAccess.isLoaded(level,pos))return null;
        var state=LoadedLevelAccess.blockState(level,pos);
        if(!(state.getBlock() instanceof ITrackBlock track))return null;
        var nearest=track.getNearestTrackAxis(level,pos,state,look);
        var graphLocation=bezier!=null
            ?TrackGraphHelper.getBezierGraphLocationAt(level,pos,
                direction?AxisDirection.POSITIVE:AxisDirection.NEGATIVE,bezier)
            :TrackGraphHelper.getGraphLocationAt(level,pos,nearest.getSecond(),nearest.getFirst());
        if(graphLocation==null)return null;
        var graph=graphLocation.graph;long revision=graph.revision();
        var first=graph.locateNode(graphLocation.edge.getFirst());
        var second=graph.locateNode(graphLocation.edge.getSecond());
        if(first==null||second==null)return null;
        var edge=graph.getConnectionsFrom(first).get(second);
        if(edge==null)return null;
        var probe=new TravellingPoint(first,second,edge,graphLocation.position,false);
        var steer=probe.steer(TravellingPoint.SteerDirection.NONE,track.getUpNormal(level,pos,state));
        var ignoreSignals=probe.ignoreEdgePoints();var ignoreTurns=probe.ignoreTurns();
        var locations=new ArrayList<Location>();var positions=new ArrayList<Vec3>();
        var wheels=new ArrayList<Wheel>();train.forEachTravellingPoint(point->wheels.add(new Wheel(point)));
        boolean[] blocked={false};
        train.forEachTravellingPointBackwards((point,distance)->{
            if(blocked[0])return;
            boolean[] portal={false};
            probe.travel(graph,distance,steer,ignoreSignals,ignoreTurns,ignored->{portal[0]=true;return true;});
            locations.add(new Location(probe.node1,probe.node2,probe.edge,probe.position));
            var position=probe.getPosition(graph);positions.add(position);
            if(probe.blocked||portal[0]||!level.dimension().equals(probe.node1.getLocation().dimension)
                    ||!LoadedLevelAccess.isLoaded(level,BlockPos.containing(position)))blocked[0]=true;
        });
        boolean valid=!blocked[0]&&locations.size()==wheels.size();
        if(valid)for(int i=0;i<positions.size()-1;i++) {
            if(train.findCollidingTrain(level,positions.get(i),positions.get(i+1),level.dimension(),graph)!=null) {
                positions.subList(i+2,positions.size()).clear();valid=false;break;
            }
        }
        return new Plan(train,graph,revision,List.copyOf(wheels),List.copyOf(locations),new Preview(valid,positions));
    }

    private TrainRelocator() {}
}
