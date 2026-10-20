package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.trains.graph.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Genuine source wheel migration, preserving dimension and finite zero-length portal identity. */
public final class TrainMigration {
    Couple<TrackNodeLocation> locations;
    double positionOnOldEdge;
    boolean curve;
    Vec3 fallback;
    public TrainMigration(){}
    public TrainMigration(TravellingPoint point){
        if(point.edge==null||point.node1==null||point.node2==null||!Double.isFinite(point.position))throw new IllegalArgumentException("Actual complete wheel point required for migration");
        double length=point.edge.getLength();double t=length==0?0:point.position/length;fallback=point.edge.getPosition(null,t);curve=point.edge.isTurn();positionOnOldEdge=point.position;locations=Couple.create(point.node1.getLocation().copy(),point.node2.getLocation().copy());validate();
    }
    public TrackGraphLocation tryMigratingTo(TrackGraph graph){
        validate();var node1=graph.locateNode(locations.getFirst());var node2=graph.locateNode(locations.getSecond());
        if(node1!=null&&node2!=null){var edge=graph.getConnectionsFrom(node1).get(node2);if(edge!=null&&positionOnOldEdge<=edge.getLength())return location(graph,locations,positionOnOldEdge);}
        // Curves and real dimension portals only migrate to their exact source endpoints.
        if(curve||!locations.getFirst().dimension.equals(locations.getSecond().dimension))return null;
        var directionBefore=locations.getSecond().getLocation().subtract(locations.getFirst().getLocation()).normalize();
        for(var candidate:graph.getNodes()){
            var origin=candidate.getLocation();if(!candidate.dimension.equals(locations.getFirst().dimension)||origin.distanceToSqr(fallback)>32*32)continue;
            var first=graph.locateNode(candidate);
            for(var entry:graph.getConnectionsFrom(first).entrySet()){
                var edge=entry.getValue();var second=entry.getKey();if(edge.isTurn()||edge.isInterDimensional()||!second.getLocation().dimension.equals(locations.getSecond().dimension))continue;
                var direction=edge.getDirection(true);if(!Mth.equal(direction.dot(directionBefore),1))continue;
                double radius=1/64d;var intersection=TrainMath.intersectSphere(origin,direction,fallback,radius);if(intersection==null||!Mth.equal(direction.dot(intersection.subtract(origin).normalize()),1))continue;
                double position=intersection.distanceTo(origin)-radius;if(!Double.isFinite(position)||position<0||position>edge.getLength())continue;
                return location(graph,Couple.create(candidate,second.getLocation()),position);
            }
        }
        return null;
    }
    private static TrackGraphLocation location(TrackGraph graph,Couple<TrackNodeLocation> endpoints,double position){var result=new TrackGraphLocation();result.graph=graph;result.edge=endpoints.map(TrackNodeLocation::copy);result.position=position;return result;}
    private void validate(){if(locations==null||locations.either(java.util.Objects::isNull)||fallback==null||!RailCodecs.finite(fallback)||!Double.isFinite(positionOnOldEdge)||positionOnOldEdge<0)throw new IllegalArgumentException("Invalid actual persisted train migration geometry");}
    public CompoundTag write(DimensionPalette dimensions){validate();var tag=new CompoundTag();tag.putBoolean("Curve",curve);tag.store("Fallback",RailCodecs.VECTOR,fallback);tag.putDouble("Position",positionOnOldEdge);tag.put("Nodes",RailCodecs.writeCouple(locations,location->location.write(dimensions)));return tag;}
    public static TrainMigration read(CompoundTag tag,DimensionPalette dimensions){var value=new TrainMigration();value.curve=tag.getBooleanOr("Curve",false);value.fallback=tag.read("Fallback",RailCodecs.VECTOR).orElseThrow();value.positionOnOldEdge=tag.getDoubleOr("Position",Double.NaN);value.locations=RailCodecs.readCouple(tag,"Nodes",location->TrackNodeLocation.read(location,dimensions));value.validate();return value;}
}
