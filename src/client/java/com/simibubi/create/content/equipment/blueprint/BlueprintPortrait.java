package com.simibubi.create.content.equipment.blueprint;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Source small-partial screen portrait, captured before native GUI submission. */
public final class BlueprintPortrait {
    public record State(Mesh mesh,Matrix4fc transform,int x0,int y0,int x1,int y1,float scale,
            ScreenRectangle scissorArea,ScreenRectangle bounds) implements PictureInPictureRenderState {}

    public static final class Renderer extends PictureInPictureRenderer<State> {
        @Override public Class<State> getRenderStateClass(){return State.class;}
        @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector){
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            pose.pushPose();pose.mulPose(state.transform);
            collector.submitBlockModel(pose,ChunkSectionLayerHelper::getMovingBlockRenderType,false,List.of(),
                state.mesh,new int[0],LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
        @Override protected String getTextureLabel(){return "Create crafting blueprint preview";}
        @Override protected float getTranslateY(int height,int scale){return height/2f;}
    }

    /** x/y are the screen background's top-left, as in the original BlueprintScreen. */
    public static void extract(GuiGraphicsExtractor gui,int x,int y,float partial){
        var model=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.CRAFTING_BLUEPRINT_1x1,model);
        var transform=new PoseStack();
        // Native PIP supplies (scale,scale,-scale); retain source (scale,-scale,scale).
        transform.scale(1,-1,-1);transform.rotateDegrees(Axis.ZP,22.5f);
        transform.rotateDegrees(Axis.XP,45);transform.rotateDegrees(Axis.YP,-45);
        int centerX=x+179+20,centerY=y+109-32,x0=centerX-40,y0=centerY-40,x1=centerX+40,y1=centerY+40;
        var state=new State(model.mesh,new Matrix4f(transform.last().pose()),x0,y0,x1,y1,40,null,
            PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null));
        ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(state);
    }

    public static void register(){PictureInPictureRendererRegistry.register(context->new Renderer());}
    private BlueprintPortrait(){}
}
