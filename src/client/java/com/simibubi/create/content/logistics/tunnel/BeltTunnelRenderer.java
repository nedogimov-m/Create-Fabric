package com.simibubi.create.content.logistics.tunnel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.funnel.FunnelRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class BeltTunnelRenderer implements BlockEntityRenderer<BeltTunnelBlockEntity,BeltTunnelRenderer.State> {
    private final ItemModelResolver items;
    public BeltTunnelRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    private record Filter(Direction face,ItemStackRenderState item,float offset){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState model=new PartialModelRenderState();
        public final Map<Direction,Float> flaps=new EnumMap<>(Direction.class);
        private final List<Filter> filters=new ArrayList<>();
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(BeltTunnelBlockEntity tunnel,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(tunnel,state,partial,camera,overlay);AllPartialModels.extract(AllPartialModels.BELT_TUNNEL_FLAP,state.model);state.flaps.clear();tunnel.flaps.forEach((side,value)->state.flaps.put(side,value.value(partial)));state.filters.clear();
        if(tunnel instanceof BrassTunnelBlockEntity brass&&ValueBoxRenderer.isFilterInRange(tunnel)){
            for(var side:Direction.Plane.HORIZONTAL){var sample=brass.filtering.getFilter(side);if(sample.isEmpty())continue;var item=new ItemStackRenderState();items.updateForTopItem(item,sample,ItemDisplayContext.FIXED,tunnel.getLevel(),null,0);state.filters.add(new Filter(side,item,ValueBoxRenderer.customZOffset(sample.getItem())));}
        }
    }
    private static float yaw(Direction side){return switch(side){case NORTH->180;case EAST->90;case WEST->270;default->0;};}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        for(var entry:state.flaps.entrySet()){
            pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw(entry.getKey().getOpposite()));pose.translate(-.5,-.5,-.5);pose.translate(.075/16,0,0);
            for(int segment=0;segment<4;segment++){pose.pushPose();pose.translate(0,10f/16,1f/16);pose.rotateDegrees(Axis.XP,FunnelRenderer.flapAngle(entry.getValue(),segment));pose.translate(0,-10f/16,-1f/16);state.model.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();pose.translate(-3.05/16,0,0);}pose.popPose();
        }
        for(var filter:state.filters){var position=TunnelSettings.filterPosition(filter.face);pose.pushPose();pose.translate(position.x,position.y,position.z);pose.rotateDegrees(Axis.YP,yaw(filter.face));pose.scale(.5f,.5f,.5f);ValueBoxRenderer.renderItemIntoValueBox(filter.item,pose,collector,state.lightCoords,filter.offset);pose.popPose();}
    }
}
