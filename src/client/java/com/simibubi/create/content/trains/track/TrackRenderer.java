package com.simibubi.create.content.trains.track;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.decoration.girder.GirderModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Source tie/rail frame math; extraction freezes native meshes, transforms and per-segment world light. */
public final class TrackRenderer implements BlockEntityRenderer<TrackBlockEntity,TrackRenderer.State> {
    public record Piece(PartialModelRenderState model,Matrix4f transform,int light){}
    public static final class State extends BlockEntityRenderState{public List<Piece> pieces=List.of();public int primaryCurves,segments,girderPieces;}
    public TrackRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(TrackBlockEntity track,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(track,state,partial,camera,overlay);var pieces=new ArrayList<Piece>();state.primaryCurves=state.segments=state.girderPieces=0;
        if(!track.getBlockState().getOptionalValue(TrackBlock.HAS_BE).orElse(false)){state.pieces=List.of();return;}
        for(var curve:track.getConnections().values()){
            if(!curve.isPrimary())continue;state.primaryCurves++;
            if(curve.hasGirder)state.girderPieces+=extractGirder(track,curve,pieces,state.lightCoords);
            var models=TrackModels.get(curve.getMaterial());
            var tie=new PartialModelRenderState();var leftModel=new PartialModelRenderState();var rightModel=new PartialModelRenderState();
            AllPartialModels.extract(models.tie(),tie);AllPartialModels.extract(models.leftSegment(),leftModel);AllPartialModels.extract(models.rightSegment(),rightModel);
            Vec3 previousLeft=null,previousRight=null;int last=curve.getSegmentCount();
            for(var segment:curve){
                var left=segment.position.add(segment.normal.scale(.965f));var right=segment.position.subtract(segment.normal.scale(.965f));
                if(previousLeft==null){previousLeft=left;previousRight=right;continue;}
                var middle=left.add(right).scale(.5);var prevMiddle=previousLeft.add(previousRight).scale(.5);
                int light=track.getLevel()==null?state.lightCoords:net.minecraft.util.LightCoordsUtil.getLightCoords(track.getLevel(),BlockPos.containing(middle).offset(track.getBlockPos()));
                var angle=getModelAngles(segment.normal,middle.subtract(prevMiddle));var pose=frame(prevMiddle,angle);pose.translate(-.5,-2/16f-1/256f,0);
                pieces.add(new Piece(tie,new Matrix4f(pose.last().pose()),light));
                float scale=segment.index==last?2.2f:2.1f;
                for(boolean first:new boolean[]{true,false}){
                    var start=first?previousLeft:previousRight;var diff=(first?left:right).subtract(start);var rail=frame(start,getModelAngles(segment.normal,diff));
                    rail.translate(0,-2/16f-1/256f,-1/32f);rail.scale(1,1,(float)diff.length()*scale);
                    pieces.add(new Piece(first?leftModel:rightModel,new Matrix4f(rail.last().pose()),light));
                }
                state.segments++;previousLeft=left;previousRight=right;
            }
        }
        state.pieces=List.copyOf(pieces);
    }
    /** Original BezierConnection.GirderAngles: both side beams and their exact top/bottom caps. */
    private static int extractGirder(TrackBlockEntity track,BezierConnection curve,List<Piece> pieces,int fallbackLight){
        var middleModel=new PartialModelRenderState();var topModel=new PartialModelRenderState();var bottomModel=new PartialModelRenderState();
        AllPartialModels.extract(GirderModels.SEGMENT_MIDDLE,middleModel);AllPartialModels.extract(GirderModels.SEGMENT_TOP,topModel);AllPartialModels.extract(GirderModels.SEGMENT_BOTTOM,bottomModel);
        Vec3[] previousTop=null,previousBottom=null;int added=0,last=curve.getSegmentCount();
        for(var segment:curve){
            var up=segment.derivative.normalize().cross(segment.normal);var firstOffset=up.scale(-8/16f);var secondOffset=up.scale(-10/16f);
            Vec3[] top={segment.position.add(segment.normal.scale(1)).add(firstOffset),segment.position.subtract(segment.normal.scale(1)).add(firstOffset)};
            Vec3[] bottom={top[0].add(secondOffset),top[1].add(secondOffset)};
            if(previousTop==null){previousTop=top;previousBottom=bottom;continue;}
            int light=track.getLevel()==null?fallbackLight:net.minecraft.util.LightCoordsUtil.getLightCoords(track.getLevel(),BlockPos.containing(segment.position).offset(track.getBlockPos()));float scale=segment.index==last?2.3f:2.2f;
            float capYOffset=2/16f+(segment.index%2==0?1:-1)/2048f-1/1024f;
            for(int side=0;side<2;side++){
                var current=top[side].add(bottom[side]).scale(.5);var previous=previousTop[side].add(previousBottom[side]).scale(.5);var difference=current.subtract(previous);
                var beam=frame(previous,getModelAngles(segment.normal,difference));beam.translate(0,capYOffset,-1/32f);beam.scale(1,1,(float)difference.length()*scale);pieces.add(new Piece(middleModel,new Matrix4f(beam.last().pose()),light));added++;
                for(boolean upper:new boolean[]{true,false}){
                    var start=upper?previousTop[side]:previousBottom[side];var diff=(upper?top[side]:bottom[side]).subtract(start);var cap=frame(start,getModelAngles(segment.normal,diff));cap.translate(0,capYOffset,-1/32f);cap.scale(1,1,(float)diff.length()*scale);
                    pieces.add(new Piece(upper?topModel:bottomModel,new Matrix4f(cap.last().pose()),light));added++;
                }
            }
            previousTop=top;previousBottom=bottom;
        }
        return added;
    }
    private static PoseStack frame(Vec3 offset,Vec3 angle){var pose=new PoseStack();pose.translate(offset);pose.rotate(Axis.YP.rotation((float)angle.y));pose.rotate(Axis.XP.rotation((float)angle.x));pose.rotate(Axis.ZP.rotation((float)angle.z));return pose;}
    /** Same source yaw/pitch/roll basis, independent of mutable block entities or legacy model APIs. */
    public static Vec3 getModelAngles(Vec3 normal,Vec3 diff){
        double length=Math.sqrt(diff.x*diff.x+diff.z*diff.z);double yaw=Math.atan2(diff.x,diff.z);double pitch=Math.atan2(length,diff.y)-Math.PI*.5;
        var transformed=new Vector3f(0,1,0).rotateX((float)pitch).rotateY((float)yaw);var yawPitchNormal=new Vec3(transformed.x,transformed.y,transformed.z);
        double sign=Math.signum(yawPitchNormal.dot(normal));if(Math.abs(sign)<.5)sign=yawPitchNormal.distanceToSqr(normal)<.5?-1:1;
        double dot=diff.cross(normal).normalize().dot(yawPitchNormal);return new Vec3(pitch,yaw,Math.acos(Mth.clamp(dot,-1,1))*sign);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){for(var piece:state.pieces){pose.pushPose();pose.mulPose(piece.transform());piece.model().submit(pose,collector,piece.light(),OverlayTexture.NO_OVERLAY,0);pose.popPose();}}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 192;}
}
