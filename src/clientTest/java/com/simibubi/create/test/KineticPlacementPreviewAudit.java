package com.simibubi.create.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.placement.KineticPlacementPreview;
import java.util.ArrayList;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Only observes the actual production frame's unchanged native model submission and its return. */
public final class KineticPlacementPreviewAudit {
    private static volatile Sample active;
    public record Receipt(String phase,String source,String target,String state,String hand,String helper,int quads,
        float alpha,int animationTicks,float[] actualPose,boolean nativeSubmissionReturned) {}
    public static final class Sample {
        final String phase;final BlockPos source,target;final BlockState state;final InteractionHand hand;
        volatile Receipt receipt;volatile Throwable failure;
        Sample(String phase,BlockPos source,BlockPos target,BlockState state,InteractionHand hand){this.phase=phase;this.source=source;this.target=target;this.state=state;this.hand=hand;}
        public Receipt completed(){return receipt;}public Throwable failure(){return failure;}
    }
    public static Sample expect(String phase,BlockPos source,BlockPos target,BlockState state,InteractionHand hand){var sample=new Sample(phase,source,target,state,hand);active=sample;return sample;}
    public static void clear(){active=null;}
    public static void submitted(KineticPlacementPreview.Frame frame,PartialModelRenderState actual,PoseStack pose,Vec3 camera,int light,int overlay,int outline){
        var sample=active;if(sample==null||sample.receipt!=null||!frame.prediction().source().equals(sample.source)||!frame.prediction().target().equals(sample.target))return;
        try{
            var prediction=frame.prediction();require(prediction.state()==sample.state&&prediction.hand()==sample.hand,"Actual accepted native state/hand differs from the focused source scene");
            require(frame.animationTicks()==10&&frame.alpha()>=.26f&&frame.alpha()<=.563f,"Actual source breathing/fade alpha must reach native steady preview");
            require(actual.mesh==frame.mesh()&&actual.translucent&&!actual.isEmpty(),"Actual native translucent submission must consume the actual extracted production mesh");
            require(light==LightCoordsUtil.FULL_BRIGHT&&overlay==OverlayTexture.NO_OVERLAY&&outline==0,"Actual source ghost native fullbright/overlay/outline arguments changed");
            var p=sample.target;var expected=new Matrix4f().translate((float)(p.getX()-camera.x),(float)(p.getY()-camera.y),(float)(p.getZ()-camera.z))
                .translate(.5f,.5f,.5f).scale(.85f).translate(-.5f,-.5f,-.5f);
            require(expected.equals(pose.last().pose(),.0001f),"Actual source ghost native camera-relative center/.85 pose changed");
            var original=new PartialModelRenderState();AllPartialModels.extract(sample.state,original);
            require(original.mesh!=null&&original.mesh.size()==frame.mesh().size(),"Actual original baked block model must retain all ghost quads");
            var sourceVertices=new ArrayList<float[]>();var sourceColors=new ArrayList<Integer>();
            original.mesh.forEach(quad->{for(int i=0;i<4;i++){sourceVertices.add(new float[]{quad.x(i),quad.y(i),quad.z(i),quad.u(i),quad.v(i)});sourceColors.add(quad.color(i));}});
            int tint=Math.clamp(Math.round(frame.alpha()*255),0,255)<<24|0xFFFFFF;int[] index={0};
            frame.mesh().forEach(quad->{
                require(quad.chunkLayer()==ChunkSectionLayer.TRANSLUCENT&&quad.cullFace()==null&&quad.tintIndex()==-1,"Actual ghost material must use native translucent layer and all original faces");
                for(int i=0;i<4;i++){
                    int at=index[0]++;var before=sourceVertices.get(at);
                    require(quad.x(i)==before[0]&&quad.y(i)==before[1]&&quad.z(i)==before[2]&&quad.u(i)==before[3]&&quad.v(i)==before[4],"Actual source ghost modified original native model geometry/atlas UVs");
                    require(quad.color(i)==ARGB.multiply(sourceColors.get(at),tint)&&ARGB.alpha(quad.color(i))>0&&ARGB.alpha(quad.color(i))<255,"Actual native ghost vertex alpha/color must retain original model color and source breathing");
                }
            });
            require(index[0]==sourceVertices.size(),"Actual native source ghost changed vertex count");
            sample.receipt=new Receipt(sample.phase,prediction.source().toString(),prediction.target().toString(),prediction.state().toString(),prediction.hand().name(),prediction.helper().name(),frame.mesh().size(),frame.alpha(),frame.animationTicks(),pose.last().pose().get(new float[16]),true);
        }catch(Throwable failure){sample.failure=failure;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private KineticPlacementPreviewAudit(){}
}
