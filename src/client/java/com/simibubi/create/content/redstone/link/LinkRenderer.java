package com.simibubi.create.content.redstone.link;

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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Item samples are extracted from the live BE; submit consumes only detached render states. */
public final class LinkRenderer implements BlockEntityRenderer<RedstoneLinkBlockEntity,LinkRenderer.State>{
    private final ItemModelResolver items;
    public static final class State extends BlockEntityRenderState{
        public final ItemStackRenderState[] samples={new ItemStackRenderState(),new ItemStackRenderState()};
        public final float[] offsets=new float[2];public Direction facing;
    }
    public LinkRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(RedstoneLinkBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.facing=entity.getBlockState().getValue(RedstoneLinkBlock.FACING);
        for(var sample:state.samples)sample.clear();
        if(!ValueBoxRenderer.isFilterInRange(entity))return;
        var key=entity.getLink().getNetworkKey();
        for(int i=0;i<2;i++){var stack=key.get(i==0).getStack();state.samples[i].clear();items.updateForTopItem(state.samples[i],stack,ItemDisplayContext.FIXED,entity.getLevel(),null,i);state.offsets[i]=ValueBoxRenderer.customZOffset(stack.getItem());}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        for(int i=0;i<2;i++){pose.pushPose();var offset=RedstoneLinkFrequencySlot.offset(state.facing,i==0);pose.translate(offset.x,offset.y,offset.z);
            pose.rotateDegrees(Axis.YP,state.facing.getAxis().isVertical()?0:state.facing.toYRot()+180);
            pose.rotateDegrees(Axis.XP,state.facing==Direction.UP?90:state.facing==Direction.DOWN?270:0);
            pose.scale(.4975f,.4975f,.4975f);ValueBoxRenderer.renderItemIntoValueBox(state.samples[i],pose,collector,state.lightCoords,state.offsets[i]);pose.popPose();}
    }
}
