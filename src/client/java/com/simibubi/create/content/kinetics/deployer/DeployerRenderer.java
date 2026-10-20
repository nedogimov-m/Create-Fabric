package com.simibubi.create.content.kinetics.deployer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class DeployerRenderer implements BlockEntityRenderer<DeployerBlockEntity,DeployerRenderer.State> {
    private final ItemModelResolver items;
    public DeployerRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState shaft=new PartialModelRenderState(),pole=new PartialModelRenderState(),hand=new PartialModelRenderState();
        public final ItemStackRenderState held=new ItemStackRenderState(),filter=new ItemStackRenderState();
        public Direction facing,filterFace;
        public Direction.Axis axis;
        public float angle,offset,time;
        public boolean poleTurn,punching,display,blockItem;
        public Vec3 filterPosition;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(DeployerBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);var block=entity.getBlockState();state.facing=block.getValue(DeployerBlock.FACING);state.axis=((DeployerBlock)block.getBlock()).getRotationAxis(block);state.angle=entity.getAngle(partial);state.offset=entity.getHandOffset(partial);state.time=entity.getLevel().getGameTime()+partial;state.poleTurn=block.getValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE)^(state.facing.getAxis()==Direction.Axis.Z);state.punching=entity.getMode()==DeployerBlockEntity.Mode.PUNCH;
        AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS,state.axis),state.shaft);AllPartialModels.extract(AllPartialModels.DEPLOYER_POLE,state.pole);AllPartialModels.extract(state.punching?AllPartialModels.DEPLOYER_HAND_PUNCHING:entity.getHeldItem().isEmpty()?AllPartialModels.DEPLOYER_HAND_POINTING:AllPartialModels.DEPLOYER_HAND_HOLDING,state.hand);
        state.held.clear();state.filter.clear();state.filterFace=null;state.display=state.facing==Direction.UP&&entity.getSpeed()==0&&!state.punching;state.blockItem=entity.getHeldItem().getItem() instanceof BlockItem;
        if(!entity.getHeldItem().isEmpty())items.updateForTopItem(state.held,entity.getHeldItem(),state.display?ItemDisplayContext.GROUND:state.punching?ItemDisplayContext.THIRD_PERSON_RIGHT_HAND:ItemDisplayContext.FIXED,entity.getLevel(),null,0);
        var client=net.minecraft.client.Minecraft.getInstance();if(!entity.filter.isEmpty()&&client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(entity.getBlockPos())&&((DeployerBlock)block.getBlock()).filterSide(block,hit.getDirection())){state.filterFace=hit.getDirection();state.filterPosition=DeployerBlock.filterPosition(block,state.filterFace);items.updateForTopItem(state.filter,entity.filter,ItemDisplayContext.FIXED,entity.getLevel(),null,0);}
    }
    private static float yaw(Direction face){return switch(face){case NORTH->180;case EAST->90;case WEST->270;default->0;};}
    public void extractActorRenderState(DeployerBlockEntity entity,State state,com.simibubi.create.content.contraptions.behaviour.MovementContext context,float partial,Vec3 camera){
        extractRenderState(entity,state,partial,camera,null);state.held.clear();state.filter.clear();state.filterFace=null;state.display=false;
        state.punching=DeployerMovementBehaviour.getMode(context)==DeployerBlockEntity.Mode.PUNCH;
        AllPartialModels.extract(state.punching?AllPartialModels.DEPLOYER_HAND_PUNCHING:AllPartialModels.DEPLOYER_HAND_POINTING,state.hand);
        state.angle=context.contraption.stalled?0:(float)((context.world.getGameTime()+partial)/20d*context.getAnimationSpeed()%360);
        double factor;if(context.contraption.stalled||context.position==null||context.data.contains("StationaryTimer"))factor=Math.sin((context.world.getGameTime()+partial)*.5)*.25+.25;
        else{var center=Vec3.atCenterOf(net.minecraft.core.BlockPos.containing(context.position));double distance=context.position.distanceTo(center),next=context.position.add(context.motion).distanceTo(center);factor=.5-Math.clamp(distance+(next-distance)*partial,0,1);}
        state.offset=context.disabled?0:(float)factor;
    }
    public static void transformComponent(PoseStack pose,State state,boolean pole){pose.translate(0.5,0.5,0.5);pose.rotateDegrees(Axis.YP,yaw(state.facing));pose.rotateDegrees(Axis.XP,state.facing==Direction.UP?270:state.facing==Direction.DOWN?90:0);if(pole&&state.poleTurn)pose.rotateDegrees(Axis.ZP,90);pose.translate(-0.5,-0.5,-0.5);}
    public static void transformHeldItem(PoseStack pose,State state){
        pose.translate(0.5+state.facing.getStepX()*state.offset,0.5+state.facing.getStepY()*state.offset,0.5+state.facing.getStepZ()*state.offset);pose.rotateDegrees(Axis.YP,yaw(state.facing)+180);
        if(!state.display){pose.rotateDegrees(Axis.XP,state.facing==Direction.UP?90:state.facing==Direction.DOWN?270:0);pose.translate(0,0,-11f/16);}
        if(state.punching)pose.translate(0,1f/8,-1f/16);
        if(state.display){pose.translate(0,state.blockItem?9f/16:11f/16,0);float scale=state.blockItem?1.25f:1;pose.scale(scale,scale,scale);pose.rotateDegrees(Axis.YP,state.time);}
        else {float scale=state.punching?0.75f:state.blockItem?0.75f-1f/64:0.5f;pose.scale(scale,scale,scale);}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(0.5,0.5,0.5);pose.rotate(switch(state.axis){case X->Axis.XP.rotationDegrees(state.angle);case Y->Axis.YP.rotationDegrees(state.angle);case Z->Axis.ZP.rotationDegrees(state.angle);});pose.translate(-0.5,-0.5,-0.5);state.shaft.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        for(boolean pole:new boolean[]{true,false}){pose.pushPose();pose.translate(state.facing.getStepX()*state.offset,state.facing.getStepY()*state.offset,state.facing.getStepZ()*state.offset);transformComponent(pose,state,pole);(pole?state.pole:state.hand).submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        if(!state.held.isEmpty()){
            pose.pushPose();transformHeldItem(pose,state);
            state.held.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
        if(!state.filter.isEmpty()){
            pose.pushPose();pose.translate(state.filterPosition.x,state.filterPosition.y,state.filterPosition.z);pose.rotateDegrees(Axis.YP,yaw(state.filterFace)+180);if(state.filterFace.getAxis().isVertical())pose.rotateDegrees(Axis.XP,state.filterFace==Direction.UP?90:270);pose.scale(0.25f,0.25f,0.25f);state.filter.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
}
