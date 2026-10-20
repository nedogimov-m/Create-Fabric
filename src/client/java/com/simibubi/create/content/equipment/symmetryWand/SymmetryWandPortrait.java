package com.simibubi.create.content.equipment.symmetryWand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.*;

/** Pinned mirror model through the native RenderPearl picture-in-picture pipeline. */
public final class SymmetryWandPortrait {
    public record State(PartialModelRenderState model,Matrix4f transform,int x0,int y0,int x1,int y1,float scale,ScreenRectangle scissorArea,ScreenRectangle bounds) implements PictureInPictureRenderState{}
    public static final class Renderer extends PictureInPictureRenderer<State>{
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){net.minecraft.client.Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI);pose.mulPose(state.transform);state.model.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);}
        @Override protected float getTranslateY(int height,int scale){return height/2f;}
        @Override protected String getTextureLabel(){return "create symmetry mirror";}
    }
    public static void extract(GuiGraphicsExtractor gui,SymmetryMirror mirror,int x,int y){var pose=new PoseStack();pose.scale(1,-1,1);pose.rotate(Axis.of(new Vector3f(.3f,1,0).normalize()).rotationDegrees(-22.5f));pose.translate(-.5,-.5,-.5);SymmetryMirrorClient.applyModelTransform(mirror,pose);int x0=x-18,y0=y-20,x1=x+18,y1=y+20;((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(new State(SymmetryMirrorClient.extract(mirror),new Matrix4f(pose.last().pose()),x0,y0,x1,y1,20,null,PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null)));}
    public static void register(){PictureInPictureRendererRegistry.register(context->new Renderer());}
    private SymmetryWandPortrait(){}
}
