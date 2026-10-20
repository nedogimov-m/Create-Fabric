package com.simibubi.create.content.redstone.link.controller;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class LecternControllerRenderer implements BlockEntityRenderer<LecternControllerBlockEntity,LecternControllerRenderer.State>{
    private final LinkedControllerItemRenderer item=new LinkedControllerItemRenderer();
    public static final class State extends BlockEntityRenderState{public LinkedControllerItemRenderer.State controller;public Direction facing;}
    public LecternControllerRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(LecternControllerBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.facing=entity.getBlockState().getValue(LecternControllerBlock.FACING);
        var player=Minecraft.getInstance().player;state.controller=entity.getController().isEmpty()?null:LinkedControllerItemRenderer.extract(entity.hasUser(),player!=null&&entity.isUsedBy(player),partial);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.controller==null)return;pose.pushPose();pose.translate(.5,1.45,.5);pose.rotateDegrees(Axis.YP,state.facing.toYRot()-90);pose.translate(.28,0,0);pose.rotateDegrees(Axis.ZP,-22);item.submit(state.controller,pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,false,0);pose.popPose();
    }
}
