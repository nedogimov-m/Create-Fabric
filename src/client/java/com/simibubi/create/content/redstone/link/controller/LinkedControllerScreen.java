package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.Create;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class LinkedControllerScreen extends AbstractContainerScreen<LinkedControllerMenu>{
    public LinkedControllerScreen(LinkedControllerMenu menu,Inventory inventory,Component title){super(menu,inventory,title,179,221);}
    @Override protected void init(){super.init();addRenderableWidget(Button.builder(Component.literal("×"),button->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+117,topPos+85,18,18).tooltip(Tooltip.create(Component.translatable("controls.reset"))).build());addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+146,topPos+85,18,18).build());}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/curiosities_2.png"),leftPos,topPos,0,0,179,109,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+1,topPos+113,0,0,176,108,256,256);}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,15,4,0xFF592424,false);gui.text(font,playerInventoryTitle,8,119,0xFF404040,false);}
    @Override protected void containerTick(){super.containerTick();if(!menu.stillValid(minecraft.player))onClose();}
}
