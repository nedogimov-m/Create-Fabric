package com.simibubi.create.content.equipment.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Real source shaft/cogs transforms, captured before native submission. */
public class BacktankRenderer implements BlockEntityRenderer<BacktankBlockEntity,BacktankRenderer.State> {
    public static class State extends BlockEntityRenderState {public final PartialModelRenderState shaft=new PartialModelRenderState(),cogs=new PartialModelRenderState();public float shaftAngle,cogAngle,yaw;}
    public BacktankRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(BacktankBlockEntity tank,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay crumbling){BlockEntityRenderer.super.extractRenderState(tank,state,partial,camera,crumbling);AllPartialModels.extract(getShaftModel(tank.getBlockState()),state.shaft);AllPartialModels.extract(getCogsModel(tank.getBlockState()),state.cogs);state.shaftAngle=tank.getAngle(partial);state.cogAngle=(tank.getLevel().getGameTime()+partial)*tank.getSpeed()/4%360;state.yaw=tank.getBlockState().getValue(BacktankBlock.HORIZONTAL_FACING).toYRot();}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.shaftAngle);pose.translate(-.5,-.5,-.5);state.shaft.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();submitCogs(pose,collector,state.cogs,state.yaw,state.cogAngle,state.lightCoords,0,false);}
    public static void submitCogs(PoseStack pose,SubmitNodeCollector collector,PartialModelRenderState cogs,float yaw,float angle,int light,int outline,boolean foil){pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,180+yaw);pose.translate(-.5,-.5,-.5);pose.translate(0,6.5/16.,11/16.);pose.rotateDegrees(Axis.XP,angle);pose.translate(0,-6.5/16.,-11/16.);submitPartial(pose,collector,cogs,light,outline,foil);pose.popPose();}
    public static void submitPartial(PoseStack pose,SubmitNodeCollector collector,PartialModelRenderState model,int light,int outline,boolean foil){if(foil&&model.mesh!=null)collector.submitItem(pose,net.minecraft.world.item.ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,outline,net.minecraft.client.renderer.block.BlockModelRenderState.EMPTY_TINTS,net.minecraft.client.resources.model.geometry.ItemQuads.EMPTY,model.mesh,net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD);else model.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,outline);}
    public static ExtraModelKey<BlockStateModel> getCogsModel(BlockState state){return state.is(AllBlocks.NETHERITE_BACKTANK)?AllPartialModels.NETHERITE_BACKTANK_COGS:AllPartialModels.COPPER_BACKTANK_COGS;}
    public static ExtraModelKey<BlockStateModel> getShaftModel(BlockState state){return state.is(AllBlocks.NETHERITE_BACKTANK)?AllPartialModels.NETHERITE_BACKTANK_SHAFT:AllPartialModels.COPPER_BACKTANK_SHAFT;}
}
