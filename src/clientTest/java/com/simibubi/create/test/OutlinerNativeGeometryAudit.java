package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.OutlineRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Observes real queued native callbacks and every original emitted vertex; never invokes/substitutes callbacks. */
public final class OutlinerNativeGeometryAudit {
    private static volatile Sample active;
    private static final ThreadLocal<ArrayDeque<Base>> BASES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Emission>> EMISSIONS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final float EPS=.0002f;
    private record Base(Outline.Geometry geometry,Outline.Segment line,Matrix4f pose) {}
    public record GroupReceipt(String role,int color,int lines,int faces,int actualDeferredVertices,int light,
        boolean disableNormals,boolean axisAligned,float[] firstNativePose) {}
    public record Receipt(List<GroupReceipt> groups,boolean everyNativeQueueReturned,boolean everyActualDeferredCallbackReturned) {}
    public static final class Sample {
        final int cluster,box,line;final int dimLight;
        final IdentityHashMap<Outline.Geometry,Group> groups=new IdentityHashMap<>();volatile Throwable failure;
        Sample(int cluster,int box,int line,int dimLight){this.cluster=cluster;this.box=box;this.line=line;this.dimLight=dimLight;}
        public Throwable failure(){return failure;}
        public synchronized Receipt completed(){
            var result=new ArrayList<GroupReceipt>();
            for(int color:new int[]{cluster,box,line}){
                var group=groups.values().stream().filter(g->(g.geometry.color()&0xFFFFFF)==color&&complete(g)).findFirst().orElse(null);
                if(group==null)return null;var geometry=group.geometry;
                result.add(new GroupReceipt(color==cluster?"source-cluster":color==box?"source-box-no-cull":"source-free-line",color,
                    (int)group.tokens.stream().filter(t->t.line!=null).count(),(int)group.tokens.stream().filter(t->t.face!=null).count(),
                    group.tokens.stream().mapToInt(t->t.vertices).sum(),geometry.light(),geometry.disableNormals(),color!=line,
                    group.tokens.getFirst().pose.get(new float[16])));
            }return new Receipt(List.copyOf(result),true,true);
        }
        private boolean complete(Group group){
            int color=group.geometry.color()&0xFFFFFF,lines=color==cluster?16:color==box?12:1,faces=color==cluster?10:color==box?6:0;
            return group.tokens.stream().filter(t->t.line!=null).count()==lines&&group.tokens.stream().filter(t->t.face!=null).count()==faces
                &&group.tokens.stream().allMatch(t->t.queued&&t.returned&&t.vertices==(t.line!=null?24:4));
        }
    }
    private static final class Group {
        final Sample sample;final Outline.Geometry geometry;final List<Token> tokens=new ArrayList<>();
        Group(Sample sample,Outline.Geometry geometry){this.sample=sample;this.geometry=geometry;}
    }
    public static final class Token {
        final Group group;final Outline.Face face;final Outline.Segment line;final Matrix4f pose;
        boolean queued,returned;int vertices;
        Token(Group group,Outline.Face face,Outline.Segment line,PoseStack pose){this.group=group;this.face=face;this.line=line;this.pose=new Matrix4f(pose.last().pose());}
    }
    private static final class Vertex {
        final float x,y,z;float u,v,nx,ny,nz;int color,light,overlay;boolean uvSet,colorSet,lightSet,overlaySet,normalSet;
        Vertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    }
    private static final class Emission {
        final Token token;final Vec3 min,max;final List<Vertex> vertices=new ArrayList<>();
        Emission(Token token,Vec3 min,Vec3 max){this.token=token;this.min=min;this.max=max;}
    }
    public static Sample begin(int cluster,int box,int line,int dimLight){var sample=new Sample(cluster,box,line,dimLight);active=sample;return sample;}
    public static void clear(){active=null;BASES.remove();EMISSIONS.remove();}
    private static Group group(Outline.Geometry geometry){
        var sample=active;if(sample==null)return null;int color=geometry.color()&0xFFFFFF;
        if(color!=sample.cluster&&color!=sample.box&&color!=sample.line)return null;
        synchronized(sample){var existing=sample.groups.get(geometry);if(existing!=null)return existing;if(sample.groups.size()>=256)return null;var next=new Group(sample,geometry);sample.groups.put(geometry,next);return next;}
    }
    public static void lineBase(PoseStack pose,Outline.Geometry geometry,Outline.Segment line){BASES.get().push(new Base(geometry,line,new Matrix4f(pose.last().pose())));}
    public static void lineBaseReturned(){if(!BASES.get().isEmpty())BASES.get().pop();}
    public static Token queued(PoseStack pose,Outline.Geometry geometry,Outline.Face face,Outline.Segment line,RenderType type){
        var group=group(geometry);if(group==null)return null;var sample=group.sample;
        synchronized(sample){
            var token=new Token(group,face,line,pose);group.tokens.add(token);
            check(sample,type==(face==null?RenderTypes.entitySolid(OutlineRenderer.BLANK):face.cull()?RenderTypes.entityTranslucentCull(face.texture()):RenderTypes.entityTranslucent(face.texture())),"Actual native outline pipeline/cull choice differs from pinned source");
            int color=geometry.color()&0xFFFFFF;check(sample,geometry.light()==(color==sample.cluster?LightCoordsUtil.FULL_BRIGHT:sample.dimLight)&&geometry.disableNormals()==(color!=sample.cluster),"Actual extracted lightmap/disableLineNormals must reach native geometry");
            if(face!=null)check(sample,face.cull()==(color==sample.cluster),"Actual test source cull/no-cull variants were not retained");
            if(line!=null){
                check(sample,line.axisAligned()==(color!=sample.line),"Actual source box/free-line basis metadata was lost");
                var base=BASES.get().peek();check(sample,base!=null&&base.geometry==geometry&&base.line==line,"Actual native line submission lost its genuine incoming source pose");
                if(base!=null){var expected=new Matrix4f(base.pose).translate((float)line.start().x,(float)line.start().y,(float)line.start().z);
                    if(!line.axisAligned()){var d=line.end().subtract(line.start());float x=(float)d.x,y=(float)d.y,z=(float)d.z;
                        float yaw=(float)Math.toDegrees(Mth.atan2(x,z)),pitch=(float)Math.toDegrees(Mth.atan2(Mth.sqrt(x*x+z*z),y))-90;
                        expected.rotateY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(pitch));}
                    check(sample,expected.equals(token.pose,EPS),"Actual native source line yaw/pitch/basis pose changed");}
            }return token;
        }
    }
    public static void queuedReturned(Token token){if(token!=null)synchronized(token.group.sample){token.queued=true;}}
    private static Token pending(Outline.Geometry geometry,Outline.Face face,Outline.Segment line){
        var group=group(geometry);if(group==null)return null;synchronized(group.sample){for(var token:group.tokens)if(!token.returned&&token.face==face&&token.line==line)return token;}return null;
    }
    public static void cuboid(PoseStack.Pose pose,Outline.Geometry geometry,Outline.Segment line,Vec3 min,Vec3 max){
        var token=pending(geometry,null,line);if(token==null)return;var sample=token.group.sample;
        synchronized(sample){check(sample,token.pose.equals(pose.pose(),EPS),"Actual deferred native line callback changed submitted pose");
            double radius=geometry.width()/2d;var delta=line.end().subtract(line.start());Vec3 expectedMin,expectedMax;
            if(line.axisAligned()){expectedMin=new Vec3(Math.min(0,delta.x)-radius,Math.min(0,delta.y)-radius,Math.min(0,delta.z)-radius);expectedMax=new Vec3(Math.max(0,delta.x)+radius,Math.max(0,delta.y)+radius,Math.max(0,delta.z)+radius);}
            else{float x=(float)delta.x,y=(float)delta.y,z=(float)delta.z;expectedMin=new Vec3(-radius,-radius,-radius);expectedMax=new Vec3(radius,radius,Mth.sqrt(x*x+y*y+z*z)+radius);}
            check(sample,min.distanceToSqr(expectedMin)<1e-10&&max.distanceToSqr(expectedMax)<1e-10,"Actual source cuboid must extend both endpoints by half the extracted line width");
        }EMISSIONS.get().push(new Emission(token,min,max));
    }
    public static void face(PoseStack.Pose pose,Outline.Geometry geometry,Outline.Face face){var token=pending(geometry,face,null);if(token==null)return;synchronized(token.group.sample){check(token.group.sample,token.pose.equals(pose.pose(),EPS),"Actual deferred native face callback changed submitted pose");}EMISSIONS.get().push(new Emission(token,null,null));}
    private static Emission emission(){var e=EMISSIONS.get().peek();return e;}
    private static Vertex last(){var e=emission();return e==null||e.vertices.isEmpty()?null:e.vertices.getLast();}
    public static void vertex(float x,float y,float z){var e=emission();if(e!=null)e.vertices.add(new Vertex(x,y,z));}
    public static void color(int value){var v=last();if(v!=null){v.color=value;v.colorSet=true;}}
    public static void uv(float u,float v){var row=last();if(row!=null){row.u=u;row.v=v;row.uvSet=true;}}
    public static void light(int value){var v=last();if(v!=null){v.light=value;v.lightSet=true;}}
    public static void overlay(int value){var v=last();if(v!=null){v.overlay=value;v.overlaySet=true;}}
    public static void normal(float x,float y,float z){var v=last();if(v!=null){v.nx=x;v.ny=y;v.nz=z;v.normalSet=true;}}
    public static void returned(Outline.Geometry geometry,Outline.Face face,Outline.Segment line){
        var stack=EMISSIONS.get();if(stack.isEmpty())return;var e=stack.peek();var token=e.token;
        if(token.group.geometry!=geometry||token.face!=face||token.line!=line)return;stack.pop();var sample=token.group.sample;
        synchronized(sample){
            boolean cuboid=line!=null;check(sample,e.vertices.size()==(cuboid?24:4),"Actual deferred source outline must emit six closed quad faces/24 vertices or one textured face/4 vertices");
            Direction[] normals={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
            for(int i=0;i<e.vertices.size();i++){
                var v=e.vertices.get(i);int corner=i%4;var expectedNormal=cuboid?(geometry.disableNormals()?Direction.UP:normals[Math.min(5,i/4)]).getUnitVec3():face.normal().getUnitVec3();
                float u=corner>=2?(cuboid?1:face.maxU()):0,w=corner==1||corner==2?(cuboid?1:face.maxV()):0;
                check(sample,v.colorSet&&v.lightSet&&v.overlaySet&&v.normalSet&&v.uvSet&&v.color==(cuboid?geometry.color():face.color())&&v.light==geometry.light()&&v.overlay==OverlayTexture.NO_OVERLAY,"Actual emitted source vertex lost color/lightmap/overlay/native attribute calls");
                check(sample,v.u==u&&v.v==w&&new Vec3(v.nx,v.ny,v.nz).distanceToSqr(expectedNormal)<1e-10,"Actual emitted source UV order or per-face/disabled normals changed");
                if(!cuboid){var expected=List.of(face.a(),face.b(),face.c(),face.d()).get(corner);check(sample,new Vec3(v.x,v.y,v.z).distanceToSqr(expected)<1e-9,"Actual emitted native face corner changed");}
                else{
                    check(sample,bound(v.x,e.min.x,e.max.x)&&bound(v.y,e.min.y,e.max.y)&&bound(v.z,e.min.z,e.max.z),"Actual native closed cuboid vertex left the source extended bounds");
                    boolean plane=switch(i/4){case 0->Math.abs(v.y-e.min.y)<EPS;case 1->Math.abs(v.y-e.max.y)<EPS;case 2->Math.abs(v.z-e.min.z)<EPS;case 3->Math.abs(v.z-e.max.z)<EPS;case 4->Math.abs(v.x-e.min.x)<EPS;default->Math.abs(v.x-e.max.x)<EPS;};
                    check(sample,plane,"Actual native source cuboid must close each distinct boundary plane, including both extended endpoint caps");
                }
            }
            if(cuboid&&e.vertices.size()==24)for(int i=0;i<6;i++){
                var a=point(e.vertices.get(i*4));var b=point(e.vertices.get(i*4+1));var c=point(e.vertices.get(i*4+2));
                check(sample,b.subtract(a).cross(c.subtract(b)).normalize().dot(normals[i].getUnitVec3())>.99999,"Actual closed cuboid source winding/end cap is missing or inward");
            }
            token.vertices=e.vertices.size();token.returned=true;
        }
    }
    private static boolean bound(float value,double min,double max){return Math.abs(value-min)<EPS||Math.abs(value-max)<EPS;}
    private static Vec3 point(Vertex v){return new Vec3(v.x,v.y,v.z);}
    private static void check(Sample sample,boolean valid,String message){if(!valid&&sample.failure==null)sample.failure=new AssertionError(message);}
    private OutlinerNativeGeometryAudit(){}
}
