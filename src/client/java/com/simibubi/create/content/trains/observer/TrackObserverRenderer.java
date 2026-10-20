package com.simibubi.create.content.trains.observer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.TrainMailModels;
import com.simibubi.create.content.trains.track.TrackTargetingRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Real physical rail/Bezier marker and source top-face cargo filter on native render-state submission. */
public final class TrackObserverRenderer implements BlockEntityRenderer<TrackObserverBlockEntity,TrackObserverRenderer.State> {
  public static final class State extends BlockEntityRenderState{public final PartialModelRenderState overlay=new PartialModelRenderState();public final ItemStackRenderState filter=new ItemStackRenderState();public Matrix4f transform;public int overlayLight;public float filterOffset;public boolean hasFilter;}
  private final ItemModelResolver items;
  public TrackObserverRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
  @Override public State createRenderState(){return new State();}
  @Override public void extractRenderState(TrackObserverBlockEntity observer,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay crumbling){
    BlockEntityRenderer.super.extractRenderState(observer,state,partial,camera,crumbling);state.overlay.clear();state.filter.clear();state.transform=TrackTargetingRenderer.extractTransform(observer,observer.targeting());
    if(state.transform!=null){AllPartialModels.extract(TrainMailModels.OBSERVER_OVERLAY,state.overlay);cutout(state.overlay);state.overlayLight=net.minecraft.util.LightCoordsUtil.getLightCoords(observer.getLevel(),observer.targeting().getGlobalPosition());}
    var filter=observer.getFilter();state.hasFilter=ValueBoxRenderer.isFilterInRange(observer)&&!filter.isEmpty();if(state.hasFilter){items.updateForTopItem(state.filter,filter,ItemDisplayContext.FIXED,observer.getLevel(),null,0);state.filterOffset=ValueBoxRenderer.customZOffset(filter.getItem());}
  }
  @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
    if(state.transform!=null){pose.pushPose();pose.mulPose(state.transform);state.overlay.submit(pose,collector,state.overlayLight,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    if(state.hasFilter){pose.pushPose();pose.translate(.5,15.5/16d,.5);pose.rotateDegrees(Axis.XP,90);pose.scale(.5f,.5f,.5f);ValueBoxRenderer.renderItemIntoValueBox(state.filter,pose,collector,state.lightCoords,state.filterOffset);pose.popPose();}
  }
  @Override public boolean shouldRenderOffScreen(){return true;}
  @Override public int getViewDistance(){return 96;}
  private static void cutout(PartialModelRenderState state){
    var mesh=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();var emitter=mesh.emitter();
    state.mesh.forEach(quad->emitter.copyFrom(quad).chunkLayer(net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT).emit());state.mesh=mesh.immutableCopy();
  }

}
