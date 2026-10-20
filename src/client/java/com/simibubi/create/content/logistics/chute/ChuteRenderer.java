package com.simibubi.create.content.logistics.chute;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import java.util.*;
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

public final class ChuteRenderer implements BlockEntityRenderer<ChuteBlockEntity,ChuteRenderer.State> {
    private final ItemModelResolver items;
    public ChuteRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    private record Filter(Direction side,ItemStackRenderState item,float offset){}
    public static final class State extends BlockEntityRenderState {
        public final ItemStackRenderState item=new ItemStackRenderState();
        private final List<Filter> filters=new ArrayList<>();
        public float position;
        public boolean packageItem;
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ChuteBlockEntity chute,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(chute,state,partial,camera,overlay);state.item.clear();state.filters.clear();state.position=chute.getItemPosition(partial);state.packageItem=com.simibubi.create.content.logistics.box.PackageItem.isPackage(chute.getItem());var block=chute.getBlockState();
        boolean render=chute instanceof SmartChuteBlockEntity?state.position<=0:AbstractChuteBlock.getChuteFacing(block)==Direction.DOWN&&(AbstractChuteBlock.isTransparentChute(block)||chute.getBottomPullDistance()!=0&&state.position<=.5f);
        if(render&&!chute.getItem().isEmpty())items.updateForTopItem(state.item,chute.getItem(),ItemDisplayContext.FIXED,chute.getLevel(),null,0);
        if(chute instanceof SmartChuteBlockEntity smart&&ValueBoxRenderer.isFilterInRange(chute)&&!smart.filtering.getFilter().isEmpty()){
            var client=net.minecraft.client.Minecraft.getInstance();if(client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(chute.getBlockPos())&&SmartChuteBlock.activeSide(hit.getDirection())){var sample=smart.filtering.getFilter();var filter=new ItemStackRenderState();items.updateForTopItem(filter,sample,ItemDisplayContext.FIXED,chute.getLevel(),null,0);state.filters.add(new Filter(hit.getDirection(),filter,ValueBoxRenderer.customZOffset(sample.getItem())));}
        }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(!state.item.isEmpty()){pose.pushPose();pose.translate(.5,state.position,.5);float scale=state.packageItem?1.5f:.5f;pose.scale(scale,scale,scale);if(!state.packageItem){pose.rotateDegrees(Axis.XP,state.position*180);pose.rotateDegrees(Axis.YP,state.position*180);}state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        for(var filter:state.filters){var at=SmartChuteBlock.filterPosition(filter.side);pose.pushPose();pose.translate(at.x,at.y,at.z);pose.rotateDegrees(Axis.YP,switch(filter.side){case NORTH->180;case EAST->90;case WEST->270;default->0;});pose.scale(.5f,.5f,.5f);ValueBoxRenderer.renderItemIntoValueBox(filter.item,pose,collector,state.lightCoords,filter.offset);pose.popPose();}
    }
}
