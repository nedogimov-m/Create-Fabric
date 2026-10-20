package com.simibubi.create.infrastructure.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.*;

/** Source four genuine block models, frozen during extraction and drawn by native RenderPearl PIP. */
public final class CreateMenuArtwork {
    public record Gear(PartialModelRenderState model,Matrix4fc transform,boolean large,int side,float angle){public Gear{transform=new Matrix4f(transform);}}
    public record State(List<Gear> gears,float intro,long time,int viewportWidth,int viewportHeight,int x0,int y0,int x1,int y1,float scale,Matrix3x2fc pose,ScreenRectangle scissorArea,ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State{gears=List.copyOf(gears);pose=new Matrix3x2f(pose);}
    }
    public static void extract(GuiGraphicsExtractor gui,int screenWidth,float intro,long time){
        int width=224,height=112,x=(screenWidth-width)/2;var gears=new ArrayList<Gear>();
        var large=new PartialModelRenderState();var small=new PartialModelRenderState();
        AllPartialModels.extract(AllBlocks.LARGE_COGWHEEL.defaultBlockState(),large);AllPartialModels.extract(AllBlocks.COGWHEEL.defaultBlockState(),small);
        for(int side:new int[]{1,-1}){
            var base=new PoseStack();base.translate(width/2f,60,200);base.scale(24*side,24*side,32);base.translate(-1.75*(intro*intro/2f+.5f),.25,0);base.rotateDegrees(Axis.XP,45);
            float largeAngle=(time%11520)/32f*side,smallAngle=(time%5760)/-16f*side+22.5f;
            gears.add(new Gear(large,modelTransform(base,largeAngle),true,side,largeAngle));base.translate(-1,0,-1);gears.add(new Gear(small,modelTransform(base,smallAngle),false,side,smallAngle));
        }
        var pose=new Matrix3x2f(gui.pose());var scissor=new ScreenRectangle(x,0,width,height);
        var state=new State(gears,intro,time,width,height,x,0,x+width,height,1,pose,scissor,PictureInPictureRenderState.getBounds(x,0,x+width,height,pose,scissor));
        ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(state);
    }
    private static Matrix4fc modelTransform(PoseStack base,float angle){
        base.pushPose();try{base.scale(1,-1,1);base.translate(.5,.5,.5);base.rotateDegrees(Axis.YP,angle);base.translate(-.5,-.5,-.5);return new Matrix4f(base.last().pose());}finally{base.popPose();}
    }
    public static final class Renderer extends PictureInPictureRenderer<State>{
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected String getTextureLabel(){return "Create source animated menu cogwheels";}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            pose.translate(-state.viewportWidth()/2f,-state.viewportHeight(),0);
            for(var gear:state.gears()){pose.pushPose();try{pose.mulPose(gear.transform());gear.model().submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);}finally{pose.popPose();}}
        }
    }
    public static void register(){PictureInPictureRendererRegistry.register(context->new Renderer());}
    private CreateMenuArtwork(){}
}
