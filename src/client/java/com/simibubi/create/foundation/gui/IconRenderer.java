package com.simibubi.create.foundation.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Typed native adapter for the same source icon data; common API resolves no client classes. */
public final class IconRenderer {
    public static void render(AllIcons icon,GuiGraphicsExtractor gui,int x,int y){render(icon,gui,x,y,0xFFFFFFFF);}
    public static void render(AllIcons icon,GuiGraphicsExtractor gui,int x,int y,int color){
        gui.blit(RenderPipelines.GUI_TEXTURED,AllIcons.ICON_ATLAS,x,y,icon.getIconX(),icon.getIconY(),16,16,256,256,color);
    }
    public static void submit(AllIcons icon,PoseStack pose,SubmitNodeCollector collector,int color){
        float u=icon.getIconX()/256f,v=icon.getIconY()/256f;
        collector.submitCustomGeometry(pose,net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(AllIcons.ICON_ATLAS),(matrix,vertices)->{
            float[][] points={{0,0,u,v},{0,1,u,v+1/16f},{1,1,u+1/16f,v+1/16f},{1,0,u+1/16f,v}};
            for(var point:points)vertices.addVertex(matrix,point[0],point[1],0).setColor(color).setUv(point[2],point[3]).setLight(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setNormal(matrix,0,0,-1);
        });
    }
    private IconRenderer(){}
}
