package com.simibubi.create.content.decoration.placard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.bell.BellRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/** Source attached-item transforms, extracted through the real native item model resolver. */
public final class PlacardRenderer implements BlockEntityRenderer<PlacardBlockEntity,PlacardRenderer.State> {
    private final ItemModelResolver items;
    public static final class State extends BlockEntityRenderState {public final ItemStackRenderState item=new ItemStackRenderState();public float yaw,pitch,scale;}
    public PlacardRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(PlacardBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);items.updateForTopItem(state.item,entity.getHeldItem(),ItemDisplayContext.FIXED,entity.getLevel(),null,0);var block=entity.getBlockState();var face=block.getValue(PlacardBlock.FACE);state.yaw=(face==AttachFace.CEILING?180:0)+180+BellRenderer.horizontalAngle(block.getValue(PlacardBlock.FACING));state.pitch=face==AttachFace.CEILING?-90:face==AttachFace.FLOOR?90:0;state.scale=state.item.usesBlockLight()?.5f:.375f;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){if(state.item.isEmpty())return;pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.XP,state.pitch);pose.translate(0,0,4.5/16f);pose.scale(state.scale,state.scale,state.scale);state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
}
