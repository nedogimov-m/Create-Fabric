package com.simibubi.create.content.trains.track;

import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Source curve safe-NBT and detached transform; portal bindings and live graph owners never enter a schematic. */
public final class TrackSchematicSupport {
    public static void writeSafe(BlockEntity entity,CompoundTag tag,HolderLookup.Provider registries){
        var track=(TrackBlockEntity)entity;var encoded=new ListTag();for(var curve:track.getConnections().values())encoded.add(track.tilt.restoreToOriginalCurve(curve.clone()).write(track.getBlockPos()));tag.put("Connections",encoded);
    }
    public static void transform(BlockEntity entity,StructureTransform transform){
        var track=(TrackBlockEntity)entity;var restored=new LinkedHashMap<BlockPos,BezierConnection>();
        for(var curve:track.getConnections().values()){var fresh=track.tilt.restoreToOriginalCurve(track.tilt.restoreToOriginalCurve(curve.clone().secondary()).secondary());restored.put(fresh.getKey(),fresh);}
        track.tilt.smoothingAngle=Optional.empty();track.tilt.afterLoad();
        if(transform.rotationAxis!=Direction.Axis.Y){track.replaceSmoothingConnections(restored);return;}
        var changed=new LinkedHashMap<BlockPos,BezierConnection>();
        for(var curve:restored.values()){
            curve.normals.replace(transform::applyWithoutOffsetUncentered);curve.axes.replace(transform::applyWithoutOffsetUncentered);
            var delta=curve.bePositions.getSecond().subtract(curve.bePositions.getFirst());curve.bePositions.setSecond(BlockPos.containing(Vec3.atCenterOf(curve.bePositions.getFirst()).add(transform.applyWithoutOffsetUncentered(Vec3.atLowerCornerOf(delta)))));
            var center=Vec3.atCenterOf(track.getBlockPos());var start=curve.starts.getFirst();var transformedStart=transform.applyWithoutOffsetUncentered(start.subtract(center)).add(center);var transformedEnd=transform.applyWithoutOffsetUncentered(curve.starts.getSecond().subtract(start)).add(transformedStart);
            curve.starts.setFirst(new TrackNodeLocation(transformedStart).getLocation());curve.starts.setSecond(new TrackNodeLocation(transformedEnd).getLocation());if(changed.putIfAbsent(curve.getKey(),curve)!=null)throw new IllegalArgumentException("Transformed schematic curves collide on one actual endpoint");
        }
        track.replaceSmoothingConnections(changed);
    }
    /** Only actual loaded reciprocal endpoints can acquire source geometry. Existing curve/portal owners survive. */
    public static boolean merge(TrackBlockEntity live, TrackBlockEntity source) {
        var level=live.getLevel();var pos=live.getBlockPos();
        if(!(level instanceof net.minecraft.server.level.ServerLevel)||live.isRemoved()||TrackBlockEntityAccess.existing(level,pos)!=live||source.boundLocation!=null||source.getPortalBinding()!=null)return false;
        if(live.boundLocation!=null||live.getPortalBinding()!=null)return false;
        var additions=new ArrayList<BezierConnection>();
        for(var original:source.getConnections().values()){
            var curve=original.clone();var target=curve.getKey();
            if(!curve.bePositions.getFirst().equals(pos)||target.equals(pos)||!level.isInValidBounds(target)||!level.getWorldBorder().isWithinBounds(target)||!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,target)||TrackConnectionCleanup.isPending((net.minecraft.server.level.ServerLevel)level,curve))return false;
            var liveState=live.getBlockState();if(!(liveState.getBlock() instanceof ITrackBlock first))return false;var startAxis=TrackBlockEntityTilt.axisBeforeSmoothing(curve,true);if(first.getTrackAxes(level,pos,liveState).stream().noneMatch(candidate->candidate.normalize().distanceTo(startAxis)<1/1024f||candidate.normalize().distanceTo(startAxis.scale(-1))<1/1024f))return false;
            var state=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,target);
            if(!(state.getBlock() instanceof ITrackBlock track)||!state.hasProperty(TrackBlock.HAS_BE))return false;
            var axis=TrackBlockEntityTilt.axisBeforeSmoothing(curve,false);
            if(track.getTrackAxes(level,target,state).stream().noneMatch(candidate->candidate.normalize().distanceTo(axis)<1/1024f||candidate.normalize().distanceTo(axis.scale(-1))<1/1024f))return false;
            var old=live.getConnections().get(target);
            if(old!=null){var geometry=live.tilt.restoreToOriginalCurve(old.clone());if(!geometry.equalsSansMaterial(curve)||geometry.getMaterial()!=curve.getMaterial())return false;}
            if(TrackBlockEntityAccess.existing(level,target) instanceof TrackBlockEntity other){
                if(other.boundLocation!=null||other.getPortalBinding()!=null)return false;
                var reverse=other.getConnections().get(pos);if(reverse!=null){var geometry=other.tilt.restoreToOriginalCurve(reverse.clone());if(!geometry.equalsSansMaterial(curve)||geometry.getMaterial()!=curve.getMaterial())return false;}
            }
            additions.add(curve);
        }
        // Require genuine reciprocal native owners. No half-installed source maps at an unknown frontier.
        var before=new LinkedHashMap<TrackBlockEntity,Map<BlockPos,BezierConnection>>();
        before.put(live,new LinkedHashMap<>(live.getConnections()));
        for(var curve:additions){var other=TrackBlockEntityAccess.existing(level,curve.getKey());if(!(other instanceof TrackBlockEntity endpoint))return false;before.putIfAbsent(endpoint,new LinkedHashMap<>(endpoint.getConnections()));}
        try{TrackPropagator.preflightChanges((net.minecraft.server.level.ServerLevel)level,before.keySet().stream().map(BlockEntity::getBlockPos).toList());}catch(UnloadedTrackException unavailable){return false;}
        var after=new LinkedHashMap<TrackBlockEntity,Map<BlockPos,BezierConnection>>();before.forEach((owner,connections)->after.put(owner,new LinkedHashMap<>(connections)));
        for(var curve:additions){var other=(TrackBlockEntity)TrackBlockEntityAccess.existing(level,curve.getKey());after.get(live).putIfAbsent(curve.getKey(),curve);after.get(other).putIfAbsent(pos,curve.secondary());}
        // Publish every actual map without native callbacks between endpoint writes.
        after.forEach(TrackBlockEntity::replaceSmoothingConnections);
        try{
            for(var endpoint:before.keySet())endpoint.finishSmoothingUpdate();
            for(var endpoint:before.keySet())if(endpoint.isRemoved()||TrackBlockEntityAccess.existing(level,endpoint.getBlockPos())!=endpoint)throw new IllegalStateException("Actual track endpoint owner changed during schematic merge callback");
            for(var curve:additions)if(curve.primary)live.manageFakeTracksAlong(curve,false);
            return true;
        }catch(RuntimeException failure){
            // Restore only the exact retained native owners. External replacement cargo is never erased.
            for(var entry:before.entrySet())if(!entry.getKey().isRemoved()&&TrackBlockEntityAccess.existing(level,entry.getKey().getBlockPos())==entry.getKey())entry.getKey().replaceSmoothingConnections(entry.getValue());
            for(var endpoint:before.keySet())if(!endpoint.isRemoved()&&TrackBlockEntityAccess.existing(level,endpoint.getBlockPos())==endpoint)endpoint.finishSmoothingUpdate();
            com.simibubi.create.Create.LOGGER.error("Actual source track merge was refused; original paid launch remains owned",failure);return false;
        }
    }

    private TrackSchematicSupport(){}
}
