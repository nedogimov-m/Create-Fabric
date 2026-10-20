package com.simibubi.create.content.kinetics.mechanicalArm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Native Fabric meshes and item submissions; RenderPearl selects the actual Vulkan device. */
public final class ArmRenderer implements BlockEntityRenderer<ArmBlockEntity,ArmRenderer.State> {
    private final ItemModelResolver items;
    public ArmRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState cog=new PartialModelRenderState(),base=new PartialModelRenderState(),lower=new PartialModelRenderState(),upper=new PartialModelRenderState(),claw=new PartialModelRenderState(),upperGrip=new PartialModelRenderState(),lowerGrip=new PartialModelRenderState();
        public final ItemStackRenderState held=new ItemStackRenderState();
        public float cogAngle,baseAngle,lowerAngle,upperAngle,headAngle;
        public boolean ceiling,goggles,hasItem,blockItem,dancing;
        public int color;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ArmBlockEntity arm,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(arm,state,partial,camera,overlay);
        state.ceiling=arm.isOnCeiling();state.goggles=arm.hasGoggles();state.cogAngle=arm.getAngle(partial)+((arm.getBlockPos().getX()+arm.getBlockPos().getZ())%2==0?22.5f:0);
        state.held.clear();var item=arm.getHeldItem();state.hasItem=!item.isEmpty();
        if(state.hasItem)items.updateForTopItem(state.held,item,ItemDisplayContext.FIXED,arm.getLevel(),null,0);
        state.blockItem=state.hasItem&&item.getItem() instanceof BlockItem&&state.held.usesBlockLight();
        state.dancing=arm.getPhase()==ArmBlockEntity.Phase.DANCING&&arm.getSpeed()!=0;state.color=0xFFFFFFFF;
        if(state.dancing){
            float time=arm.getLevel().getGameTime()+partial+arm.hashCode()%64;
            state.baseAngle=time*10%360;state.lowerAngle=Mth.lerp((Mth.sin(time/4)+1)/2,-45,15);
            state.upperAngle=Mth.lerp((Mth.sin(time/8)+1)/4,-45,95);state.headAngle=-state.lowerAngle;state.color=ArmVisual.rainbow((int)(arm.getLevel().getGameTime()%1_728_000)*100);
        }else{var angles=arm.angles(partial);state.baseAngle=angles[0];state.lowerAngle=angles[1]-135;state.upperAngle=angles[2]-90;state.headAngle=angles[3];}
        AllPartialModels.extract(AllPartialModels.ARM_COG,state.cog);AllPartialModels.extract(AllPartialModels.ARM_BASE,state.base);
        AllPartialModels.extract(AllPartialModels.ARM_LOWER_BODY,state.lower);AllPartialModels.extract(AllPartialModels.ARM_UPPER_BODY,state.upper);
        AllPartialModels.extract(state.goggles?AllPartialModels.ARM_CLAW_BASE_GOGGLES:AllPartialModels.ARM_CLAW_BASE,state.claw);
        AllPartialModels.extract(AllPartialModels.ARM_CLAW_GRIP_UPPER,state.upperGrip);AllPartialModels.extract(AllPartialModels.ARM_CLAW_GRIP_LOWER,state.lowerGrip);
        ArmVisual.tint(state.lower,state.color);ArmVisual.tint(state.upper,state.color);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.cogAngle);pose.translate(-.5,-.5,-.5);state.cog.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();ArmVisual.root(pose,state.ceiling);ArmVisual.base(pose,state.baseAngle);state.base.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        ArmVisual.lower(pose,state.lowerAngle);state.lower.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        ArmVisual.upper(pose,state.upperAngle);state.upper.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        ArmVisual.head(pose,state.headAngle);
        pose.pushPose();if(state.ceiling&&state.goggles)pose.rotateDegrees(Axis.ZP,180);state.claw.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        for(int flip:new int[]{1,-1}){pose.pushPose();ArmVisual.grip(pose,state.hasItem,state.blockItem,flip);(flip>0?state.lowerGrip:state.upperGrip).submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        if(state.hasItem){pose.pushPose();ArmVisual.held(pose,state.blockItem);state.held.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
}
