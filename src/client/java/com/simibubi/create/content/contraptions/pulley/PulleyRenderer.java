package com.simibubi.create.content.contraptions.pulley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** Native extraction snapshots coil UVs, actual shared body extension and each rope section's light. */
public final class PulleyRenderer implements BlockEntityRenderer<PulleyBlockEntity,PulleyRenderer.State> {
    public PulleyRenderer(BlockEntityRendererProvider.Context context){}
    public record Section(PartialModelRenderState model,float offset,int light){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState shaft=new PartialModelRenderState(),coil=new PartialModelRenderState();
        public final List<Section> sections=new ArrayList<>();public Direction.Axis axis;public float angle,coilYaw,offset;public boolean running,mirror;
    }
    @Override public State createRenderState(){return new State();}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return Math.max(1,PulleySettings.maxRopeLength);}
    @Override public void extractRenderState(PulleyBlockEntity pulley,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(pulley,state,partial,camera,overlay);state.sections.clear();state.axis=pulley.getBlockState().getValue(PulleyBlock.HORIZONTAL_AXIS);state.offset=pulley.getInterpolatedOffset(partial);state.running=pulley.isRunning()||pulley.getMirrorParent()!=null||pulley.isVirtual();state.mirror=pulley.getMirrorParent()!=null;
        int parity=pulley.getBlockPos().getY()+(state.axis==Direction.Axis.X?pulley.getBlockPos().getZ():pulley.getBlockPos().getX());state.angle=pulley.getAngle(partial)+(parity%2==0?22.5f:0);state.coilYaw=state.axis==Direction.Axis.X?90:0;
        AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,state.axis),state.shaft);AllPartialModels.extract(AllPartialModels.ROPE_COIL,state.coil);scrollCoil(state.coil,state.offset);
        var rope=new PartialModelRenderState();AllPartialModels.extract(AllBlocks.ROPE.defaultBlockState(),rope);var half=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.ROPE_HALF,half);var magnet=new PartialModelRenderState();if(state.offset>.25f)AllPartialModels.extract(AllBlocks.PULLEY_MAGNET.defaultBlockState(),magnet);else AllPartialModels.extract(AllPartialModels.ROPE_HALF_MAGNET,magnet);
        if(state.running||state.offset==0)addSection(pulley,state,magnet,state.offset);float fraction=state.offset%1;if(state.offset>.75f&&(fraction<.25f||fraction>.75f))addSection(pulley,state,half,fraction>.75f?fraction-1:fraction);
        if(state.running)for(int i=0;i<state.offset-1.25f;i++)addSection(pulley,state,rope,state.offset-i-1);
    }
    private static void addSection(PulleyBlockEntity pulley,State state,PartialModelRenderState model,float offset){var pos=pulley.getBlockPos().below((int)offset);if(pulley.getLevel().isLoaded(pos))state.sections.add(new Section(model,offset,LightCoordsUtil.getLightCoords(pulley.getLevel(),pos)));}
    private static void scrollCoil(PartialModelRenderState model,float offset){
        if(offset==0)return;var atlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);var source=atlas.getSprite(Create.asResource("block/rope_pulley_coil"));var target=atlas.getSprite(Create.asResource("block/rope_pulley_coil_scroll"));float phase=offset/2+3f/16,scroll=-phase-(float)Math.floor(-2*phase)/2;
        var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();model.mesh.forEach(quad->{emitter.copyFrom(quad);for(int i=0;i<4;i++)emitter.uv(i,quad.u(i)-source.getU0()+target.getU0(),quad.v(i)-source.getV0()+target.getV0()+scroll*(target.getV1()-target.getV0()));emitter.emit();});model.mesh=mesh.immutableCopy();
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(state.axis==Direction.Axis.X?Axis.XP:Axis.ZP,state.angle);pose.translate(-.5,-.5,-.5);state.shaft.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.coilYaw);pose.translate(-.5,-.5,-.5);state.coil.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        for(var section:state.sections){pose.pushPose();pose.translate(0,-section.offset(),0);section.model().submit(pose,collector,section.light(),OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
}
