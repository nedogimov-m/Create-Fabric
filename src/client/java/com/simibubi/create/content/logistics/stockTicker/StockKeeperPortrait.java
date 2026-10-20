package com.simibubi.create.content.logistics.stockTicker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

public final class StockKeeperPortrait {
    public record BurnerState(BlazeBurnerRenderer.State burner,PartialModelRenderState cage,int x0,int y0,int x1,int y1,float scale,ScreenRectangle scissorArea,ScreenRectangle bounds) implements PictureInPictureRenderState{}
    public static final class BurnerRenderer extends PictureInPictureRenderer<BurnerState>{
        @Override public Class<BurnerState> getRenderStateClass(){return BurnerState.class;}
        @Override protected void renderToTexture(BurnerState state,PoseStack pose,SubmitNodeCollector collector){
            net.minecraft.client.Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI);
            pose.scale(1,-1,1);pose.rotateDegrees(Axis.XP,-22.5f);pose.rotateDegrees(Axis.YP,-45);pose.translate(-.5,-.5,-.5);
            state.cage.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);BlazeBurnerRenderer.draw(state.burner,pose,collector);
        }
        @Override protected String getTextureLabel(){return "Create stock keeper portrait";}
        @Override protected float getTranslateY(int height,int guiScale){return height/2f;}
    }
    public static void register(){PictureInPictureRendererRegistry.register(context->new BurnerRenderer());}
    public static String extract(GuiGraphicsExtractor gui,StockTickerMenu menu,int left,int top,int height,int mouseX,int mouseY,float partial){
        var keeper=StockTickerInteractionHandler.findKeeper(menu.owner.getLevel(),menu.owner.getBlockPos());int center=left-35,bottom=top+height-47;
        if(keeper.burner()!=null){var state=BlazeBurnerRenderer.portrait(keeper.burner(),partial);var cage=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.BLAZE_CAGE,cage);int x0=center-48,y0=bottom-94,x1=center+48,y1=bottom+18;
            ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addPicturesInPictureState(new BurnerState(state,cage,x0,y0,x1,y1,48,null,PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null)));return "burner";
        }
        var entity=keeper.entity();if(entity!=null&&entity.isAlive()){
            int dx=(int)(Math.max(0,entity.getBoundingBox().getXsize()-1)*50),dy=(int)(Math.max(0,entity.getBoundingBox().getYsize()-1)*25);center-=dx;bottom-=dy;
            InventoryScreen.extractEntityInInventoryFollowsMouse(gui,center-100,bottom-100,center+100,bottom+100,50,0,mouseX,Math.clamp(mouseY,bottom-50,bottom+10),entity);return "entity";
        }return "none";
    }
    private StockKeeperPortrait(){}
}
