package com.simibubi.create.content.redstone.displayLink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** Actual source pulse bulb/tint, extracted geometry and native additive eyes pipeline used by existing link rendering. */
public final class LinkBulbRenderer implements BlockEntityRenderer<DisplayLinkBlockEntity,LinkBulbRenderer.State> {
    public static final class State extends BlockEntityRenderState {public final PartialModelRenderState tube=new PartialModelRenderState(),halo=new PartialModelRenderState();public Direction facing;public Vec3 offset;public boolean visible;public float glow;}
    public LinkBulbRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(DisplayLinkBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.glow=entity.getGlow(partial);state.visible=state.glow>=.125f;state.tube.clear();state.halo.clear();if(!state.visible)return;var block=entity.getBlockState();state.facing=entity.getBulbFacing(block);state.offset=entity.getBulbOffset(block);int color=Math.clamp((int)(200*Math.clamp(1-2*Math.pow(state.glow-.75f,2),-1,1)),0,255);AllPartialModels.extract(AllPartialModels.DISPLAY_LINK_TUBE,state.tube);AllPartialModels.extract(AllPartialModels.DISPLAY_LINK_GLOW,state.halo);
        var tube=Renderer.get().mutableMesh();var emitter=tube.emitter();state.tube.mesh.forEach(quad->emitter.copyFrom(quad).chunkLayer(ChunkSectionLayer.TRANSLUCENT).emissive(true).emit());state.tube.mesh=tube.immutableCopy();state.tube.translucent=true;var halo=Renderer.get().mutableMesh();var output=halo.emitter();int tint=0xFF000000|color<<16|color<<8|color;state.halo.mesh.forEach(quad->output.copyFrom(quad).chunkLayer(ChunkSectionLayer.TRANSLUCENT).color(tint,tint,tint,tint).emissive(true).emit());state.halo.mesh=halo.immutableCopy();state.halo.translucent=true;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){if(!state.visible)return;float yaw=switch(state.facing){case SOUTH->180;case WEST->90;case EAST->270;default->0;},pitch=state.facing==Direction.UP?0:state.facing==Direction.DOWN?-180:-90;pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,pitch);pose.translate(-.5,-.5,-.5);pose.translate(state.offset.x,state.offset.y,state.offset.z);state.tube.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);collector.submitBlockModel(pose,layer->RenderTypes.eyes(TextureAtlas.LOCATION_BLOCKS),true,List.of(),state.halo.mesh,new int[0],LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
}
