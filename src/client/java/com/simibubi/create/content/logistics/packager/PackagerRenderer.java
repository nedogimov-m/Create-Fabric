package com.simibubi.create.content.logistics.packager;

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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class PackagerRenderer implements BlockEntityRenderer<PackagerBlockEntity,PackagerRenderer.State> {
    private final ItemModelResolver items;
    public PackagerRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState hatch=new PartialModelRenderState(),tray=new PartialModelRenderState();
        public final ItemStackRenderState box=new ItemStackRenderState();
        public Direction facing;public float offset;public boolean open;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(PackagerBlockEntity packager,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(packager,state,partial,camera,overlay);state.facing=packager.getBlockState().getValue(PackagerBlock.FACING).getOpposite();state.offset=packager.getTrayOffset(partial);state.open=packager.animationTicks>(packager.animationInward?1:5)&&packager.animationTicks<PackagerBlockEntity.CYCLE-(packager.animationInward?5:1);
        AllPartialModels.extract(state.open?AllPartialModels.PACKAGER_HATCH_OPEN:AllPartialModels.PACKAGER_HATCH_CLOSED,state.hatch);AllPartialModels.extract(packager.getBlockState().is(AllBlocks.PACKAGER)?AllPartialModels.PACKAGER_TRAY:AllPartialModels.REPACKAGER_TRAY,state.tray);state.box.clear();var rendered=packager.getRenderedBox();if(!rendered.isEmpty())items.updateForTopItem(state.box,rendered,ItemDisplayContext.FIXED,packager.getLevel(),null,0);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        var facing=state.facing;float yaw=switch(facing){case NORTH->180;case WEST->90;case EAST->270;default->0;};float vertical=facing==Direction.UP?-90:facing==Direction.DOWN?90:0;
        pose.pushPose();pose.translate(facing.getStepX()*.49999,facing.getStepY()*.49999,facing.getStepZ()*.49999);pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,facing==Direction.WEST?270:facing==Direction.EAST?90:yaw);pose.rotateDegrees(Axis.XP,vertical);pose.translate(-.5,-.5,-.5);state.hatch.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();pose.translate(facing.getStepX()*state.offset,facing.getStepY()*state.offset,facing.getStepZ()*state.offset);pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,yaw);pose.translate(-.5,-.5,-.5);state.tray.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        if(!state.box.isEmpty()){pose.pushPose();pose.translate(.5+facing.getStepX()*state.offset,.5+facing.getStepY()*state.offset,.5+facing.getStepZ()*state.offset);pose.rotateDegrees(Axis.YP,yaw);pose.translate(0,2f/16,0);pose.scale(1.49f,1.49f,1.49f);state.box.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
}
