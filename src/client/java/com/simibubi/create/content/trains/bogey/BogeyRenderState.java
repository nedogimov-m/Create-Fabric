package com.simibubi.create.content.trains.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.*;

/** Detached source bogey parts. No world, BE, mutable style data or model-manager reference is retained. */
public final class BogeyRenderState {
    public record Part(String name,PartialModelRenderState model,Matrix4fc transform){}
    public final List<Part> parts=new ArrayList<>();
    public float wheelAngle,pistonOffset,beltScroll;
    public void clear(){parts.clear();wheelAngle=0;pistonOffset=0;beltScroll=0;}
    public void add(String name,PartialModelRenderState model,Matrix4fc transform){parts.add(new Part(name,model,new Matrix4f(transform)));}
    public void submit(PoseStack pose,SubmitNodeCollector collector,int light,int overlay,int outline){
        for(var part:parts){pose.pushPose();pose.mulPose(part.transform());part.model().submit(pose,collector,light,overlay,outline);pose.popPose();}
    }
}
