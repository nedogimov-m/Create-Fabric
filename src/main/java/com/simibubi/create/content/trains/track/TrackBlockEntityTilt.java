package com.simibubi.create.content.trains.track;

import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Actual source two-slope smoothing and handle restoration, with discovery before reciprocal geometry ownership changes. */
public final class TrackBlockEntityTilt {
    public Optional<Double> smoothingAngle=Optional.empty();
    private Couple<Pair<Vec3,Integer>> previousSmoothingHandles;
    private final TrackBlockEntity blockEntity;
    private boolean pending;
    private int retryDelay;
    public TrackBlockEntityTilt(TrackBlockEntity blockEntity){this.blockEntity=blockEntity;}

    public void tryApplySmoothing(){
        if(smoothingAngle.isPresent()||!(blockEntity.getLevel() instanceof ServerLevel level)||blockEntity.isRemoved())return;
        pending=false;var state=blockEntity.getBlockState();var pos=blockEntity.getBlockPos();
        if(!(state.getBlock() instanceof ITrackBlock track)||blockEntity.boundLocation!=null)return;
        var axes=track.getTrackAxes(level,pos,state);if(axes.size()!=1||axes.getFirst().y!=0||axes.getFirst().lengthSqr()<1e-12)return;
        BezierConnection low=null,high=null;Vec3 axis=null;
        for(var curve:blockEntity.getConnections().values()){
            if(curve.starts.getFirst().y==curve.starts.getSecond().y)continue;var normed=curve.axes.getFirst().normalize();
            if(axis!=null){if(high!=null||normed.dot(axis)>-1+1/64d)return;high=curve;}
            else{axis=normed;low=curve;}
        }
        if(low==null||high==null)return;
        if(low.starts.getSecond().y>high.starts.getSecond().y){var swap=low;low=high;high=swap;}
        if(low.starts.getSecond().y>low.starts.getFirst().y||high.starts.getSecond().y<high.starts.getFirst().y)return;
        // Source removes all other local links when selecting exactly two opposite non-level slopes.
        // Keep that source eligibility explicit; don't silently discard a third paid physical curve.
        if(blockEntity.getConnections().size()!=2)return;
        double horizontalDistance=low.getLength()+high.getLength();var base=low.axes.getFirst();double baseLength=base.x!=0&&base.z!=0?Math.sqrt(2):1;
        double m=(high.starts.getSecond().y-low.starts.getSecond().y)/horizontalDistance;if(!Double.isFinite(m)||m<=0)return;
        var difference=high.starts.getFirst().subtract(low.starts.getFirst());boolean flip=difference.dot(new Vec3(1,0,2).normalize())<=0;
        double angle=Math.toDegrees(Mth.atan2(m,1))*(flip?-1:1);int offset=Mth.clamp((int)(m*baseLength*16),0,15);
        var raised=difference.normalize().add(0,Mth.clamp(m,0,1-1/512d),0).normalize().scale(baseLength);
        var adjustedLow=low.clone();var adjustedHigh=high.clone();
        adjustedHigh.starts.setFirst(adjustedLow.starts.getFirst().add(raised));
        for(boolean first:new boolean[]{true,false}){
            var curve=first?adjustedLow:adjustedHigh;if(curve.smoothing==null)curve.smoothing=Couple.create(0,0);curve.smoothing.setFirst(first?0:offset);
            curve.axes.setFirst(curve.axes.getFirst().add(0,(first?1:-1)*-m,0).normalize());
        }
        try{replace(List.of(adjustedLow,adjustedHigh),Optional.of(angle));}catch(UnloadedTrackException unavailable){pending=true;retryDelay=20;}
    }

    public void captureSmoothingHandles(){
        previousSmoothingHandles=Couple.create(null,null);boolean first=true;
        for(var curve:blockEntity.getConnections().values()){
            previousSmoothingHandles.set(first,Pair.of(curve.starts.getFirst(),curve.smoothing==null?0:curve.smoothing.getFirst()));if(!first)break;first=false;
        }
    }
    public void undoSmoothing(){
        if(smoothingAngle.isEmpty()||previousSmoothingHandles==null||blockEntity.getConnections().size()==2||!(blockEntity.getLevel() instanceof ServerLevel))return;
        var originals=new ArrayList<BezierConnection>();for(var curve:blockEntity.getConnections().values())originals.add(restoreToOriginalCurve(curve.clone()));
        try{replace(originals,Optional.empty());previousSmoothingHandles=null;pending=false;}catch(UnloadedTrackException unavailable){pending=true;retryDelay=20;}
    }
    public void connectionsChanged(){if(smoothingAngle.isEmpty()){pending=true;retryDelay=1;}}
    public void tick(){
        if(smoothingAngle.isPresent()){
            if(blockEntity.getConnections().size()==2){pending=false;return;}
            if(previousSmoothingHandles==null)captureSmoothingHandles();
            if(!pending||--retryDelay<=0)undoSmoothing();
        }else if(pending&&--retryDelay<=0)tryApplySmoothing();
    }
    public void afterLoad(){pending=false;retryDelay=0;previousSmoothingHandles=null;if(smoothingAngle.isPresent()&&blockEntity.getConnections().size()!=2)captureSmoothingHandles();}

    /** Fresh source restore. Mutate only a detached clone: Bezier caches its real runtime geometry. */
    public BezierConnection restoreToOriginalCurve(BezierConnection curve){
        if(curve.smoothing!=null){curve.smoothing.setFirst(0);if(curve.smoothing.both(value->value==0))curve.smoothing=null;}
        curve.starts.setFirst(new TrackNodeLocation(curve.starts.getFirst()).getLocation());curve.axes.setFirst(curve.axes.getFirst().multiply(1,0,1).normalize());return curve;
    }
    public int getYOffsetForAxisEnd(Vec3 end){
        if(smoothingAngle.isEmpty())return 0;
        for(var curve:blockEntity.getConnections().values())if(compareHandles(curve.starts.getFirst(),end))return curve.yOffsetAt(end);
        if(previousSmoothingHandles!=null)for(var handle:previousSmoothingHandles)if(handle!=null&&compareHandles(handle.getFirst(),end))return handle.getSecond();return 0;
    }
    public static boolean compareHandles(Vec3 a,Vec3 b){return new TrackNodeLocation(a).getLocation().multiply(1,0,1).distanceToSqr(new TrackNodeLocation(b).getLocation().multiply(1,0,1))<1/512f;}
    /** Original flat track handles can carry a source smoothing Y axis while retaining their genuine UP face normal. */
    public static Vec3 axisBeforeSmoothing(BezierConnection curve,boolean first){var axis=curve.axes.get(first);return curve.smoothing!=null&&curve.normals.get(first).distanceToSqr(new Vec3(0,1,0))<1e-12?axis.multiply(1,0,1).normalize():axis;}

    private record Binding(TrackBlockEntity endpoint,BlockState state,Map<BlockPos,BezierConnection> before,Map<BlockPos,BezierConnection> after){}
    private void replace(List<BezierConnection> replacements,Optional<Double> angle){
        var level=(ServerLevel)blockEntity.getLevel();var pos=blockEntity.getBlockPos();var expected=blockEntity.getConnections();var bindings=new LinkedHashMap<BlockPos,Binding>();
        var ownerMap=new LinkedHashMap<BlockPos,BezierConnection>();for(var curve:replacements)ownerMap.put(curve.getKey(),curve);
        if(!LoadedLevelAccess.isLoaded(level,pos)||TrackBlockEntityAccess.existing(level,pos)==null)throw new UnloadedTrackException(level,pos);
        if(TrackBlockEntityAccess.existing(level,pos)!=blockEntity)throw new IllegalStateException("Smoothing local native endpoint binding changed");
        bindings.put(pos,new Binding(blockEntity,blockEntity.getBlockState(),new LinkedHashMap<>(expected),ownerMap));
        for(var before:expected.values()){
            var target=before.getKey();if(!LoadedLevelAccess.isLoaded(level,target))throw new UnloadedTrackException(level,target);
            if(!(TrackBlockEntityAccess.existing(level,target) instanceof TrackBlockEntity other))throw new UnloadedTrackException(level,target);
            var reverse=other.getConnections().get(pos);if(reverse==null||reverse.primary==before.primary||reverse.getMaterial()!=before.getMaterial()||!reverse.equalsSansMaterial(before))throw new IllegalStateException("Smoothing reciprocal geometry differs at "+target);
            var after=new LinkedHashMap<>(other.getConnections());var replacement=ownerMap.get(target);if(replacement==null)throw new IllegalStateException("Smoothing may not discard a paid source curve");after.put(pos,replacement.secondary());
            bindings.put(target,new Binding(other,other.getBlockState(),new LinkedHashMap<>(other.getConnections()),after));
        }
        TrackPropagator.preflightChanges(level,bindings.keySet());
        for(var entry:bindings.entrySet()){
            var at=entry.getKey();var binding=entry.getValue();
            if(!LoadedLevelAccess.isLoaded(level,at)||TrackBlockEntityAccess.existing(level,at)==null)throw new UnloadedTrackException(level,at);
            if(TrackBlockEntityAccess.existing(level,at)!=binding.endpoint||LoadedLevelAccess.blockState(level,at)!=binding.state||!binding.before.equals(binding.endpoint.getConnections()))throw new IllegalStateException("Smoothing native endpoint binding changed after read-only preflight");
        }
        // No native block/property/BE lifecycle transition and no inventory operation occurs in this geometry-only swap.
        for(var binding:bindings.values())binding.endpoint.replaceSmoothingConnections(binding.after);
        smoothingAngle=angle;
        for(var binding:bindings.values()){
            for(var curve:binding.before.values())if(curve.primary)TrackCurveCoverage.update(level,curve,true);
            for(var curve:binding.after.values())if(curve.primary)TrackCurveCoverage.update(level,curve,false);
            binding.endpoint.finishSmoothingUpdate();
        }
    }
}
