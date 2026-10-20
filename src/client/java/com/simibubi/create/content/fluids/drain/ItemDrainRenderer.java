package com.simibubi.create.content.fluids.drain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.foundation.fluid.FluidBoxRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class ItemDrainRenderer implements BlockEntityRenderer<ItemDrainBlockEntity,ItemDrainRenderer.State> {
    private final ItemModelResolver items;
    public ItemDrainRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {
        public final ItemStackRenderState item=new ItemStackRenderState();
        public final FluidBoxRenderState tank=new FluidBoxRenderState(),stream=new FluidBoxRenderState();
        public float offset,angle,yaw;
        public Direction direction;
        public boolean upright,blockItem;
        public int count;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ItemDrainBlockEntity drain,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(drain,state,partial,camera,overlay);state.tank.clear();state.stream.clear();state.item.clear();
        var fluid=drain.tank.getResource();float fill=drain.getFluidLevel(partial);
        if(!fluid.isBlank()&&fill>0)state.tank.extract(fluid,drain.getLevel(),drain.getBlockPos(),state.lightCoords,2f/16,5f/16,2f/16,14f/16,5f/16+7f/16*fill,14f/16,false);
        var held=drain.getHeldItem();if(held==null)return;
        var stack=held.stack;items.updateForTopItem(state.item,stack,ItemDisplayContext.FIXED,drain.getLevel(),null,0);
        state.upright=BeltHelper.isItemUpright(stack);state.blockItem=state.item.usesBlockLight();state.count=(31-Integer.numberOfLeadingZeros(stack.getCount()))/2;
        state.direction=held.insertedFrom;state.offset=held.prevBeltPosition+(held.beltPosition-held.prevBeltPosition)*partial;
        state.angle=state.direction.getAxisDirection().getStep()*state.offset*360;
        var view=Vec3.atCenterOf(drain.getBlockPos()).add(state.direction.getStepX()*(state.offset-0.5),0,state.direction.getStepZ()*(state.offset-0.5)).subtract(camera);
        float radians=(float)Math.toRadians(state.angle);
        if(state.direction.getAxis()!=Direction.Axis.X)view=view.xRot(-radians);
        if(state.direction.getAxis()!=Direction.Axis.Z)view=view.zRot(radians);
        state.yaw=(float)Math.toDegrees(Math.atan2(view.z,-view.x)-Math.PI/2);
        var processing=drain.getProcessingFluid();if(processing.isEmpty())return;
        float time=drain.processingTicks-partial,progress=Math.clamp(1-(time-5)/10,0,1),radius=1-(2*progress-1)*(2*progress-1);
        if(drain.processingTicks>0&&radius>0)state.stream.extract(processing.variant(),drain.getLevel(),drain.getBlockPos(),state.lightCoords,0.5f-radius/32,0.25f,0.5f-radius/32,0.5f+radius/32,1,0.5f+radius/32,true);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        state.tank.submit(pose,collector);state.stream.submit(pose,collector);if(state.item.isEmpty())return;
        pose.pushPose();pose.translate(0.5+state.direction.getStepX()*(state.offset-0.5),15f/16,0.5+state.direction.getStepZ()*(state.offset-0.5));
        if(state.upright)pose.translate(0,3f/32,0);
        if(state.direction.getAxis()!=Direction.Axis.X)pose.rotateDegrees(Axis.XP,state.angle);
        if(state.direction.getAxis()!=Direction.Axis.Z)pose.rotateDegrees(Axis.ZP,-state.angle);
        if(state.upright){pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(0,0,-1f/16);}
        var random=new java.util.Random(0);
        for(int i=0;i<=state.count;i++){
            pose.pushPose();if(state.blockItem)pose.translate(random.nextFloat()*0.0625f*i,0,random.nextFloat()*0.0625f*i);pose.scale(0.5f,0.5f,0.5f);
            if(!state.blockItem&&!state.upright)pose.rotateDegrees(Axis.XP,90);
            state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
            if(!state.upright){if(!state.blockItem)pose.rotateDegrees(Axis.YP,10);pose.translate(0,state.blockItem?1f/64:1f/16,0);}else pose.translate(0,0,-1f/16);
        }pose.popPose();
    }
}
