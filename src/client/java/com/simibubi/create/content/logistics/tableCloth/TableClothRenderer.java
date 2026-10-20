package com.simibubi.create.content.logistics.tableCloth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class TableClothRenderer implements BlockEntityRenderer<TableClothBlockEntity,TableClothRenderer.State> {
    private final ItemModelResolver items;
    public static final class State extends BlockEntityRenderState {
        public final List<ItemStackRenderState> items=new ArrayList<>();
        public final PartialModelRenderState tag=new PartialModelRenderState();
        public final ItemStackRenderState price=new ItemStackRenderState();
        public float yaw,cameraYaw,priceOffset;
        public boolean shop,topPrice;
        public Vec3 pricePosition;
    }
    public TableClothRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(TableClothBlockEntity cloth,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(cloth,state,partial,camera,overlay);state.items.clear();state.tag.clear();state.price.clear();state.shop=cloth.isShop();state.topPrice=cloth.sideOccluded;state.yaw=180-cloth.facing.toYRot();state.pricePosition=cloth.pricePosition();
        var delta=Vec3.atCenterOf(cloth.getBlockPos()).subtract(camera);state.cameraYaw=(float)Math.toDegrees(Math.atan2(delta.x,delta.z)+Math.PI);
        for(var stack:cloth.getItemsForRender()){var item=new ItemStackRenderState();items.updateForTopItem(item,stack,ItemDisplayContext.FIXED,cloth.getLevel(),null,0);state.items.add(item);}
        if(state.shop){AllPartialModels.extract(cloth.sideOccluded?AllPartialModels.TABLE_CLOTH_PRICE_TOP:AllPartialModels.TABLE_CLOTH_PRICE_SIDE,state.tag);items.updateForTopItem(state.price,cloth.getPaymentItem(),ItemDisplayContext.FIXED,cloth.getLevel(),null,0);state.priceOffset=ValueBoxRenderer.customZOffset(cloth.getPaymentItem().getItem());}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(-.5,-.5,-.5);
        if(state.shop)state.tag.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        for(int i=0;i<state.items.size();i++){
            var item=state.items.get(i);pose.pushPose();pose.translate(.5,3/16.,.5);
            if(state.items.size()>1){float angle=i*360f/state.items.size()+45;pose.rotateDegrees(Axis.YP,angle);pose.translate(0,i%2==0?-.005:0,5/16.);pose.rotateDegrees(Axis.YP,-angle);}
            boolean block=item.usesBlockLight();if(block){pose.translate(0,1/16.,0);pose.scale(.755f,.755f,.755f);}else{pose.rotateDegrees(Axis.YP,state.cameraYaw-state.yaw);pose.translate(0,3/32.,-1/16.);pose.scale(.5f,.5f,.5f);}
            item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }pose.popPose();
        if(!state.price.isEmpty()){pose.pushPose();pose.translate(state.pricePosition.x,state.pricePosition.y,state.pricePosition.z);pose.rotateDegrees(Axis.YP,state.yaw);if(state.topPrice)pose.rotateDegrees(Axis.XP,90);pose.scale(.33f,.33f,.33f);ValueBoxRenderer.renderItemIntoValueBox(state.price,pose,collector,state.lightCoords,state.priceOffset);pose.popPose();}
    }
}
