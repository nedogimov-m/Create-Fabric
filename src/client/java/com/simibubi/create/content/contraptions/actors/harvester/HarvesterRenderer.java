package com.simibubi.create.content.contraptions.actors.harvester;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Native source blade pivot and motion; submit reads detached mesh/angle/facing only. */
public final class HarvesterRenderer implements BlockEntityRenderer<HarvesterBlockEntity,HarvesterRenderer.State> {
    public HarvesterRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState blade=new PartialModelRenderState();
        public Direction facing;public float angle;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(HarvesterBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.facing=entity.getBlockState().getValue(HarvesterBlock.FACING);state.angle=(float)((entity.getLevel().getGameTime()+partial)/20d*entity.getAnimatedSpeed()%360);AllPartialModels.extract(AllPartialModels.HARVESTER_BLADE,state.blade);}
    public void extractActorRenderState(HarvesterBlockEntity entity,State state,MovementContext context,float partial,Vec3 camera){extractRenderState(entity,state,partial,camera,null);float speed=context.contraption.stalled||DrillMovementBehaviour.backwards(context,state.facing)?0:context.getAnimationSpeed();state.angle=(float)((context.world.getGameTime()+partial)/20d*speed%360);}
    public static void transformBlade(PoseStack pose,Direction facing,float angle){
        float yaw=switch(facing){case NORTH->180;case EAST->90;case WEST->270;default->0;};pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw);pose.translate(-.5,-.5,-.5);
        pose.translate(0,6/16d,9/16d);pose.rotateDegrees(Axis.XP,-angle);pose.translate(0,-6/16d,-9/16d);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){pose.pushPose();transformBlade(pose,state.facing,state.angle);state.blade.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
}
