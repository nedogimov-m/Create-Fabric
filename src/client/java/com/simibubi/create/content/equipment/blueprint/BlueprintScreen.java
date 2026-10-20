package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native extraction/menu synchronization for the source blueprint editor. */
public final class BlueprintScreen extends AbstractContainerScreen<BlueprintMenu> {
 public BlueprintScreen(BlueprintMenu menu,Inventory inventory,Component title){super(menu,inventory,title,179,221);}
 @Override protected void init(){super.init();addRenderableWidget(Button.builder(Component.literal("×"),button->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+117,topPos+85,18,18).tooltip(Tooltip.create(Component.translatable("controls.reset"))).build());addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+146,topPos+85,18,18).tooltip(Tooltip.create(Component.translatable("gui.done"))).build());}
 @Override protected void containerTick(){super.containerTick();if(!menu.stillValid(minecraft.player))onClose();}
 @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/curiosities_2.png"),leftPos,topPos,0,109,179,109,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+1,topPos+113,0,0,176,108,256,256);BlueprintPortrait.extract(gui,leftPos,topPos,partial);}
 @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,15,4,0xFFFFFFFF,false);gui.text(font,playerInventoryTitle,8,119,0xFF404040,false);}
 @Override protected void extractTooltip(GuiGraphicsExtractor gui,int mouseX,int mouseY){
  if(!menu.getCarried().isEmpty()||hoveredSlot==null||hoveredSlot.container==menu.playerInventory){super.extractTooltip(gui,mouseX,mouseY);return;}
  int slot=hoveredSlot.getContainerSlot();if(slot<0||slot>10){super.extractTooltip(gui,mouseX,mouseY);return;}var lines=new ArrayList<Component>();if(hoveredSlot.hasItem())lines.add(hoveredSlot.getItem().getHoverName());String kind=slot<9?"crafting_slot":slot==9?"display_slot":"secondary_display_slot";lines.add(Component.translatable("create.crafting_blueprint."+kind).withStyle(ChatFormatting.GOLD));if(slot<9)lines.add(Component.translatable("create.crafting_blueprint.filter_items_viable").withStyle(ChatFormatting.GRAY));else if(slot==9&&hoveredSlot.hasItem())lines.add(Component.translatable("create.crafting_blueprint."+(menu.inferredIcon()?"inferred":"manually_assigned")).withStyle(ChatFormatting.GRAY));else if(slot==10)lines.add(Component.translatable("create.crafting_blueprint.optional").withStyle(ChatFormatting.GRAY));gui.setComponentTooltipForNextFrame(font,lines,mouseX,mouseY);
 }
}
