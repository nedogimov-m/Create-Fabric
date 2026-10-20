package com.simibubi.create.content.trains.track;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;

/** Source two-sample segment selection box; both native client picking and server packet admission use this geometry. */
public final class TrackCurveSelection {
    public record Hit(BlockPos owner,BlockPos target,int segment,Vec3 point,Vec3 anchor,Vec3 side,Vec3 up,Vec3 direction){}
    private static final AABB SOURCE_BOX=new AABB(-1.375,-.125,-.5,1.375,.125,.5);
    public static Hit trace(BezierConnection curve,Vec3 from,Vec3 to){
        if(!curve.getBounds().contains(from)&&curve.getBounds().clip(from,to).isEmpty())return null;
        var steps=curve.getStepLUT();int count=curve.getSegmentCount();Hit best=null;double nearest=from.distanceToSqr(to)+1e-8;
        for(int i=0;i<steps.length-2;i++){
            double t=steps[i]*i/count,t1=steps[i+1]*(i+1)/count,t2=steps[i+2]*(i+2)/count;
            var start=curve.getPosition(t);var end=curve.getPosition(t2);var direction=end.subtract(start).normalize();if(direction.lengthSqr()<1e-12)continue;
            var up=curve.getNormal(t1).normalize();var side=up.cross(direction).normalize();up=direction.cross(side).normalize();
            var anchor=start.add(end).scale(.5);var a=project(from.subtract(anchor),side,up,direction);var b=project(to.subtract(anchor),side,up,direction);
            var local=SOURCE_BOX.clip(a,b).orElse(null);if(local==null)continue;
            var point=anchor.add(side.scale(local.x)).add(up.scale(local.y)).add(direction.scale(local.z));double distance=from.distanceToSqr(point);if(distance>nearest)continue;
            nearest=distance;best=new Hit(curve.bePositions.getFirst(),curve.getKey(),i,point,anchor,side,up,direction);
        }
        return best;
    }
    private static Vec3 project(Vec3 vector,Vec3 side,Vec3 up,Vec3 direction){return new Vec3(vector.dot(side),vector.dot(up),vector.dot(direction));}
    /** Strict loaded-only shape/occlusion view: indirect neighbor lookups cannot turn missing terrain into air. */
    public static BlockHitResult obstacle(Level level,Vec3 from,Vec3 to,net.minecraft.world.entity.Entity entity){return new StrictView(level).clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,entity));}
    private record StrictView(Level level) implements BlockGetter {
        private void require(BlockPos pos){if(!LoadedLevelAccess.isLoaded(level,pos))throw new UnloadedTrackException(level,pos);}
        @Override public BlockState getBlockState(BlockPos pos){require(pos);return LoadedLevelAccess.blockState(level,pos);}
        @Override public BlockEntity getBlockEntity(BlockPos pos){require(pos);return LoadedLevelAccess.blockEntity(level,pos);}
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinY(){return level.getMinY();}
    }
    public static boolean visible(Vec3 from,Hit selected,BlockHitResult obstacle){return obstacle.getType()==HitResult.Type.MISS||from.distanceToSqr(obstacle.getLocation())+1e-6>=from.distanceToSqr(selected.point());}
    private TrackCurveSelection(){}
}
