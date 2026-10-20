package com.simibubi.create.content.fluids.pipes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

public final class SmartFluidPipeRenderer implements BlockEntityRenderer<SmartFluidPipeBlockEntity,SmartFluidPipeRenderer.State> {
    private final ItemModelResolver items;
    public SmartFluidPipeRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {public final ItemStackRenderState filter=new ItemStackRenderState();Vec3 offset;float yaw,pitch,itemOffset;}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(SmartFluidPipeBlockEntity pipe,State state,float partialTick,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(pipe,state,partialTick,camera,overlay);state.filter.clear();if(!ValueBoxRenderer.isFilterInRange(pipe)||pipe.getFilter().isEmpty())return;
        var block=pipe.getBlockState();state.offset=SmartFluidPipeBlock.filterPosition(block);state.yaw=SmartFluidPipeBlock.angleY(block);state.pitch=block.getValue(SmartFluidPipeBlock.FACE)==AttachFace.CEILING?-45:45;
        items.updateForTopItem(state.filter,pipe.getFilter(),ItemDisplayContext.FIXED,pipe.getLevel(),null,0);
        state.itemOffset=ValueBoxRenderer.customZOffset(pipe.getFilter().getItem());
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.filter.isEmpty())return;pose.pushPose();pose.translate(state.offset.x,state.offset.y,state.offset.z);pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.XP,state.pitch);pose.scale(0.51f,0.51f,0.51f);ValueBoxRenderer.renderItemIntoValueBox(state.filter,pose,collector,state.lightCoords,state.itemOffset);pose.popPose();
    }
}
