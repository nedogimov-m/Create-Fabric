package com.simibubi.create.content.kinetics.steamEngine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Original crank-slider geometry, using extracted partial models on every graphics backend. */
public final class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineBlockEntity,SteamEngineRenderer.State> {
    public SteamEngineRenderer(BlockEntityRendererProvider.Context context){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState piston=new PartialModelRenderState(),linkage=new PartialModelRenderState(),connector=new PartialModelRenderState();
        public boolean visible,roll90;
        public Direction facing;
        public float angle,pistonOffset,linkageAngle;
    }
    @Override public State createRenderState(){return new State();}
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 128;}
    @Override public void extractRenderState(SteamEngineBlockEntity engine,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(engine,state,partial,camera,overlay);state.visible=false;
        var shaft=engine.getShaft();if(shaft==null)return;state.facing=SteamEngineBlock.getFacing(engine.getBlockState());
        var facingAxis=state.facing.getAxis();var axis=shaft.getBlockState().getValue(PoweredShaftBlock.AXIS);if(axis==facingAxis)return;
        float angle=shaft.getAngle(partial);int parity=(axis==Direction.Axis.X?0:shaft.getBlockPos().getX())+(axis==Direction.Axis.Y?0:shaft.getBlockPos().getY())+(axis==Direction.Axis.Z?0:shaft.getBlockPos().getZ());angle+=parity%2==0?22.5f:0;
        if(axis.isHorizontal()&&(facingAxis==Direction.Axis.X^(state.facing.getAxisDirection()==Direction.AxisDirection.POSITIVE)))angle*=-1;
        if(axis==Direction.Axis.X&&state.facing==Direction.DOWN)angle*=-1;angle=(float)Math.toRadians(angle);
        state.angle=angle;state.roll90=facingAxis.isHorizontal()&&axis==Direction.Axis.Y||facingAxis.isVertical()&&axis==Direction.Axis.Z;
        float crank=6f/16,rod=14f/16;
        state.pistonOffset=crank*(float)Math.sin(angle)-(float)Math.sqrt(rod*rod-crank*crank*Math.pow(Math.cos(angle),2));
        float distance=Math.abs(state.pistonOffset-crank*(float)Math.sin(angle));state.linkageAngle=(float)Math.acos(Math.clamp(distance/rod,-1,1))*(Math.cos(angle)>=0?1:-1);
        AllPartialModels.extract(AllPartialModels.ENGINE_PISTON,state.piston);AllPartialModels.extract(AllPartialModels.ENGINE_LINKAGE,state.linkage);AllPartialModels.extract(AllPartialModels.ENGINE_CONNECTOR,state.connector);state.visible=true;
    }
    private static void orient(State state,PoseStack pose){
        pose.translate(0.5,0.5,0.5);float yaw=switch(state.facing){case NORTH->180;case WEST->-90;case EAST->-270;default->0;};float pitch=state.facing==Direction.UP?-90:state.facing==Direction.DOWN?90:0;
        pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,pitch+90);if(state.roll90)pose.rotateDegrees(Axis.YP,-90);pose.translate(-0.5,-0.5,-0.5);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(!state.visible)return;
        pose.pushPose();orient(state,pose);pose.translate(0,state.pistonOffset+20f/16,0);state.piston.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();orient(state,pose);pose.translate(0,1+state.pistonOffset+20f/16,0);pose.translate(0,4f/16,8f/16);pose.rotate(Axis.XP.rotation(state.linkageAngle));pose.translate(0,-4f/16,-8f/16);state.linkage.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();orient(state,pose);pose.translate(0,2,0);pose.translate(0.5,0.5,0.5);pose.rotate(Axis.XP.rotation(-(state.angle+(float)Math.PI/2)));pose.translate(-0.5,-0.5,-0.5);state.connector.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
}
