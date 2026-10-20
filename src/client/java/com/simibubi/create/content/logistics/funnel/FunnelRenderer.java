package com.simibubi.create.content.logistics.funnel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.*;

/** Immutable extracted mesh and item state submitted to Minecraft's native renderer. */
public final class FunnelRenderer implements BlockEntityRenderer<FunnelBlockEntity,FunnelRenderer.State> {
    private final ItemModelResolver items;
    public FunnelRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState flaps=new PartialModelRenderState();
        public final ItemStackRenderState filter=new ItemStackRenderState();
        public boolean hasFlap,topFilter;
        public Direction facing,filterFace;
        public Vec3 filterPosition;
        public float flap,offset,itemOffset,filterPitch;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(FunnelBlockEntity funnel,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(funnel,state,partial,camera,overlay);
        var block=funnel.getBlockState();state.hasFlap=funnel.hasFlap();state.facing=AbstractFunnelBlock.getFunnelFacing(block);state.flap=funnel.getFlap(partial);state.offset=funnel.getFlapOffset();
        state.flaps.clear();if(state.hasFlap)AllPartialModels.extract(block.getBlock() instanceof BeltFunnelBlock?AllPartialModels.BELT_FUNNEL_FLAP:AllPartialModels.FUNNEL_FLAP,state.flaps);
        state.filter.clear();if(!ValueBoxRenderer.isFilterInRange(funnel)||!funnel.supportsFiltering()||funnel.filtering.getFilter().isEmpty())return;
        var client=net.minecraft.client.Minecraft.getInstance();
        var side=state.facing;
        if(side.getAxis().isVertical()){
            if(!(client.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(funnel.getBlockPos())||!FunnelFilterSlotPositioning.activeSide(block,hit.getDirection()))return;
            side=hit.getDirection();
        }else if(block.getBlock() instanceof BeltFunnelBlock&&block.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.EXTENDED)side=Direction.UP;
        state.filterPitch=block.getBlock() instanceof FunnelBlock&&state.facing.getAxis().isHorizontal()||block.getBlock() instanceof BeltFunnelBlock&&(block.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.PUSHING||block.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.PULLING)?-22.5f:0;
        state.filterFace=side;state.filterPosition=FunnelFilterSlotPositioning.position(block,side);state.topFilter=side==Direction.UP;
        items.updateForTopItem(state.filter,funnel.filtering.getFilter(),ItemDisplayContext.FIXED,funnel.getLevel(),null,0);state.itemOffset=ValueBoxRenderer.customZOffset(funnel.filtering.getFilter().getItem());
    }
    private static float yaw(Direction side){return switch(side){case NORTH->180;case EAST->90;case WEST->270;default->0;};}
    public static float flapAngle(float value,int segment){float angle=(float)Math.sin((1-Math.abs(value))*Math.PI*(segment==3?1.5:segment+1))*30*value;return value<0?angle*.5f:angle;}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.hasFlap){
            pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw(state.facing.getOpposite()));pose.translate(-.5,-.5,-.5);pose.translate(.075/16,0,-state.offset);
            for(int segment=0;segment<4;segment++){
                pose.pushPose();pose.translate(0,10f/16,9.5f/16);pose.rotateDegrees(Axis.XP,flapAngle(state.flap,segment));pose.translate(0,-10f/16,-9.5f/16);
                state.flaps.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();pose.translate(-3.05/16,0,0);
            }pose.popPose();
        }
        if(!state.filter.isEmpty()){
            pose.pushPose();pose.translate(state.filterPosition.x,state.filterPosition.y,state.filterPosition.z);pose.rotateDegrees(Axis.YP,yaw(state.topFilter?state.facing:state.filterFace));pose.rotateDegrees(Axis.XP,state.topFilter?90:state.filterPitch);pose.scale(.5f,.5f,.5f);ValueBoxRenderer.renderItemIntoValueBox(state.filter,pose,collector,state.lightCoords,state.itemOffset);pose.popPose();
        }
    }
}
