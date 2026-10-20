package com.simibubi.create.content.schematics.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicOutline;
import java.util.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

/** Extracted native tool geometry: submission never reads a tool, player, schematic or live world. */
public record SchematicToolRenderState(List<Outline> outlines,List<Line> lines) {
    public enum Role {BOUNDS,DEPLOYMENT,ROTATION_AXIS,MIRROR_PLANE,OTHER}
    public record Outline(Role role,Matrix4fc transform,SchematicOutline.Frame geometry){}
    public record Line(Role role,SchematicOutline.Edge geometry,int color,float width){}
    public SchematicToolRenderState{outlines=List.copyOf(outlines);lines=List.copyOf(lines);}
    public Outline outline(Role role){return outlines.stream().filter(outline->outline.role==role).findFirst().orElse(null);}
    public Line line(Role role){return lines.stream().filter(line->line.role==role).findFirst().orElse(null);}
    public void submit(SubmitNodeCollector collector,CameraRenderState camera){
        for(var outline:outlines){var pose=new PoseStack();pose.mulPose(outline.transform);outline.geometry.submit(pose,collector);}
        var nativeLines=new DrawableGizmoPrimitives();for(var line:lines)nativeLines.addLine(line.geometry.first(),line.geometry.second(),line.color,line.width);if(!nativeLines.isEmpty())nativeLines.submit(collector,camera,false);
    }
    public static final class Builder {
        private final List<Outline> outlines=new ArrayList<>();private final List<Line> lines=new ArrayList<>();
        public void addOutline(Role role,PoseStack pose,SchematicOutline.Frame geometry){outlines.add(new Outline(Objects.requireNonNull(role),new Matrix4f(pose.last().pose()),Objects.requireNonNull(geometry)));}
        public void addLine(Role role,PoseStack pose,Vec3 camera,Vec3 first,Vec3 second,int color,float width){
            if(!Float.isFinite(width)||width<=0)throw new IllegalArgumentException("Native tool line needs positive finite width");
            lines.add(new Line(Objects.requireNonNull(role),new SchematicOutline.Edge(world(pose,camera,first),world(pose,camera,second)),color,width));
        }
        private static Vec3 world(PoseStack pose,Vec3 camera,Vec3 point){var transformed=pose.last().pose().transformPosition(new Vector3f((float)point.x,(float)point.y,(float)point.z));return new Vec3(transformed.x,transformed.y,transformed.z).add(camera);}
        public SchematicToolRenderState build(){return new SchematicToolRenderState(outlines,lines);}
    }
}
