package com.simibubi.create.content.logistics.crate;

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
import net.minecraft.world.phys.Vec3;

public final class CreativeCrateRenderer implements BlockEntityRenderer<CreativeCrateBlockEntity,CreativeCrateRenderer.State> {
    private final ItemModelResolver items;
    public static final class State extends BlockEntityRenderState{public final ItemStackRenderState sample=new ItemStackRenderState();public float offset;}
    public CreativeCrateRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(CreativeCrateBlockEntity crate,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){BlockEntityRenderer.super.extractRenderState(crate,state,partial,camera,overlay);state.sample.clear();var sample=crate.filtering.getFilter();if(sample.isEmpty())return;items.updateForTopItem(state.sample,sample,ItemDisplayContext.FIXED,crate.getLevel(),null,0);state.offset=ValueBoxRenderer.customZOffset(sample.getItem());}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){if(state.sample.isEmpty())return;pose.pushPose();var at=CreativeCrateBlock.FILTER_POSITION;pose.translate(at.x,at.y,at.z);pose.rotateDegrees(Axis.XP,90);pose.scale(.5f,.5f,.5f);ValueBoxRenderer.renderItemIntoValueBox(state.sample,pose,collector,state.lightCoords,state.offset);pose.popPose();}
}
