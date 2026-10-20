package com.simibubi.create.content.kinetics.mechanicalArm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;

/** Source arm joints, grips and held-item poses shared by the native submit renderer and scene checks. */
public final class ArmVisual {
    public static void root(PoseStack pose,boolean ceiling){pose.translate(.5,.5,.5);if(ceiling)pose.rotateDegrees(Axis.XP,180);}
    public static void base(PoseStack pose,float angle){pose.translate(0,4/16d,0);pose.rotateDegrees(Axis.YP,angle);}
    public static void lower(PoseStack pose,float angle){pose.translate(0,2/16d,0);pose.rotateDegrees(Axis.XP,angle+135);}
    public static void upper(PoseStack pose,float angle){pose.translate(0,0,-14/16d);pose.rotateDegrees(Axis.XP,angle-90);}
    public static void head(PoseStack pose,float angle){pose.translate(0,0,-15/16d);pose.rotateDegrees(Axis.XP,angle-45);}
    public static void grip(PoseStack pose,boolean hasItem,boolean block,int flip){pose.translate(0,-flip*(hasItem?block?3/16d:5/64d:1/16d),-6/16d);}
    public static void held(PoseStack pose,boolean block){pose.rotateDegrees(Axis.XP,90);pose.translate(0,block?-9/16d:-10/16d,0);float scale=block?.5f:.625f;pose.scale(scale,scale,scale);}
    public static void tint(PartialModelRenderState model,int argb){
        if(argb==0xFFFFFFFF||model.mesh==null)return;
        var tinted=Renderer.get().mutableMesh();var emitter=tinted.emitter();
        model.mesh.forEach(quad->emitter.copyFrom(quad).multiplyColor(argb).emit());model.mesh=tinted.immutableCopy();
    }
    /** Exact original Catnip Color.rainbowColor phases, driven by the native client world's tick clock. */
    public static int rainbow(int timeStep){
        int local=Math.abs(timeStep)%1536,progress=local%256,phase=local/256;
        return 0xFF000000|colorInPhase(phase+4,progress)<<16|colorInPhase(phase+2,progress)<<8|colorInPhase(phase,progress);
    }
    private static int colorInPhase(int phase,int progress){phase%=6;if(phase<=1)return 0;if(phase==2)return progress;if(phase<=4)return 255;return 255-progress;}
    private ArmVisual(){}
}
