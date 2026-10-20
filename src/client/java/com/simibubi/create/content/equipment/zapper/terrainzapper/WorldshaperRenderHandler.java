package com.simibubi.create.content.equipment.zapper.terrainzapper;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.zapper.*;
import com.simibubi.create.content.schematics.client.SchematicOutline;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;

/** Source cluster faces and boundary edges, captured without retaining mutable brush/world owners. */
public class WorldshaperRenderHandler {
    private static Collection<BlockPos> renderedPositions=List.of();
    public static void tick(){gatherSelectedBlocks();}
    protected static void gatherSelectedBlocks(){
        var player=Minecraft.getInstance().player;
        if(player==null){clear();return;}
        var main=player.getMainHandItem();var off=player.getOffhandItem();
        if(main.is(AllItems.WORLDSHAPER)&&(!main.has(AllDataComponents.SHAPER_SWAP)||!off.is(AllItems.WORLDSHAPER)))createBrushOutline(player,main);
        else if(off.is(AllItems.WORLDSHAPER))createBrushOutline(player,off);else clear();
    }
    public static void createBrushOutline(LocalPlayer player,ItemStack stack){
        var params=stack.get(AllDataComponents.SHAPER_BRUSH_PARAMS);
        var kind=stack.getOrDefault(AllDataComponents.SHAPER_BRUSH,TerrainBrushes.Cuboid);
        var tool=stack.getOrDefault(AllDataComponents.SHAPER_TOOL,TerrainTools.Fill);
        if(params==null||!WorldshaperItem.validSettings(kind,params,tool)){clear();return;}
        var hit=ZapperWorldAccess.clip(player,128);
        if(hit.getType()==HitResult.Type.MISS){clear();return;}
        var brush=kind.get();brush.set(params.getX(),params.getY(),params.getZ());
        var pos=hit.getBlockPos().offset(brush.getOffset(player.getLookAngle(),hit.getDirection(),stack.getOrDefault(AllDataComponents.SHAPER_PLACEMENT_OPTIONS,PlacementOptions.Merged)));
        var result=new ArrayList<BlockPos>();brush.addToGlobalPositions(player.level(),pos,hit.getDirection(),result,tool);
        result.removeIf(at->!ZapperWorldAccess.loaded(player.level(),at));renderedPositions=List.copyOf(result);
    }
    public record Edge(Vec3 start,Vec3 end){}
    public record Frame(List<SchematicOutline.Face> faces,List<Edge> edges,List<ZapperRenderHandler.Beam> beams,int blocks){
        public void submit(PoseStack pose,SubmitNodeCollector collector){
            if(!faces.isEmpty())collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(Create.asResource("textures/special/checkerboard.png")),(matrix,vertices)->{
                for(var face:faces)for(int i=0;i<4;i++){var point=face.vertices().get(i);vertices.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z).setColor(191,191,191,75).setUv(i==1||i==2?1:0,i>=2?1:0).setLight(LightCoordsUtil.FULL_BRIGHT).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(matrix,face.normal().getStepX(),face.normal().getStepY(),face.normal().getStepZ());}
            });
            for(var edge:edges)tube(pose,collector,edge.start,edge.end,1/64f,0xFFBFBFBF);
            for(var beam:beams)tube(pose,collector,beam.start().lerp(beam.end(),1-beam.intensity()),beam.end(),beam.intensity()/16,0xFFFFFFFF);
        }
    }
    private static void tube(PoseStack pose,SubmitNodeCollector collector,Vec3 start,Vec3 end,float radius,int color){
        var delta=end.subtract(start);if(delta.lengthSqr()<1e-12)return;
        var axis=delta.normalize();var helper=Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0);
        var u=axis.cross(helper).normalize().scale(radius);var v=axis.cross(u).normalize().scale(radius);
        var points=List.of(u.add(v),u.subtract(v),u.scale(-1).subtract(v),v.subtract(u));
        collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,vertices)->{
            for(int side=0;side<4;side++){var a=points.get(side);var b=points.get((side+1)%4);for(var point:List.of(start.add(a),start.add(b),end.add(b),end.add(a)))vertices.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z).setColor(color);}
        });
    }
    private static double latticeCoordinate(double coordinate){
        double rounded=Math.rint(coordinate);
        // Vec3 keys distinguish signed zero; overlay offsets must share one lattice vertex.
        return rounded==0?0:rounded;
    }
    private static Vec3 lattice(Vec3 point){return new Vec3(latticeCoordinate(point.x),latticeCoordinate(point.y),latticeCoordinate(point.z));}
    private static int compare(Vec3 a,Vec3 b){int x=Double.compare(a.x,b.x);if(x!=0)return x;int y=Double.compare(a.y,b.y);return y!=0?y:Double.compare(a.z,b.z);}
    public static Frame extract(List<ZapperRenderHandler.Beam> beams){return extract(renderedPositions,beams);}
    public static Frame extract(Collection<BlockPos> selected,List<ZapperRenderHandler.Beam> beams){
        var positions=new HashSet<>(selected);var faces=new ArrayList<SchematicOutline.Face>();var boundaries=new LinkedHashMap<Edge,List<Direction>>();
        for(var pos:positions.stream().sorted().toList()){
            for(var face:SchematicOutline.extract(new AABB(pos),null,0xBFBFBF).faces())if(!positions.contains(pos.relative(face.normal()))){
                faces.add(face);for(int i=0;i<4;i++){var a=lattice(face.vertices().get(i));var b=lattice(face.vertices().get((i+1)%4));var edge=compare(a,b)<0?new Edge(a,b):new Edge(b,a);boundaries.computeIfAbsent(edge,key->new ArrayList<>()).add(face.normal());}
            }
        }
        var edges=boundaries.entrySet().stream().filter(entry->entry.getValue().size()!=2||entry.getValue().get(0)!=entry.getValue().get(1)).map(Map.Entry::getKey).toList();
        return new Frame(List.copyOf(faces),List.copyOf(edges),List.copyOf(beams),positions.size());
    }
    public static void clear(){renderedPositions=List.of();}
}
