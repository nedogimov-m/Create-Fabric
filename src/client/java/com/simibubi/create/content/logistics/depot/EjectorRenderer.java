package com.simibubi.create.content.logistics.depot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Source lid and genuine owned ballistic stock rendered through native Fabric/Vulkan submissions. */
public final class EjectorRenderer implements BlockEntityRenderer<EjectorBlockEntity,EjectorRenderer.State> {
    private final ItemModelResolver models;
    public EjectorRenderer(BlockEntityRendererProvider.Context context){models=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState shaft=new PartialModelRenderState(),lid=new PartialModelRenderState();
        public Matrix4f shaftPose=new Matrix4f(),lidPose=new Matrix4f();
        public List<DepotRenderer.ItemPose> stock=List.of(),flying=List.of();
    }
    @Override public State createRenderState(){return new State();}
    public static void applyLidAngle(PoseStack pose,float facing,float angle){
        pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,180+facing);pose.translate(-.5,-.5,-.5);
        pose.translate(0,11.25/16f,.75/16f);pose.rotateDegrees(Axis.XP,-angle);pose.translate(0,-11.25/16f,-.75/16f);
    }
    @Override public void extractRenderState(EjectorBlockEntity ejector,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(ejector,state,partial,camera,overlay);
        Direction.Axis axis=ejector.getFacing().getClockWise().getAxis();
        AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis),state.shaft);
        AllPartialModels.extract(AllPartialModels.EJECTOR_TOP,state.lid);
        int parity=(axis==Direction.Axis.X?0:ejector.getBlockPos().getX())+ejector.getBlockPos().getY()+(axis==Direction.Axis.Z?0:ejector.getBlockPos().getZ());
        float spin=ejector.getAngle(partial)+(parity%2==0?22.5f:0),facing=AngleHelper.horizontalAngle(ejector.getFacing());
        var pose=new PoseStack();pose.translate(.5,.5,.5);pose.rotateDegrees(axis==Direction.Axis.X?Axis.XP:Axis.ZP,spin);pose.translate(-.5,-.5,-.5);state.shaftPose=new Matrix4f(pose.last().pose());
        pose=new PoseStack();applyLidAngle(pose,facing,ejector.getLidProgress(partial)*70);state.lidPose=new Matrix4f(pose.last().pose());
        pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,-180-facing);pose.translate(-.5,-.5,-.5);
        state.stock=DepotRenderer.extract(ejector.depotBehaviour,models,pose,partial,camera);
        var flying=new ArrayList<DepotRenderer.ItemPose>();
        float maximum=(float)(ejector.getEarlyTarget()!=null?ejector.getEarlyTargetTime():ejector.getLauncher().getTotalFlyingTicks());
        for(var flight:ejector.getLaunchedItems()){
            float time=flight.getFirst()+partial;var stack=flight.getValue();if(stack.isEmpty()||time>maximum)continue;
            var item=new ItemStackRenderState();models.updateForTopItem(item,stack,ItemDisplayContext.FIXED,ejector.getLevel(),null,0);
            pose=new PoseStack();pose.translate(ejector.getLaunchedItemLocation(time).subtract(Vec3.atLowerCornerOf(ejector.getBlockPos())));pose.translate(0,2/16f,-1/16f);
            if(PackageItem.isPackage(stack)){pose.translate(0,4/16f,0);pose.scale(1.5f,1.5f,1.5f);pose.rotateDegrees(Axis.YP,time*20);}
            else{pose.scale(.5f,.5f,.5f);pose.rotateDegrees(Axis.YP,facing);pose.rotateDegrees(Axis.XP,time*40);}
            pose.translate(0,-2/16f,1/16f);flying.add(new DepotRenderer.ItemPose(item,new Matrix4f(pose.last().pose())));
        }
        state.flying=List.copyOf(flying);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.mulPose(state.shaftPose);state.shaft.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();pose.mulPose(state.lidPose);state.lid.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        DepotRenderer.submitItems(state.flying,pose,collector,state.lightCoords);DepotRenderer.submitItems(state.stock,pose,collector,state.lightCoords);
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
}
