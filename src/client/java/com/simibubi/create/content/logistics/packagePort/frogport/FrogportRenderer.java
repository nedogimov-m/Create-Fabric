package com.simibubi.create.content.logistics.packagePort.frogport;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.*;

/** Tongue, head and parcel transforms are extracted through the native 26.3 render-state API. */
public final class FrogportRenderer implements BlockEntityRenderer<FrogportBlockEntity,FrogportRenderer.State>{
    public static final class State extends BlockEntityRenderState{
        public final PartialModelRenderState body=new PartialModelRenderState(),head=new PartialModelRenderState(),tongue=new PartialModelRenderState(),parcel=new PartialModelRenderState(),rig=new PartialModelRenderState();
        public float yaw,headPitch,tonguePitch,tongueLength,packageScale;
        public Vec3 packageOffset=Vec3.ZERO;
        public boolean showPackage,showRig;
        public Component name;
    }
    public FrogportRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 128;}
    @Override public void extractRenderState(FrogportBlockEntity port,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(port,state,partial,camera,overlay);state.yaw=port.getYaw();state.showPackage=state.showRig=false;state.parcel.clear();state.rig.clear();
        AllPartialModels.extract(AllPartialModels.FROGPORT_BODY,state.body);AllPartialModels.extract(port.goggles?AllPartialModels.FROGPORT_HEAD_GOGGLES:AllPartialModels.FROGPORT_HEAD,state.head);AllPartialModels.extract(AllPartialModels.FROGPORT_TONGUE,state.tongue);
        state.name=Minecraft.getInstance().hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(port.getBlockPos())&&!port.addressFilter.isBlank()?Component.literal(port.addressFilter):null;
        float headPitch=80,tonguePitch=0,length=0,modifier=1,itemDistance=0,scale=1;Vec3 diff=Vec3.ZERO;boolean active=port.isAnimationInProgress(),deposit=port.currentlyDepositing;
        var target=port.target==null?Vec3.ZERO:port.target.getExactTargetLocation(port);
        if(!target.equals(Vec3.ZERO)){diff=target.subtract(0,active&&deposit?0:.75,0).subtract(Vec3.atCenterOf(port.getBlockPos()));tonguePitch=(float)Math.toDegrees(Math.atan2(diff.y,Math.hypot(diff.x,diff.z)+3/16f));length=Math.max((float)diff.length(),1);headPitch=Mth.clamp(tonguePitch*2,60,100);}
        if(active){float progress=port.animationProgress(partial);
            if(deposit){double impulse=Math.max(0,1-Math.pow((progress-.25)*4-1,4));itemDistance=(float)Math.max(length*Math.min(1,(progress-.25)*3),length*impulse);length*=Math.max(0,1-Math.pow((progress*1.25-.25)*4-1,4));modifier=(float)Math.max(0,1-Math.pow(progress*1.25*2-1,4));scale=.25f+progress*.75f;}
            else{length*=Math.pow(Math.max(0,1-progress*1.25),5);modifier=1-(float)Math.min(1,Math.max(0,(Math.pow(progress*1.5,2)-.5)*2));scale=(float)Math.max(.5,1-progress*1.25);itemDistance=length;}
            var key=PackageRenderer.model(port.displayPackage);if(key!=null&&scale>=.45f){state.showPackage=true;AllPartialModels.extract(key,state.parcel);if(deposit){var rig=ChainConveyorRenderer.rigging(port.displayPackage);if(rig!=null){AllPartialModels.extract(rig,state.rig);state.showRig=true;}}}
        }else{length=0;float anticipation=port.anticipationProgress(partial);modifier=anticipation>0?(float)Math.max(0,1-Math.pow(anticipation*1.25*2-1,4)):0;}
        state.headPitch=Math.max(headPitch*modifier,port.manualOpenProgress(partial)*60);state.tonguePitch=tonguePitch;state.tongueLength=Math.max(length,port.manualOpenProgress(partial)*.25f);state.packageScale=scale;state.packageOffset=diff.normalize().scale(itemDistance).add(0,3/16d-(active&&deposit?.75:0),0);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(-.5,-.5,-.5);state.body.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.pushPose();pose.translate(8/16d,10/16d,11/16d);pose.rotateDegrees(Axis.XP,state.headPitch);pose.translate(-8/16d,-10/16d,-11/16d);state.head.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        if(state.tongueLength>0){pose.pushPose();pose.translate(8/16d,10/16d,11/16d);pose.rotateDegrees(Axis.XP,state.tonguePitch);pose.scale(1,1,state.tongueLength/(7/16f));pose.translate(-8/16d,-10/16d,-11/16d);state.tongue.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}pose.popPose();
        if(state.showPackage){pose.pushPose();pose.translate(state.packageOffset.x,state.packageOffset.y,state.packageOffset.z);pose.translate(.5,.5,.5);pose.scale(state.packageScale,state.packageScale,state.packageScale);pose.translate(-.5,-.5,-.5);state.parcel.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);if(state.showRig)state.rig.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        if(state.name!=null)collector.submitNameTag(pose,new Vec3(.5,1.1,.5),0,state.name,true,state.lightCoords,camera);
    }
}
