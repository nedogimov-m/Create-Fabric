package com.simibubi.create.test;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperRenderHandler;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
/** Read-only receipts from real production submission and actual native custom-geometry execution. */
public final class WorldshaperNativeProbe {
    private static volatile boolean active;
    private static String label;private static long frames,previewBlocks,faces,edges,beams,draws,meshDraws,emissiveQuads,opaqueGlows,translucentGlows,customCallbacks,vertices,invalid;
    public static void begin(String name){label=name;frames=previewBlocks=faces=edges=beams=draws=meshDraws=emissiveQuads=opaqueGlows=translucentGlows=customCallbacks=vertices=invalid=0;active=true;}
    public static void frame(WorldshaperRenderHandler.Frame frame){if(!active)return;frames++;previewBlocks+=frame.blocks();faces+=frame.faces().size();edges+=frame.edges().size();beams+=frame.beams().size();}
    public static void draw(PoseStack pose,ItemDisplayContext display,ItemQuads quads){if(active){draws++;EquipmentNativeFrameProbe.draw(pose,display,quads);}}
    public static void mesh(PoseStack pose,MeshView mesh,int light){if(!active)return;meshDraws++;mesh.forEach(quad->{if(quad.emissive())emissiveQuads++;else invalid++;if(quad.itemRenderType()==null)invalid++;else if(quad.itemRenderType()==net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS))translucentGlows++;else opaqueGlows++;for(int i=0;i<4;i++){var point=pose.last().pose().transformPosition(quad.copyPos(i,new org.joml.Vector3f()));if(!point.isFinite())invalid++;}});}
    public static SubmitNodeCollector.CustomGeometryRenderer geometry(SubmitNodeCollector.CustomGeometryRenderer original){if(!active)return original;return (pose,vertices)->{if(!active){original.render(pose,vertices);return;}customCallbacks++;original.render(pose,new Counting(vertices));};}
    private record Counting(VertexConsumer delegate) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x,float y,float z){vertices++;if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))invalid++;delegate.addVertex(x,y,z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){delegate.setColor(r,g,b,a);return this;}
        @Override public VertexConsumer setColor(int color){delegate.setColor(color);return this;}
        @Override public VertexConsumer setUv(float u,float v){if(!Float.isFinite(u)||!Float.isFinite(v))invalid++;delegate.setUv(u,v);return this;}
        @Override public VertexConsumer setUv1(int a,int b){delegate.setUv1(a,b);return this;}
        @Override public VertexConsumer setUv2(int a,int b){delegate.setUv2(a,b);return this;}
        @Override public VertexConsumer setUv3(float a,float b){delegate.setUv3(a,b);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){delegate.setNormal(x,y,z);return this;}
        @Override public VertexConsumer setLineWidth(float width){delegate.setLineWidth(width);return this;}
    }
    public static void end(boolean requirePreview,boolean requireItem,boolean requireBeam){active=false;var receipt=new LinkedHashMap<String,Object>();receipt.put("scene",label);receipt.put("actualFrameSubmits",frames);receipt.put("actualPreviewBlocks",previewBlocks);receipt.put("actualFaces",faces);receipt.put("actualBoundaryEdges",edges);receipt.put("actualBeams",beams);receipt.put("actualSourceItemDraws",draws);receipt.put("actualEmissiveMeshDraws",meshDraws);receipt.put("actualEmissiveQuads",emissiveQuads);receipt.put("actualOpaqueGlows",opaqueGlows);receipt.put("actualTranslucentGlows",translucentGlows);receipt.put("actualNativeCustomCallbacks",customCallbacks);receipt.put("actualNativeVertices",vertices);receipt.put("invalid",invalid);System.out.println("CREATE_WORLDSHAPER_NATIVE_SUBMISSION "+new Gson().toJson(receipt));if(invalid!=0||requirePreview&&(previewBlocks==0||faces==0||edges==0||customCallbacks==0||vertices==0)||requireItem&&(draws==0||meshDraws<2||emissiveQuads==0||opaqueGlows==0||translucentGlows==0)||requireBeam&&beams==0)throw new AssertionError("Actual native Worldshaper submission is incomplete: "+receipt);}
    private WorldshaperNativeProbe(){}
}
