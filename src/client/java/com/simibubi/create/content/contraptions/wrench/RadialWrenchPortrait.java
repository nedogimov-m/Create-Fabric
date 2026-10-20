package com.simibubi.create.content.contraptions.wrench;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** RenderPearl PIP owns detached native models and BE render state; no live BE/world mutation or GL calls. */
final class RadialWrenchPortrait {
    record State(PartialModelRenderState model,BlockEntityRenderState entity,BlockEntityRenderer renderer,
                 CameraRenderState camera,float pitch,float yaw,int x0,int y0,int x1,int y1,float scale,
                 ScreenRectangle scissorArea,ScreenRectangle bounds) implements PictureInPictureRenderState {}
    @SuppressWarnings("unchecked")
    private static final class Renderer extends PictureInPictureRenderer<State> {
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            pose.pushPose();
            try{
                pose.scale(1,-1,-1);pose.rotateDegrees(Axis.XP,state.pitch);pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(-.5,-.5,-.5);
                state.model.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
                if(state.entity!=null&&state.renderer!=null)state.renderer.submit(state.entity,pose,collector,state.camera);
            }finally{pose.popPose();}
        }
        @Override protected String getTextureLabel(){return "Create radial wrench block rotation";}
        @Override protected float getTranslateY(int height,int scale){return height/2f;}
    }
    @SuppressWarnings("unchecked")
    static void extract(GuiGraphicsExtractor gui,BlockState selected,BlockEntity owner,int x,int y,float partial,float pitch,float yaw){
        var client=Minecraft.getInstance();var model=new PartialModelRenderState();AllPartialModels.extract(selected,model);
        BlockEntityRenderState extracted=null;BlockEntityRenderer renderer=null;
        if(owner!=null&&!owner.isRemoved()){
            renderer=client.getBlockEntityRenderDispatcher().getRenderer(owner);
            if(renderer!=null){extracted=renderer.createRenderState();renderer.extractRenderState(owner,extracted,partial,Vec3.atCenterOf(owner.getBlockPos()).add(0,0,4),null);extracted.lightCoords=LightCoordsUtil.FULL_BRIGHT;}
        }
        var camera=new CameraRenderState();camera.pos=Vec3.ZERO;camera.blockPos=BlockPos.ZERO;camera.initialized=true;camera.xRot=pitch;camera.yRot=yaw;camera.orientation.rotationYXZ((float)Math.toRadians(yaw),(float)Math.toRadians(pitch),0);
        int x0=x-28,y0=y-28,x1=x+28,y1=y+28;
        var state=new State(model,extracted,renderer,camera,pitch,yaw,x0,y0,x1,y1,24,null,PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null));
        ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(state);
    }
    static void register(){PictureInPictureRendererRegistry.register(context->new Renderer());}
    private RadialWrenchPortrait(){}
}
