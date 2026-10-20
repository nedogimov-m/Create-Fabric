package com.simibubi.create.content.contraptions.chassis;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** The original sticker head, baked on resource reload and submitted using the native renderer. */
public final class StickerRenderer implements BlockEntityRenderer<StickerBlockEntity,StickerRenderer.State> {
    public static final ExtraModelKey<BlockStateModel> HEAD=ExtraModelKey.create(()->"create:block/sticker/head");
    public static void registerModels(){ModelLoadingPlugin.register(context->context.addModel(HEAD,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/sticker/head"))));}
    public StickerRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState{public final PartialModelRenderState head=new PartialModelRenderState();public float yaw,pitch,extension;}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(StickerBlockEntity sticker,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(sticker,state,partial,camera,overlay);var facing=sticker.getBlockState().getValue(StickerBlock.FACING);
        state.yaw=switch(facing){case NORTH->180;case EAST->90;case WEST->270;default->0;};state.pitch=facing==Direction.UP?0:facing==Direction.DOWN?180:90;
        float progress=sticker.getLevel()==Minecraft.getInstance().level?sticker.getPiston(partial):sticker.isBlockStateExtended()?1:0;
        state.extension=progress*progress*4/16f;AllPartialModels.extract(HEAD,state.head);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.rotate(Axis.XP.rotationDegrees(state.pitch));pose.translate(-.5,-.5,-.5);pose.translate(0,state.extension,0);state.head.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
}
