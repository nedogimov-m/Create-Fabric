package com.simibubi.create.content.logistics.redstoneRequester;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RedstoneRequesterScreen extends AbstractContainerScreen<RedstoneRequesterMenu> {
    private EditBox address;
    private Button partialButton,fullButton;
    private boolean partial;
    private final List<Integer> amounts=new ArrayList<>();
    public RedstoneRequesterScreen(RedstoneRequesterMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,238,232);partial=menu.owner().allowPartialRequests;for(int i=0;i<9;i++)amounts.add(menu.amount(i)); }
    @Override protected void init() {
        super.init();address=new EditBox(font,leftPos+55,topPos+68,110,10,Component.translatable("create.gui.redstone_requester.requester_address"));address.setBordered(false);address.setTextColor(0xFF555555);address.setMaxLength(25);address.setValue(menu.owner().encodedTargetAdress);addRenderableWidget(address);
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+202,topPos+95,18,18).tooltip(Tooltip.create(Component.translatable("gui.done"))).build());
        partialButton=addRenderableWidget(Button.builder(Component.literal("½"),button->{partial=true;updateButtons();}).bounds(leftPos+12,topPos+95,18,18).tooltip(Tooltip.create(Component.translatable("create.gui.redstone_requester.allow_partial"))).build());
        fullButton=addRenderableWidget(Button.builder(Component.literal("1"),button->{partial=false;updateButtons();}).bounds(leftPos+30,topPos+95,18,18).tooltip(Tooltip.create(Component.translatable("create.gui.redstone_requester.dont_allow_partial"))).build());
        updateButtons();setInitialFocus(address);
    }
    private void updateButtons() { partialButton.active=!partial;fullButton.active=partial; }
    @Override protected void containerTick() { super.containerTick();for(int i=0;i<9;i++)if(menu.getSlot(i).getItem().isEmpty())amounts.set(i,1); }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(vertical!=0)for(int i=0;i<9;i++)if(isHovering(27+20*i,28,16,16,x,y)){if(!menu.getSlot(i).getItem().isEmpty())amounts.set(i,Math.clamp(amounts.get(i)+(vertical>0?1:-1)*(minecraft.hasShiftDown()?10:1),1,256));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void onClose() { ClientPlayNetworking.send(new RedstoneRequesterMenu.Update(menu.containerId,address.getValue(),partial,List.copyOf(amounts)));super.onClose(); }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { if(event.isEscape())return super.keyPressed(event);if(getFocused() instanceof EditBox edit&&edit.canConsumeInput()){edit.keyPressed(event);return true;}return super.keyPressed(event); }
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float delta) { super.extractBackground(gui,mouseX,mouseY,delta);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/requester.png"),leftPos+3,topPos,16,16,232,120,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos-3,topPos+124,0,0,176,108,256,256); }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY) { gui.text(font,title,117-font.width(title)/2,4,0xFF3D3C48,false);gui.text(font,playerInventoryTitle,5,130,0xFF303030,false); }
    @Override protected void extractSlot(GuiGraphicsExtractor gui,net.minecraft.world.inventory.Slot slot,int mouseX,int mouseY) { super.extractSlot(gui,slot,mouseX,mouseY);if(slot.index<9&&!slot.getItem().isEmpty())gui.itemDecorations(font,slot.getItem(),slot.x,slot.y,Integer.toString(amounts.get(slot.index))); }
    public EditBox addressBox() { return address; }
    public net.minecraft.world.phys.Vec2 slotPosition(int index) { var slot=menu.getSlot(index);return new net.minecraft.world.phys.Vec2(leftPos+slot.x+8,topPos+slot.y+8); }
    public static void register() {
        net.minecraft.client.gui.screens.MenuScreens.register(RedstoneRequesterMenu.TYPE,RedstoneRequesterScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(RedstoneRequesterEffectPacket.TYPE,(packet,context)->{if(LoadedLevelAccess.blockEntity(context.client().level,packet.pos()) instanceof RedstoneRequesterBlockEntity requester)requester.playEffect(packet.success());});
    }
}
