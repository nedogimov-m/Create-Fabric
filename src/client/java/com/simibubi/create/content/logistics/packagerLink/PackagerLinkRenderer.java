package com.simibubi.create.content.logistics.packagerLink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/** Native extracted mesh glow; submit has no references to the live block or level. */
public final class PackagerLinkRenderer implements BlockEntityRenderer<PackagerLinkBlockEntity,PackagerLinkRenderer.State> {
    public PackagerLinkRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState tube=new PartialModelRenderState(),halo=new PartialModelRenderState();
        public Direction face;public Vec3 offset;public boolean visible;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(PackagerLinkBlockEntity link,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(link,state,partial,camera,overlay);float glow=link.getGlow(partial);state.visible=glow>=.125f;if(!state.visible){state.tube.clear();state.halo.clear();return;}
        float brightness=Mth.clamp((float)(1-2*Math.pow(glow-.75f,2)),-1,1);int color=Math.clamp((int)(200*brightness),0,255);var block=link.getBlockState();state.face=PackagerLinkBlock.getConnectedDirection(block);
        var attachment=block.getValue(PackagerLinkBlock.FACE);var offset=attachment==AttachFace.WALL?new Vec3(11/16d,6/16d,5/16d):new Vec3(5/16d,6/16d,11/16d);
        float angle=switch(block.getValue(PackagerLinkBlock.FACING)){case SOUTH->0;case WEST->-90;case NORTH->180;case EAST->-270;default->0;};if(attachment==AttachFace.CEILING)angle=-angle;if(attachment==AttachFace.WALL)angle=0;
        state.offset=offset.subtract(.5,.5,.5).yRot((float)Math.toRadians(angle)).add(.5,.5,.5);
        AllPartialModels.extract(AllPartialModels.DISPLAY_LINK_TUBE,state.tube);AllPartialModels.extract(AllPartialModels.DISPLAY_LINK_GLOW,state.halo);
        var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();int rgba=0xFF000000|color<<16|color<<8|color;
        state.halo.mesh.forEach(quad->{emitter.copyFrom(quad).color(rgba,rgba,rgba,rgba).emissive(true).emit();});state.halo.mesh=mesh.immutableCopy();state.halo.translucent=true;state.tube.translucent=true;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.visible)return;float yaw=switch(state.face){case SOUTH->180;case WEST->90;case EAST->270;default->0;};float pitch=state.face==Direction.UP?0:state.face==Direction.DOWN?-180:-90;
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,pitch);pose.translate(-.5,-.5,-.5);pose.translate(state.offset.x,state.offset.y,state.offset.z);
        state.tube.submit(pose,collector,net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
        collector.submitBlockModel(pose,layer->net.minecraft.client.renderer.rendertype.RenderTypes.eyes(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS),true,List.of(),state.halo.mesh,new int[0],net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
        pose.popPose();
    }
}
