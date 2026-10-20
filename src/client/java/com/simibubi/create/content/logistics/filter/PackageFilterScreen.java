package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageStyles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PackageFilterScreen extends AbstractContainerScreen<PackageFilterMenu> {
    private EditBox address;
    public PackageFilterScreen(PackageFilterMenu menu,Inventory inventory,Component title){super(menu,inventory,title,230,191);}
    @Override protected void init(){
        super.init();address=new EditBox(font,leftPos+44,topPos+28,129,12,Component.empty());address.setBordered(false);address.setMaxLength(PackageFilterMenu.MAX_ADDRESS_LENGTH);address.setTextColor(0xFFFFFFFF);address.setValue(menu.address());address.setResponder(value->ClientPlayNetworking.send(new PackageFilterMenu.Update(menu.containerId,value)));addRenderableWidget(address);setInitialFocus(address);
        addRenderableWidget(Button.builder(Component.literal("×"),button->address.setValue("")).bounds(leftPos+152,topPos+55,18,18).tooltip(Tooltip.create(Component.translatable("controls.reset"))).build());
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+181,topPos+55,18,18).tooltip(Tooltip.create(Component.translatable("gui.done"))).build());
    }
    public String address(){return address.getValue();}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.isEscape())return super.keyPressed(event);if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN){setFocused(null);return true;}return address.keyPressed(event)||address.canConsumeInput()||super.keyPressed(event);}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT&&address.isMouseOver(event.x(),event.y())){address.setValue("");return true;}return super.mouseClicked(event,doubleClick);}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/filters_2.png"),leftPos,topPos,0,0,218,79,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+32,topPos+83,0,0,176,108,256,256);gui.item(PackageStyles.getDefaultBox(),leftPos+16,topPos+23);}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,(214-font.width(title))/2,5,0xFF3D3C48,false);gui.text(font,playerInventoryTitle,40,89,0xFF303030,false);}
}
