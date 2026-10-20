package com.simibubi.create.content.equipment.toolbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.util.LightCoordsUtil;

/** Actual animated source screen preview in the native GUI offscreen renderer. */
public final class ToolboxPortrait {
    public record State(ToolBoxVisual.Geometry geometry,int x0,int y0,int x1,int y1,float scale,ScreenRectangle scissorArea,ScreenRectangle bounds)implements PictureInPictureRenderState{}
    public static final class Renderer extends PictureInPictureRenderer<State>{
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){net.minecraft.client.Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI);pose.scale(1,-1,1);pose.rotateDegrees(Axis.XP,-22);pose.rotateDegrees(Axis.YP,-202);pose.translate(-.5,-.5,-.5);ToolBoxVisual.submit(state.geometry,pose,collector,LightCoordsUtil.FULL_BRIGHT,true);}
        @Override protected String getTextureLabel(){return "Create toolbox preview";}
        @Override protected float getTranslateY(int height,int scale){return height/2f;}
    }
    public static void extract(GuiGraphicsExtractor gui,ToolboxBlockEntity box,int x,int y,float partial){int x0=x-40,y0=y-50,x1=x+40,y1=y+30;((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(new State(ToolBoxVisual.extract(box,partial,true),x0,y0,x1,y1,50,null,PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null)));}
    public static void register(){PictureInPictureRendererRegistry.register(context->new Renderer());}
    private ToolboxPortrait(){}
}
