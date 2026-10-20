package com.simibubi.create.content.trains.entity;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Actual source icon geometry submitted to the native GUI pipeline. */
public final class TrainIconRenderer {
    public static int render(TrainIconType type,int lengthOrEngine,GuiGraphicsExtractor gui,int x,int y){int width=type.getIconWidth(lengthOrEngine);gui.blit(RenderPipelines.GUI_TEXTURED,type.sheet(),x,y,type.x()+type.getIconOffset(lengthOrEngine),type.y(),width,10,256,256);return width;}
    private TrainIconRenderer(){}
}
