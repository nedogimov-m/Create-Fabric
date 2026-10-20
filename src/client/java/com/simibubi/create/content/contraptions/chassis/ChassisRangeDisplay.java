package com.simibubi.create.content.contraptions.chassis;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.*;

/** Immutable native checker faces and cluster edges; no BE/world lookup occurs during submit. */
public final class ChassisRangeDisplay {
    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Direction normal){}
    private record Edge(Vec3 a,Vec3 b){}
    public record Frame(BlockPos origin,Set<BlockPos> selected,boolean bulk,boolean complete,int lines,int faces,DrawableGizmoPrimitives primitives,List<Face> surfaces){}
    private record Entry(BlockPos pos,boolean bulk,List<ChassisBlockEntity> members,Set<BlockPos> positions,boolean complete){}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static ClientLevel level;
    private static Entry entry;
    private static int timer;
    private static Frame latest;
    public static Frame latest(){return latest;}
    private static boolean wrench(Minecraft client){return client.player!=null&&client.player.getMainHandItem().is(AllItems.WRENCH);}
    public static void clear(){if(entry!=null)for(var member:entry.members)member.currentlySelectedRange=member.getRange();entry=null;timer=0;latest=null;FRAMES.clear();}
    public static void show(BlockPos pos,boolean bulk,Integer preview){
        var client=Minecraft.getInstance();if(client.level==null||!(LoadedLevelAccess.blockEntity(client.level,pos) instanceof ChassisBlockEntity chassis))return;
        if(entry!=null)for(var member:entry.members)member.currentlySelectedRange=member.getRange();
        try{
            var members=bulk?chassis.collectChassisGroup():List.of(chassis);var positions=new LinkedHashSet<BlockPos>();
            for(var member:members)positions.addAll(ChassisAttachmentLogic.included(member,null,preview==null?member.getRange():Math.clamp(preview,1,Math.max(1,ChassisSettings.maximumForCurrentSide()))));
            for(var member:members)member.currentlySelectedRange=preview==null?member.getRange():Math.clamp(preview,1,Math.max(1,ChassisSettings.maximumForCurrentSide()));
            entry=new Entry(pos.immutable(),bulk,List.copyOf(members),Set.copyOf(positions),true);timer=200;
        }catch(AssemblyException unavailable){entry=new Entry(pos.immutable(),bulk,List.of(chassis),Set.of(),false);timer=200;}
    }
    private static void tick(Minecraft client){
        if(client.level!=level){clear();level=client.level;}if(level==null||client.player==null)return;
        if(entry!=null){for(var member:entry.members)if(member.isRemoved()||member.getLevel()!=level||!LoadedLevelAccess.isLoaded(level,member.getBlockPos())){clear();break;}}
        if(entry!=null){if(!wrench(client))timer=Math.min(timer,20);if(--timer<=0)clear();}
        if(!wrench(client)||client.gui.screen()!=null||!(client.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return;
        if(LoadedLevelAccess.blockEntity(level,hit.getBlockPos()) instanceof ChassisBlockEntity)show(hit.getBlockPos(),com.simibubi.create.AllKeys.ctrlDown(),null);
    }
    private static int compare(Vec3 first,Vec3 second){int x=Double.compare(first.x,second.x);if(x!=0)return x;int y=Double.compare(first.y,second.y);return y!=0?y:Double.compare(first.z,second.z);}
    private static void edge(Map<Edge,List<Direction>> edges,Vec3 first,Vec3 second,Direction normal){var edge=compare(first,second)<0?new Edge(first,second):new Edge(second,first);edges.computeIfAbsent(edge,key->new ArrayList<>()).add(normal);}
    private static Frame extract(){
        if(entry==null)return new Frame(BlockPos.ZERO,Set.of(),false,true,0,0,new DrawableGizmoPrimitives(),List.of());
        var origin=entry.pos;var surfaces=new ArrayList<Face>();var edges=new LinkedHashMap<Edge,List<Direction>>();var output=new DrawableGizmoPrimitives();
        for(var pos:entry.positions)for(var side:Direction.values()){
            if(entry.positions.contains(pos.relative(side)))continue;
            var normal=side.getUnitVec3();var u=side.getAxis()==Direction.Axis.X?Direction.SOUTH.getUnitVec3():Direction.EAST.getUnitVec3();var v=normal.cross(u);var center=Vec3.atCenterOf(pos).add(normal.scale(.5));
            var a=center.subtract(u.scale(.5)).subtract(v.scale(.5));var b=center.add(u.scale(.5)).subtract(v.scale(.5));var c=center.add(u.scale(.5)).add(v.scale(.5));var d=center.subtract(u.scale(.5)).add(v.scale(.5));
            edge(edges,a,b,side);edge(edges,b,c,side);edge(edges,c,d,side);edge(edges,d,a,side);
            var shift=normal.scale(1/512d).subtract(Vec3.atLowerCornerOf(origin));surfaces.add(new Face(a.add(shift),b.add(shift),c.add(shift),d.add(shift),side));
        }
        int lines=0;for(var item:edges.entrySet()){var normals=item.getValue();if(normals.size()==2&&normals.get(0)==normals.get(1))continue;output.addLine(item.getKey().a,item.getKey().b,0xFFFFFFFF,2);lines++;}
        return new Frame(origin,entry.positions,entry.bulk,entry.complete,lines,surfaces.size(),output,List.copyOf(surfaces));
    }
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(ChassisRangeDisplay::tick);ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();level=null;});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract();FRAMES.put(context.levelState(),frame);latest=frame;});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            var frame=FRAMES.get(context.levelState());if(frame==null)return;frame.primitives.submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);if(frame.surfaces.isEmpty())return;
            var camera=context.levelState().cameraRenderState.pos;var pose=new PoseStack();pose.translate(frame.origin.getX()-camera.x,frame.origin.getY()-camera.y,frame.origin.getZ()-camera.z);
            context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.entityTranslucent(Create.asResource("textures/special/highlighted_checkerboard.png")),(matrix,vertices)->{
                for(var face:frame.surfaces){var points=List.of(face.a,face.b,face.c,face.d);for(int i=0;i<4;i++){var point=points.get(i);vertices.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z).setColor(255,255,255,150).setUv(i==1||i==2?1:0,i>=2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(matrix,face.normal.getStepX(),face.normal.getStepY(),face.normal.getStepZ());}}
            });
        });
    }
    private ChassisRangeDisplay(){}
}
