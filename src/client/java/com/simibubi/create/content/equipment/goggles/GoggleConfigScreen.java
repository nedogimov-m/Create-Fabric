package com.simibubi.create.content.equipment.goggles;

import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** The original drag-to-position overlay screen, adapted to extracted GUI state and modern input events. */
public final class GoggleConfigScreen extends Screen {
    private int offsetX,offsetY;
    private final GoggleOverlayRenderer.Information preview;
    public GoggleConfigScreen(){
        super(GoggleTooltip.translate("gui.config.overlay1"));var lines=new ArrayList<Component>();
        for(int i=1;i<=8;i++){if(i==3||i==5||i==7)lines.add(Component.empty());var line=GoggleTooltip.translate("gui.config.overlay"+i);if(i==2||i==5||i==6)line.withStyle(ChatFormatting.GRAY);lines.add(line);}
        preview=new GoggleOverlayRenderer.Information(null,new ItemStack(AllItems.GOGGLES),List.copyOf(lines));
    }
    @Override protected void init(){offsetX=GoggleOverlaySettings.INSTANCE.overlayOffsetX;offsetY=GoggleOverlaySettings.INSTANCE.overlayOffsetY;}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){updateOffset(event.x(),event.y());return true;}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){updateOffset(event.x(),event.y());return true;}
    private void updateOffset(double x,double y){
        var layout=GoggleOverlayRenderer.layout(minecraft,preview,width,height,(int)x-width/2,(int)y-height/2);offsetX=layout.x()-width/2;offsetY=layout.y()-height/2;
    }
    @Override public void removed(){GoggleOverlaySettings.INSTANCE.overlayOffsetX=offsetX;GoggleOverlaySettings.INSTANCE.overlayOffsetY=offsetY;GoggleOverlaySettings.INSTANCE.save();}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        var layout=GoggleOverlayRenderer.layout(minecraft,preview,width,height,offsetX,offsetY);GoggleOverlayRenderer.draw(gui,minecraft,preview,layout,1);
    }
    @Override public boolean isPauseScreen(){return false;}
}
