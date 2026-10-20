package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class FactoryPanelScreen extends AbstractContainerScreen<FactoryPanelMenu> {
    private EditBox address,amount,expiry;
    private Button units;
    private boolean items,reset,clear,resetLinks;
    private int output;
    private List<ItemStack> craft;
    private final List<FactoryPanelConnection> inputs=new ArrayList<>();
    public FactoryPanelScreen(FactoryPanelMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,416,211);items=menu.panel().upTo;output=menu.panel().recipeOutput;craft=menu.panel().activeCraftingArrangement.stream().map(ItemStack::copy).toList();menu.panel().targetedBy.values().forEach(c->inputs.add(new FactoryPanelConnection(c.from,c.amount,c.arrowBendMode))); }
    @Override protected void init() {
        super.init();var panel=menu.panel();
        address=new EditBox(font,leftPos+60,topPos+109,108,12,Component.translatable("create.gui.factory_panel.recipe_address"));address.setBordered(false);address.setTextColor(0xFF555555);address.setMaxLength(25);address.setValue(panel.recipeAddress);addRenderableWidget(address);
        amount=number(36,141,32,panel.count,100);expiry=number(130,141,28,panel.promiseClearingInterval,30);
        units=button(items?"#":"▤",75,138,31,()->{items=!items;units.setMessage(Component.literal(items?"#":"▤"));},"create.factory_panel.target_amount");
        button("✓",194,138,18,this::onClose,"create.gui.factory_panel.save_and_close");button("×",174,138,18,()->{reset=true;onClose();},"create.gui.factory_panel.reset");
        button("▴",110,138,18,()->{clear=true;send(null);clear=false;},"create.gui.factory_panel.left_click_reset");
        if(!panel.panelBE().restocker){button("+",55,47,18,()->{send(null);FactoryPanelConnectionHandler.start(menu.position,false);onClose();},"create.gui.factory_panel.connect_input");button("↗",55,67,18,()->{send(null);FactoryPanelConnectionHandler.start(menu.position,true);onClose();},"create.gui.factory_panel.relocate");}
        if(!menu.autoCraft.isEmpty())button("▦",55,27,18,()->{craft=craft.isEmpty()?menu.autoCraft.stream().map(ItemStack::copy).toList():List.of();if(!craft.isEmpty()){output=menu.autoOutput;var counts=new HashMap<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant,Integer>();for(var stack:craft)if(!stack.isEmpty())counts.merge(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(stack),1,Integer::sum);for(var input:inputs){var source=FactoryPanelBehaviour.at(minecraft.level,input.from);var key=source==null?net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank():net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(source.getFilter());input.amount=counts.getOrDefault(key,0);counts.remove(key);}}},"create.gui.factory_panel.activate_crafting");
        if(!panel.targetedByLinks.isEmpty())button("−",31,117,18,()->{resetLinks=true;send(null);resetLinks=false;},"create.gui.factory_panel.left_click_disconnect");
        setInitialFocus(address);
    }
    private EditBox number(int x,int y,int width,int value,int max) { var edit=new EditBox(font,leftPos+x,topPos+y,width,13,Component.empty());edit.setMaxLength(4);edit.setValue(Integer.toString(value));addRenderableWidget(edit);return edit; }
    private Button button(String value,int x,int y,int width,Runnable run,String tooltip) { return addRenderableWidget(Button.builder(Component.literal(value),button->run.run()).bounds(leftPos+x,topPos+y,width,18).tooltip(Tooltip.create(Component.translatable(tooltip))).build()); }
    private int amount() { try{return Math.clamp(Integer.parseInt(amount.getValue()),0,100);}catch(NumberFormatException e){return menu.panel().count;} }
    private int expiry() { try{return Math.clamp(Integer.parseInt(expiry.getValue()),-1,30);}catch(NumberFormatException e){return -1;} }
    private void send(FactoryPanelPosition remove) { var data=new CompoundTag();data.putString("Address",address.getValue());data.putInt("Amount",amount());data.putBoolean("Items",items);data.putInt("Output",output);data.putInt("Expiry",expiry());data.putBoolean("Reset",reset);data.putBoolean("Clear",clear);data.putBoolean("ResetLinks",resetLinks);data.store("Inputs",FactoryPanelConnection.CODEC.listOf(),inputs);data.store("Craft",ItemStack.OPTIONAL_CODEC.listOf(),craft);if(remove!=null)data.store("Remove",FactoryPanelPosition.CODEC,remove);ClientPlayNetworking.send(new FactoryPanelMenu.Update(menu.containerId,data)); }
    @Override public void onClose() { send(null);super.onClose(); }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { if(event.isEscape())return super.keyPressed(event);if(getFocused() instanceof EditBox edit&&edit.canConsumeInput()){edit.keyPressed(event);return true;}return super.keyPressed(event); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick) {
        if(craft.isEmpty())for(int i=0;i<inputs.size();i++)if(isHovering(92+i%3*20,28+i/3*20,16,16,event.x(),event.y())){var removed=inputs.remove(i).from;send(removed);return true;}
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) { if(vertical!=0){int delta=vertical>0?1:-1;for(int i=0;i<inputs.size();i++)if(isHovering(92+i%3*20,28+i/3*20,16,16,x,y)){inputs.get(i).amount=Math.clamp(inputs.get(i).amount+delta,1,64);return true;}if(isHovering(184,48,16,16,x,y)){output=Math.clamp(output+delta,1,64);return true;}if(amount.isMouseOver(x,y)){amount.setValue(Integer.toString(Math.clamp(amount()+delta,0,100)));return true;}if(expiry.isMouseOver(x,y)){expiry.setValue(Integer.toString(Math.clamp(expiry()+delta,-1,30)));return true;}}return super.mouseScrolled(x,y,horizontal,vertical); }
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial) {
        super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/factory_gauge.png"),leftPos+24,topPos,32,0,192,96,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/factory_gauge.png"),leftPos+24,topPos+96,32,176,200,64,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+234,topPos+82,0,0,176,108,256,256);
        if(!craft.isEmpty()){for(int i=0;i<craft.size();i++)gui.item(craft.get(i),leftPos+92+i%3*20,topPos+28+i/3*20);}else for(int i=0;i<inputs.size();i++){var source=FactoryPanelBehaviour.at(minecraft.level,inputs.get(i).from);if(source!=null){var stack=source.getFilter();int x=leftPos+92+i%3*20,y=topPos+28+i/3*20;gui.item(stack,x,y);gui.itemDecorations(font,stack,x,y,Integer.toString(inputs.get(i).amount));}}
    }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY) { gui.text(font,title,120-font.width(title)/2,4,0xFF3D3C48,false);gui.text(font,playerInventoryTitle,242,88,0xFF303030,false);gui.text(font,Component.literal(Integer.toString(menu.stock())+"  ▴"+menu.promised()),36,87,0xFF303030,false); }
    @Override protected void extractSlot(GuiGraphicsExtractor gui,net.minecraft.world.inventory.Slot slot,int mouseX,int mouseY) { super.extractSlot(gui,slot,mouseX,mouseY);if(slot.index==0)gui.itemDecorations(font,slot.getItem(),slot.x,slot.y,Integer.toString(output)); }
    public EditBox addressBox() { return address; }
    public EditBox amountBox() { return amount; }
}
