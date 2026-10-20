package com.simibubi.create.content.schematics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.*;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Immutable actual checker faces and native line geometry, extracted from the selected bounds. */
public final class SchematicOutline {
    public record Face(List<Vec3> vertices, Direction normal, boolean highlighted, boolean highlightedTexture) {
        public Face(List<Vec3> vertices, Direction normal, boolean highlighted) { this(vertices, normal, highlighted, highlighted); }
    }
    public record Edge(Vec3 first, Vec3 second) {}
    public record Frame(AABB bounds, List<Face> faces, List<Edge> edges, int color) {
        public void submit(PoseStack pose, SubmitNodeCollector collector) {
            for (boolean highlight : new boolean[]{false, true}) {
                var selected = faces.stream().filter(face -> face.highlightedTexture == highlight).toList(); if (selected.isEmpty()) continue;
                collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(Create.asResource("textures/special/" + (highlight ? "highlighted_checkerboard" : "checkerboard") + ".png")), (matrix, vertices) -> {
                    for (var face : selected) for (int i=0;i<4;i++) {
                        var point=face.vertices.get(i); vertices.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z)
                            .setColor((color>>16)&255,(color>>8)&255,color&255,face.highlighted?180:75).setUv(i==1||i==2?1:0,i>=2?1:0)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                            .setNormal(matrix,face.normal.getStepX(),face.normal.getStepY(),face.normal.getStepZ());
                    }
                });
            }
            collector.submitShapeOutline(pose, net.minecraft.world.phys.shapes.Shapes.create(bounds), RenderTypes.lines(), color | 0xFF000000, 1.5f, false);
        }
    }
    public static Frame extract(AABB bounds, Direction selected, int color) {
        var faces=new ArrayList<Face>();var edges=new ArrayList<Edge>();
        for(var side:Direction.values()) {
            var normal=side.getUnitVec3();var u=side.getAxis()==Direction.Axis.X?Direction.SOUTH.getUnitVec3():Direction.EAST.getUnitVec3();var v=normal.cross(u);
            double halfU=(u.x!=0?bounds.getXsize():bounds.getZsize())*.5;
            double halfV=(v.y!=0?bounds.getYsize():v.x!=0?bounds.getXsize():bounds.getZsize())*.5;
            double halfN=(normal.x!=0?bounds.getXsize():normal.y!=0?bounds.getYsize():bounds.getZsize())*.5;
            var center=bounds.getCenter().add(normal.scale(halfN+.002));var a=center.subtract(u.scale(halfU)).subtract(v.scale(halfV));var b=center.add(u.scale(halfU)).subtract(v.scale(halfV));var c=center.add(u.scale(halfU)).add(v.scale(halfV));var d=center.subtract(u.scale(halfU)).add(v.scale(halfV));
            faces.add(new Face(List.of(a,b,c,d),side,side==selected));
            edges.add(new Edge(a,b));edges.add(new Edge(b,c));edges.add(new Edge(c,d));edges.add(new Edge(d,a));
        }
        return new Frame(bounds,List.copyOf(faces),List.copyOf(edges),color);
    }
    /** Selected face geometry remains available; only its source Ctrl texture is conditional. */
    public static Frame extract(AABB bounds, Direction selected, int color, boolean highlightedTexture) {
        var frame = extract(bounds, selected, color);
        return new Frame(frame.bounds, frame.faces.stream().map(face -> new Face(face.vertices, face.normal,
            face.highlighted, face.highlighted && highlightedTexture)).toList(), frame.edges, frame.color);
    }
    private SchematicOutline() {}
}
