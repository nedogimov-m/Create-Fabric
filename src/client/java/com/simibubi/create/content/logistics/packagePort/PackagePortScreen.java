package com.simibubi.create.content.logistics.packagePort;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class PackagePortScreen extends AbstractContainerScreen<PackagePortMenu>{
    private EditBox address;private Button sendOnly,receive;private boolean accepts;
    public PackagePortScreen(PackagePortMenu menu,Inventory inventory,Component title){super(menu,inventory,title,220,190);accepts=menu.owner.acceptsPackages;}
    @Override protected void init(){super.init();address=new EditBox(font,leftPos+23,topPos-11,180,10,Component.translatable("create.gui.package_port.catch_packages"));address.setBordered(false);address.setMaxLength(25);address.setTextColor(0xFF3D3C48);address.setValue(menu.owner.addressFilter);addRenderableWidget(address);
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+187,topPos+58,18,18).build());
        receive=addRenderableWidget(Button.builder(Component.literal("↔"),button->{accepts=true;buttons();}).bounds(leftPos+37,topPos+58,18,18).tooltip(Tooltip.create(Component.translatable("create.gui.package_port.send_and_receive"))).build());
        sendOnly=addRenderableWidget(Button.builder(Component.literal("→"),button->{accepts=false;buttons();}).bounds(leftPos+55,topPos+58,18,18).tooltip(Tooltip.create(Component.translatable("create.gui.package_port.send_only"))).build());buttons();setInitialFocus(address);
    }
    private void buttons(){receive.active=!accepts;sendOnly.active=accepts;receive.visible=sendOnly.visible=menu.owner.target!=null;}
    @Override public void onClose(){ClientPlayNetworking.send(new PackagePortMenu.Settings(menu.containerId,address.getValue(),accepts));super.onClose();}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.isEscape())return super.keyPressed(event);if(getFocused() instanceof EditBox edit&&edit.canConsumeInput()){edit.keyPressed(event);return true;}return super.keyPressed(event);}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float delta){super.extractBackground(gui,mouseX,mouseY,delta);var texture=Create.asResource("textures/gui/frogport_and_mailbox.png");gui.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos,topPos-17,0,0,214,17,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos,topPos,0,47,220,82,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+30,topPos+90,0,0,176,108,256,256);if(menu.owner.target!=null){gui.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+13,topPos+58,26,55,18,18,256,256);gui.item(menu.owner.target.getIcon(),leftPos+14,topPos+59);} }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,playerInventoryTitle,38,96,0xFF303030,false);}
    public EditBox addressBox(){return address;}
    public net.minecraft.world.phys.Vec2 slotPosition(int index){var slot=menu.getSlot(index);return new net.minecraft.world.phys.Vec2(leftPos+slot.x+8,topPos+slot.y+8);}
}
