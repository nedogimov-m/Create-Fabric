package com.simibubi.create.content.equipment.zapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
public final class ZapperBlockPortrait {
    public record State(PartialModelRenderState block,int x0,int y0,int x1,int y1,float scale,ScreenRectangle scissorArea,ScreenRectangle bounds)implements PictureInPictureRenderState{}
    public static class Renderer extends PictureInPictureRenderer<State>{
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){pose.scale(1,-1,1);pose.rotateDegrees(Axis.XP,-25);pose.rotateDegrees(Axis.YP,-45);pose.translate(-.5,-.5,-.5);state.block.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);}
        @Override protected String getTextureLabel(){return "Create Worldshaper selected original block";}
        @Override protected float getTranslateY(int height,int scale){return height/2f;}
    }
    public static void extract(GuiGraphicsExtractor gui,BlockState block,int x,int y){if(block.isAir())return;var model=new PartialModelRenderState();AllPartialModels.extract(block,model);int x0=x-22,y0=y-24,x1=x+22,y1=y+20;((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(new State(model,x0,y0,x1,y1,20,null,PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null)));}
    public static void register(){PictureInPictureRendererRegistry.register(context->new Renderer());}
    private ZapperBlockPortrait(){}
}
