package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.content.trains.schedule.ScheduleTextures;
import com.simibubi.create.foundation.gui.widget.ScheduleWidget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ToolboxScreen extends AbstractContainerScreen<ToolboxMenu>{
    public ToolboxScreen(ToolboxMenu menu,Inventory inventory,Component title){super(menu,inventory,title,218,255);}
    @Override protected void init(){super.init();leftPos-=11;addRenderableWidget(new Button(leftPos+185,topPos+147,0,1,Component.translatable("gui.done"),()->minecraft.player.closeContainer()));addRenderableWidget(new Button(leftPos+111,topPos+69,11,3,Component.translatable("create.toolbox.depositBox"),()->ClientPlayNetworking.send(new ToolboxDisposeAllPacket(menu.contentHolder.getBlockPos(),menu.contentHolder.getUniqueId()))));}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);ToolboxTextures.BOX.render(gui,leftPos+30,topPos);ToolboxTextures.PLAYER.render(gui,leftPos,topPos+147);ToolboxPortrait.extract(gui,menu.contentHolder,leftPos+268,topPos+183,partial);}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,45,4,0xFF592424,false);}
    @Override protected void extractSlot(GuiGraphicsExtractor gui,Slot slot,int mouseX,int mouseY){if(!(slot instanceof ToolboxSlot)){super.extractSlot(gui,slot,mouseX,mouseY);return;}if(slot.getContainerSlot()%4!=0)return;int compartment=slot.getContainerSlot()/4;ItemStack sample=slot.getItem();if(sample.isEmpty())sample=menu.getFilter(compartment);if(sample.isEmpty())return;gui.item(sample,slot.x,slot.y);gui.itemDecorations(font,sample,slot.x,slot.y,Integer.toString(menu.totalCountInCompartment(compartment)));}
    @Override protected void extractTooltip(GuiGraphicsExtractor gui,int mouseX,int mouseY){if(hoveredSlot instanceof ToolboxSlot slot&&slot.getItem().isEmpty()){ItemStack filter=menu.getFilter(slot.getContainerSlot()/4);if(!filter.isEmpty()){gui.setTooltipForNextFrame(font,filter,mouseX,mouseY);return;}}super.extractTooltip(gui,mouseX,mouseY);}
    private static final class Button extends ScheduleWidget{
        private final int u,v;private final Runnable action;
        Button(int x,int y,int u,int v,Component label,Runnable action){super(x,y,18,18);this.u=u;this.v=v;this.action=action;toolTip.add(label);setMessage(label);}
        @Override public void onClick(MouseButtonEvent event,boolean twice){action.run();}
        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(active&&visible&&event.isConfirmation()){action.run();return true;}return false;}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){(isHoveredOrFocused()?ScheduleTextures.BUTTON_HOVER:ScheduleTextures.BUTTON).render(gui,getX(),getY());ToolboxTextures.icon(gui,getX()+1,getY()+1,u,v);super.extractWidgetRenderState(gui,mouseX,mouseY,partial);}
    }
}
