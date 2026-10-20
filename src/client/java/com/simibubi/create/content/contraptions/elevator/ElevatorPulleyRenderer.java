package com.simibubi.create.content.contraptions.elevator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.pulley.PulleySettings;
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

/** Genuine extracted elevator belt/coil/magnet scrolling; submission never reads a level or a BE. */
public final class ElevatorPulleyRenderer implements BlockEntityRenderer<ElevatorPulleyBlockEntity,ElevatorPulleyRenderer.State> {
    public ElevatorPulleyRenderer(BlockEntityRendererProvider.Context context){}
    public record Section(PartialModelRenderState model,float offset,int light){}
    public static final class State extends BlockEntityRenderState{public final PartialModelRenderState shaft=new PartialModelRenderState(),coil=new PartialModelRenderState();public final List<Section> sections=new ArrayList<>();public Direction.Axis axis;public float angle,coilYaw,beltYaw,offset;public boolean running;}
    @Override public State createRenderState(){return new State();}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return Math.max(1,PulleySettings.maxRopeLength);}
    @Override public void extractRenderState(ElevatorPulleyBlockEntity elevator,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(elevator,state,partial,camera,overlay);state.sections.clear();var facing=elevator.getBlockState().getValue(ElevatorPulleyBlock.HORIZONTAL_FACING);state.axis=facing.getClockWise().getAxis();state.coilYaw=facing.getAxis()==Direction.Axis.X?-facing.toYRot():facing.toYRot();state.beltYaw=180+state.coilYaw;state.offset=elevator.getInterpolatedOffset(partial);state.running=elevator.isRunning()||elevator.getMirrorParent()!=null;int parity=elevator.getBlockPos().getY()+(state.axis==Direction.Axis.X?elevator.getBlockPos().getZ():elevator.getBlockPos().getX());state.angle=elevator.getAngle(partial)+(parity%2==0?22.5f:0);
        AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,state.axis),state.shaft);AllPartialModels.extract(AllPartialModels.ELEVATOR_COIL,state.coil);if(state.offset!=0){float phase=state.offset+3f/16,scroll=-phase-(float)Math.floor(-2*phase)/2;shift(state.coil,"block/elevator_pulley_coil","block/elevator_pulley_coil_scroll",scroll);}
        if(state.running||state.offset==0){var magnet=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.ELEVATOR_MAGNET,magnet);section(elevator,state,magnet,state.offset);}
        if(state.offset==0)return;
        float phase=-(state.offset+.5f),beltScroll=(phase-(float)Math.floor(phase))/2;var half=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.ELEVATOR_BELT_HALF,half);shift(half,"block/elevator_pulley_belt","block/elevator_pulley_belt_scroll",beltScroll);var belt=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.ELEVATOR_BELT,belt);shift(belt,"block/elevator_pulley_belt","block/elevator_pulley_belt_scroll",beltScroll);
        float fraction=state.offset%1;if(fraction<.25f||fraction>.75f)section(elevator,state,half,fraction>.75f?fraction-1:fraction);if(state.running)for(int i=0;i<state.offset-.25f;i++)section(elevator,state,belt,state.offset-i);
    }
    private static void section(ElevatorPulleyBlockEntity elevator,State state,PartialModelRenderState model,float offset){var pos=elevator.getBlockPos().below((int)offset);if(elevator.getLevel().isLoaded(pos))state.sections.add(new Section(model,offset,LightCoordsUtil.getLightCoords(elevator.getLevel(),pos)));}
    private static void shift(PartialModelRenderState model,String from,String to,float scroll){var atlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);var source=atlas.getSprite(Create.asResource(from));var target=atlas.getSprite(Create.asResource(to));var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();model.mesh.forEach(quad->{emitter.copyFrom(quad);for(int i=0;i<4;i++)emitter.uv(i,quad.u(i)-source.getU0()+target.getU0(),quad.v(i)-source.getV0()+target.getV0()+scroll*(target.getV1()-target.getV0()));emitter.emit();});model.mesh=mesh.immutableCopy();}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(state.axis==Direction.Axis.X?Axis.XP:Axis.ZP,state.angle);pose.translate(-.5,-.5,-.5);state.shaft.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.coilYaw);pose.translate(-.5,-.5,-.5);state.coil.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        for(var section:state.sections){pose.pushPose();pose.translate(0,-section.offset(),0);pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.beltYaw);pose.translate(-.5,-.5,-.5);section.model().submit(pose,collector,section.light(),OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
}
