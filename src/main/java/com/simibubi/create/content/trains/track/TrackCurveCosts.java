package com.simibubi.create.content.trains.track;

import net.minecraft.world.phys.Vec3;

/** Separate source-bug fix: physical item cost of pinned source andesite rails before slope smoothing.
 * Pure geometry/data only. No level access, cached paid amount, extra NBT, refund debt, or change to Bezier physics APIs.
 */
public final class TrackCurveCosts {
    public static BezierConnection sourceOriginalGeometry(BezierConnection curve){
        var original=curve.clone();if(curve.smoothing==null||curve.getMaterial()!=TrackMaterial.ANDESITE||curve.getMaterial().trackType!=TrackMaterial.TrackType.STANDARD)return original;
        for(boolean first:new boolean[]{true,false}){
            var axis=curve.axes.get(first);if(curve.normals.get(first).distanceToSqr(new Vec3(0,1,0))>1e-12||Math.abs(axis.y)<1e-12)continue;
            // Source standard flat shapes are XO/ZO/PD/ND. Their genuine BE position plus signed
            // source horizontal axis determines the original half-grid handle, including diagonals.
            double x=Math.abs(axis.x),z=Math.abs(axis.z);if(x<1e-12&&z<1e-12||x>1e-12&&z>1e-12&&Math.abs(x-z)>1e-8)continue;
            var sourceAxis=new Vec3(x<1e-12?0:Math.signum(axis.x),0,z<1e-12?0:Math.signum(axis.z));var pos=curve.bePositions.get(first);
            original.starts.set(first,Vec3.atCenterOf(pos).add(0,-.5,0).add(sourceAxis.scale(.5)));original.axes.set(first,sourceAxis.normalize());
        }
        original.smoothing=null;return original;
    }
    public static int getTrackItemCost(BezierConnection curve){return sourceOriginalGeometry(curve).getTrackItemCost();}
    public static int getGirderItemCost(BezierConnection curve){return curve.hasGirder?getTrackItemCost(curve)*2:0;}
    private TrackCurveCosts(){}
}
