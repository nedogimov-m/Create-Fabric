package com.simibubi.create.content.decoration.slidingDoor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;

/** Native extraction/submission renderer. Submit uses immutable geometry and scalar animation only. */
public final class SlidingDoorRenderer implements BlockEntityRenderer<SlidingDoorBlockEntity,SlidingDoorRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState first=new PartialModelRenderState(),second=new PartialModelRenderState();
        public boolean draw,fold,flip;
        public float value,yaw;
        public Vec3 offset=Vec3.ZERO,front=Vec3.ZERO;
    }
    public SlidingDoorRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(SlidingDoorBlockEntity door,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(door,state,partial,camera,overlay);
        var block=door.getBlockState();state.draw=door.shouldRenderSpecial(block);if(!state.draw){state.first.clear();state.second.clear();return;}
        var facing=block.getValue(DoorBlock.FACING);var move=facing.getClockWise();state.flip=block.getValue(DoorBlock.HINGE)==DoorHingeSide.RIGHT;if(!state.flip)move=move.getOpposite();
        state.value=door.getAnimation(partial);float front=Mth.clamp(state.value*10,0,1)/32f;state.front=facing.getUnitVec3().scale(front);state.offset=move.getUnitVec3().scale(state.value*state.value*13/16f).add(state.front);
        state.fold=((SlidingDoorBlock)block.getBlock()).isFoldingDoor();state.yaw=switch(facing.getClockWise()){case NORTH->180;case EAST->90;case WEST->270;default->0;};
        if(state.fold){var halves=SlidingDoorModels.get(BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath());if(halves==null)throw new IllegalStateException("No folding models for "+block.getBlock());AllPartialModels.extract(state.flip?halves.right():halves.left(),state.first);AllPartialModels.extract(state.flip?halves.left():halves.right(),state.second);}
        else {var closed=block.setValue(DoorBlock.OPEN,false).setValue(SlidingDoorBlock.VISIBLE,true);AllPartialModels.extract(closed.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),state.first);AllPartialModels.extract(closed.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),state.second);}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(!state.draw)return;
        if(state.fold){fold(state,pose,collector,true,state.first);fold(state,pose,collector,false,state.second);return;}
        pose.pushPose();pose.translate(state.offset);state.first.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.translate(0,1-1/512d,0);state.second.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    private static void fold(State state,PoseStack pose,SubmitNodeCollector collector,boolean left,PartialModelRenderState model){
        float sign=state.flip?-1:1,progress=state.value*state.value;
        pose.pushPose();pose.translate(0,-1/512d,0);pose.translate(state.front);pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.translate(-.5,-.5,-.5);
        if(state.flip)pose.translate(0,0,1);pose.rotate(Axis.YP.rotationDegrees(91*sign*progress));
        if(!left){pose.translate(0,0,sign/2f);pose.rotate(Axis.YP.rotationDegrees(-181*sign*progress));}
        if(state.flip)pose.translate(0,0,-.5);model.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
}
