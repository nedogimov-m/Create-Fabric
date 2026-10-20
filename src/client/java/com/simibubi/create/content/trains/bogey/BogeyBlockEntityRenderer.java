package com.simibubi.create.content.trains.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class BogeyBlockEntityRenderer implements BlockEntityRenderer<StandardBogeyBlockEntity,BogeyBlockEntityRenderer.State> {
    public static final class State extends BlockEntityRenderState {public final BogeyRenderState bogey=new BogeyRenderState();public float yaw;}
    public BogeyBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(StandardBogeyBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);var block=(AbstractBogeyBlock<?>)entity.getBlockState().getBlock();state.yaw=entity.getBlockState().getValue(AbstractBogeyBlock.AXIS)==Direction.Axis.X?90:0;
        BogeyRenderers.extract(entity.getStyle(),block.getSize(),entity.getBogeyData(),entity.getVirtualAngle(partial),partial,state.bogey,false);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.translate(0,-1.5-1/128f,0);state.bogey.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    // Native API has no per-BE custom frustum box. Parts extend beyond the one-block anchor, as source inflate(2).
    @Override public boolean shouldRenderOffScreen(){return true;}
}
