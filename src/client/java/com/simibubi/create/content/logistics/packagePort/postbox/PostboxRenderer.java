package com.simibubi.create.content.logistics.packagePort.postbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.TrainMailModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;

/** Source flag hinge/wiggle and address hover, extracted into native detached renderer state. */
public final class PostboxRenderer implements BlockEntityRenderer<PostboxBlockEntity,PostboxRenderer.State> {
  public static final class State extends BlockEntityRenderState{public final PartialModelRenderState flag=new PartialModelRenderState();public float progress,yaw;public Component name;}
  public PostboxRenderer(BlockEntityRendererProvider.Context context){}
  @Override public State createRenderState(){return new State();}
  @Override public void extractRenderState(PostboxBlockEntity box,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay crumbling){
    BlockEntityRenderer.super.extractRenderState(box,state,partial,camera,crumbling);AllPartialModels.extract(TrainMailModels.POSTBOX_FLAG,state.flag);cutout(state.flag);
    state.yaw=180-box.getBlockState().getValue(PostboxBlock.FACING).toYRot();
    float value=box.flag(partial);float progress=(float)Math.pow(Math.min(value*5,1),2);
    if(box.flagTarget()>0&&!box.flagSettled()&&progress==1){float wiggle=(value-.2f)/.8f;progress+=(float)(Math.sin(wiggle*2*Math.PI*4)/8)/Math.max(1,8*wiggle);}
    state.progress=progress;
    state.name=Minecraft.getInstance().hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(box.getBlockPos())&&!box.addressFilter.isBlank()?Component.literal(box.addressFilter):null;
  }
  @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
    pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(-.5,-.5,-.5);
    pose.translate(0,10/16d,2/16d);pose.rotateDegrees(Axis.XP,-state.progress*90);pose.translate(0,-10/16d,-2/16d);
    state.flag.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    if(state.name!=null)collector.submitNameTag(pose,new Vec3(.5,1,.5),0,state.name,true,state.lightCoords,camera);
  }
  private static void cutout(PartialModelRenderState state){
    var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();var emitter=mesh.emitter();
    state.mesh.forEach(quad->emitter.copyFrom(quad).chunkLayer(net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT).emit());state.mesh=mesh.immutableCopy();
  }

}
