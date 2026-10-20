package com.simibubi.create.content.contraptions.gantry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

/** Immutable extraction separates the output shaft and the source's two half-speed pinion gears. */
public final class GantryCarriageRenderer implements BlockEntityRenderer<GantryCarriageBlockEntity,GantryCarriageRenderer.State> {
    public static final class State extends BlockEntityRenderState{
        public final KineticRenderState shaft=new KineticRenderState();
        public final PartialModelRenderState cogs=new PartialModelRenderState();
        public Direction facing;
        public boolean alongFirst;
        public float cogAngle;
    }
    private final KineticBlockEntityRenderer shaft;
    private final BlockModelResolver models;
    private final BlockDisplayContext display=BlockDisplayContext.create();
    public GantryCarriageRenderer(BlockEntityRendererProvider.Context context){shaft=new KineticBlockEntityRenderer(context);models=context.blockModelResolver();}
    @Override public State createRenderState(){return new State();}
    private static float offset(BlockPos pos,Direction.Axis axis){int parity=(axis==Direction.Axis.X?0:pos.getX())+(axis==Direction.Axis.Y?0:pos.getY())+(axis==Direction.Axis.Z?0:pos.getZ());return parity%2==0?22.5f:0;}
    private static float yaw(Direction facing){return switch(facing){case NORTH->180;case EAST->90;case WEST->270;default->0;};}
    @Override public void extractRenderState(GantryCarriageBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);shaft.extractRenderState(entity,state.shaft,partial,camera,overlay);var block=entity.getBlockState();state.facing=block.getValue(GantryCarriageBlock.FACING);state.alongFirst=block.getValue(GantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);
        models.update(state.shaft.model,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,state.shaft.axis),display);AllPartialModels.extract(GantryClient.COGS,state.cogs);
        var visual=state.facing.getAxisDirection()==Direction.AxisDirection.POSITIVE?entity.getBlockPos():entity.getBlockPos().relative(state.facing.getOpposite());state.cogAngle=(float)(((entity.getLevel().getGameTime()+partial)*entity.getSpeed()*.15+offset(visual,state.shaft.axis))%360);
        var gantryAxis=GantryCarriageBlock.getValidGantryShaftAxis(block);if(gantryAxis==Direction.Axis.X&&state.facing==Direction.UP||gantryAxis==Direction.Axis.Y&&(state.facing==Direction.NORTH||state.facing==Direction.EAST))state.cogAngle=-state.cogAngle;
    }
    public static void transformCogs(PoseStack pose,State state){pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw(state.facing));pose.rotateDegrees(Axis.XP,state.facing==Direction.UP?0:state.facing==Direction.DOWN?180:90);pose.rotateDegrees(Axis.YP,state.alongFirst^(state.facing.getAxis()==Direction.Axis.X)?0:90);pose.translate(0,-9/16d,0);pose.rotateDegrees(Axis.XP,-state.cogAngle);pose.translate(0,9/16d,0);pose.translate(-.5,-.5,-.5);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){shaft.submit(state.shaft,pose,collector,camera);pose.pushPose();transformCogs(pose,state);state.cogs.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    @Override public boolean shouldRenderOffScreen(){return true;}
}
