package net.createmod.catnip.outliner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pinned Catnip closed cuboids and textured faces through the actual native submission pipeline. */
public final class OutlineRenderer {
    public static final Identifier BLANK=Identifier.fromNamespaceAndPath("ponder","textures/special/blank.png");
    public static void submit(PoseStack pose,SubmitNodeCollector collector,Outline.Geometry geometry){
        for(var face:geometry.faces())submitFace(pose,collector,geometry,face);
        for(var line:geometry.segments())submitLine(pose,collector,geometry,line);
    }
    private static void submitFace(PoseStack pose,SubmitNodeCollector collector,Outline.Geometry geometry,Outline.Face face){
        var type=face.cull()?RenderTypes.entityTranslucentCull(face.texture()):RenderTypes.entityTranslucent(face.texture());
        // Native no-cull already renders both sides; duplicating reversed quads doubles source opacity.
        collector.submitCustomGeometry(pose,type,(matrix,vertices)->emitFace(matrix,vertices,geometry,face));
    }
    private static void emitFace(PoseStack.Pose pose,VertexConsumer vertices,Outline.Geometry geometry,Outline.Face face){
        emitQuad(pose,vertices,face.a(),face.b(),face.c(),face.d(),face.normal().getUnitVec3(),face.color(),geometry.light(),face.maxU(),face.maxV());
    }
    private static void submitLine(PoseStack pose,SubmitNodeCollector collector,Outline.Geometry geometry,Outline.Segment line){
        float width=geometry.width();var delta=line.end().subtract(line.start());
        if(width<=0||delta.lengthSqr()<1e-12)return;
        float radius=width/2;Vec3 min,max;
        pose.pushPose();
        try{
            pose.translate(line.start());
            if(line.axisAligned()){
                min=new Vec3(Math.min(0,delta.x)-radius,Math.min(0,delta.y)-radius,Math.min(0,delta.z)-radius);
                max=new Vec3(Math.max(0,delta.x)+radius,Math.max(0,delta.y)+radius,Math.max(0,delta.z)+radius);
            }else{
                // Same float difference, yaw then pitch, local SOUTH cuboid as source LineOutline.
                float x=(float)delta.x,y=(float)delta.y,z=(float)delta.z;
                float length=Mth.sqrt(x*x+y*y+z*z),horizontal=Mth.sqrt(x*x+z*z);
                float yaw=(float)Math.toDegrees(Mth.atan2(x,z));
                float pitch=(float)Math.toDegrees(Mth.atan2(horizontal,y))-90;
                pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,pitch);
                min=new Vec3(-radius,-radius,-radius);max=new Vec3(radius,radius,length+radius);
            }
            var capturedMin=min;var capturedMax=max;
            collector.submitCustomGeometry(pose,RenderTypes.entitySolid(BLANK),(matrix,vertices)->emitCuboid(matrix,vertices,geometry,line,capturedMin,capturedMax));
        }finally{pose.popPose();}
    }
    private static void emitCuboid(PoseStack.Pose pose,VertexConsumer vertices,Outline.Geometry geometry,Outline.Segment line,Vec3 min,Vec3 max){
        var p=new Vec3[]{
            new Vec3(min.x,min.y,max.z),new Vec3(min.x,min.y,min.z),new Vec3(max.x,min.y,min.z),new Vec3(max.x,min.y,max.z),
            new Vec3(min.x,max.y,min.z),new Vec3(min.x,max.y,max.z),new Vec3(max.x,max.y,max.z),new Vec3(max.x,max.y,min.z)};
        int[][] faces={{0,1,2,3},{4,5,6,7},{7,2,1,4},{5,0,3,6},{4,1,0,5},{6,3,2,7}};
        Direction[] normals={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
        for(int i=0;i<faces.length;i++){
            var q=faces[i];var normal=geometry.disableNormals()?Direction.UP:normals[i];
            emitQuad(pose,vertices,p[q[0]],p[q[1]],p[q[2]],p[q[3]],normal.getUnitVec3(),geometry.color(),geometry.light(),1,1);
        }
    }
    private static void emitQuad(PoseStack.Pose pose,VertexConsumer vertices,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 normal,int color,int light,float maxU,float maxV){
        Vec3[] points={a,b,c,d};
        for(int i=0;i<points.length;i++){
            var point=points[i];vertices.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(color)
                .setUv(i>=2?maxU:0,i==1||i==2?maxV:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
        }
    }
    private OutlineRenderer(){}
}
