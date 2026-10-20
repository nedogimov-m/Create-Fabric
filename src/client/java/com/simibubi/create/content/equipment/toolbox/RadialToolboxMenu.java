package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.AllBlocks;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;

/** Source four-state eight-sector radial selector with mouse, scroll, hotbar keys and key-release commit. */
public final class RadialToolboxMenu extends Screen {
    public enum State{SELECT_BOX,SELECT_ITEM,SELECT_ITEM_UNEQUIP,DETACH}
    private static final int DEPOSIT=-7,UNEQUIP=-5;
    private final List<ToolboxBlockEntity> toolboxes;
    private State state;
    private ToolboxBlockEntity selectedBox;
    private int ticksOpen,hovered=-1,scrollSlot;
    private boolean scrollMode,committed;
    public RadialToolboxMenu(List<ToolboxBlockEntity> boxes,State state,ToolboxBlockEntity selected){super(Component.translatable("create.keyinfo.toolbelt"));toolboxes=new ArrayList<>(boxes);this.state=state;selectedBox=selected;}
    /** Resolve only the same persisted owner after native client chunk replacement. */
    private ToolboxBlockEntity currentOwner(ToolboxBlockEntity previous){
        if(previous==null||minecraft==null||previous.getLevel()!=minecraft.level||previous.getUniqueId()==null)return null;
        if(ToolboxHandler.live(previous))return previous;
        var actual=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(minecraft.level,previous.getBlockPos());
        return actual instanceof ToolboxBlockEntity current&&previous.getUniqueId().equals(current.getUniqueId())&&ToolboxHandler.live(current)?current:null;
    }
    private void refreshOwners(){
        for(int index=0;index<toolboxes.size();index++){var current=currentOwner(toolboxes.get(index));if(current!=null)toolboxes.set(index,current);}
        if(selectedBox!=null)selectedBox=currentOwner(selectedBox);
    }
    public void prevSlot(int slot){scrollSlot=Math.floorMod(slot,8);}
    private int selection(){return scrollMode?scrollSlot:hovered;}
    private boolean itemState(){return state==State.SELECT_ITEM||state==State.SELECT_ITEM_UNEQUIP;}
    private boolean selectable(int slot){if(slot<0||slot>7)return false;if(state==State.SELECT_BOX)return slot<toolboxes.size()&&ToolboxHandler.live(toolboxes.get(slot));return itemState()&&ToolboxHandler.live(selectedBox)&&!selectedBox.inventory.getFilter(slot).isEmpty()&&selectedBox.inventory.totalCount(slot)>0;}
    private void updateHover(double mouseX,double mouseY){double x=mouseX-width/2d,y=mouseY-height/2d,distance=x*x+y*y;hovered=-1;if(distance>25&&distance<10000)hovered=Math.floorMod(Mth.floor(Math.toDegrees(Math.atan2(y,x))+360+180-22.5),360)/45;if(scrollMode&&distance>150)scrollMode=false;if(state==State.SELECT_ITEM_UNEQUIP&&distance<=150)hovered=UNEQUIP;if(state==State.DETACH){hovered=x>-20&&x<20&&y>-80&&y<-20?UNEQUIP:-1;}else if(x>60&&x<100&&y>-20&&y<20)hovered=DEPOSIT;}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){int alpha=(int)(Math.min(1,(ticksOpen+partial)/20f)*144);gui.fill(0,0,width,height,alpha<<24|0x101018);}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){refreshOwners();updateHover(mouseX,mouseY);float fade=Math.clamp((ticksOpen+partial)/10f,1/512f,1);int cx=width/2,cy=height/2;Component tip=null;if(state==State.DETACH){ToolboxTextures.INACTIVE.render(gui,cx-12,cy-12);gui.item(new ItemStack(AllBlocks.TOOLBOXES.get(DyeColor.BROWN)),cx-9,cy-9);ToolboxTextures.SLOT.render(gui,cx-12,cy-52);ToolboxTextures.icon(gui,cx-9,cy-49,8,10);tip=Component.translatable("create.toolbox.outOfRange");if(selection()==UNEQUIP){ToolboxTextures.HIGHLIGHT.render(gui,cx-13,cy-53);tip=Component.translatable("create.toolbox.detach");}}else{int depositX=cx+80;ToolboxTextures.SLOT.render(gui,depositX-12,cy-12);ToolboxTextures.icon(gui,depositX-9,cy-9,11,3);if(selection()==DEPOSIT){ToolboxTextures.HIGHLIGHT.render(gui,depositX-13,cy-13);tip=Component.translatable(state==State.SELECT_BOX?"create.toolbox.depositAll":"create.toolbox.depositBox");}for(int slot=0;slot<8;slot++){double radians=Math.toRadians(slot*45-135);float radius=40-10*(1-fade)*(1-fade);int x=cx+Math.round((float)Math.cos(radians)*radius)-12,y=cy+Math.round((float)Math.sin(radians)*radius)-12;ItemStack sample=ItemStack.EMPTY;boolean active=false;Component name=null;if(state==State.SELECT_BOX&&slot<toolboxes.size()){var box=toolboxes.get(slot);sample=new ItemStack(AllBlocks.TOOLBOXES.get(box.getColor()));active=ToolboxHandler.live(box);name=box.getDisplayName();}else if(itemState()&&selectedBox!=null){sample=selectedBox.inventory.getFilter(slot);active=selectedBox.inventory.totalCount(slot)>0;name=sample.getHoverName();}(sample.isEmpty()?ToolboxTextures.EMPTY:active?ToolboxTextures.SLOT:ToolboxTextures.INACTIVE).render(gui,x,y);if(!sample.isEmpty())gui.item(sample,x+3,y+3);if(selection()==slot&&active){ToolboxTextures.HIGHLIGHT.render(gui,x-1,y-1);tip=name;}}if(state==State.SELECT_ITEM_UNEQUIP){ToolboxTextures.SLOT.render(gui,cx-12,cy-12);ToolboxTextures.icon(gui,cx-9,cy-9,scrollMode?3:8,scrollMode?1:4);if(selection()==UNEQUIP){ToolboxTextures.HIGHLIGHT.render(gui,cx-13,cy-13);tip=Component.translatable("create.toolbox.unequip",minecraft.player.getMainHandItem().getHoverName());}}}if(tip!=null)gui.centeredText(font,tip,width/2,height-72,((int)(fade*255)<<24)|0xFFFFFF);super.extractRenderState(gui,mouseX,mouseY,partial);}
    @Override public void tick(){refreshOwners();ticksOpen++;if(minecraft.player==null||minecraft.player.isSpectator()||itemState()&&!ToolboxHandler.live(selectedBox)){committed=true;onClose();}}
    private void sendEquip(int slot){if(minecraft.player!=null)ClientPlayNetworking.send(new ToolboxEquipPacket(selectedBox==null?null:selectedBox.getBlockPos(),selectedBox==null?null:selectedBox.getUniqueId(),slot,minecraft.player.getInventory().getSelectedSlot()));}
    @Override public void removed(){refreshOwners();if(committed||minecraft.player==null||minecraft.getConnection()==null)return;committed=true;int selected=selection();if(selected==DEPOSIT){if(state==State.SELECT_BOX)for(var box:toolboxes){if(ToolboxHandler.live(box))ClientPlayNetworking.send(new ToolboxDisposeAllPacket(box.getBlockPos(),box.getUniqueId()));}else if(itemState()&&ToolboxHandler.live(selectedBox))ClientPlayNetworking.send(new ToolboxDisposeAllPacket(selectedBox.getBlockPos(),selectedBox.getUniqueId()));return;}if(state==State.SELECT_BOX)return;if(state==State.DETACH){if(selected==UNEQUIP)sendEquip(selected);return;}if(selected==UNEQUIP||selectable(selected))sendEquip(selected);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){double x=mouseX-width/2d,y=mouseY-height/2d;if(x*x+y*y>150||scrollY==0||state==State.DETACH)return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);scrollMode=true;int step=scrollY>0?-1:1;for(int attempt=0;attempt<8;attempt++){scrollSlot=Math.floorMod(scrollSlot+step,8);if(selectable(scrollSlot))break;}return true;}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){refreshOwners();updateHover(event.x(),event.y());int selected=selection();if(event.button()==InputConstants.MOUSE_BUTTON_LEFT){if(selected==DEPOSIT||state==State.DETACH&&selected==UNEQUIP||itemState()&&(selected==UNEQUIP||selectable(selected))){ToolboxHandlerClient.cooldown=2;onClose();return true;}if(state==State.SELECT_BOX&&selectable(selected)){selectedBox=toolboxes.get(selected);state=State.SELECT_ITEM;return true;}}else if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT){if(state==State.SELECT_ITEM&&toolboxes.size()>1){state=State.SELECT_BOX;return true;}if(state==State.SELECT_ITEM_UNEQUIP&&selected==UNEQUIP){if(toolboxes.size()>1){sendEquip(UNEQUIP);state=State.SELECT_BOX;return true;}onClose();return true;}}return super.mouseClicked(event,twice);}
    @Override public boolean keyPressed(KeyEvent event){refreshOwners();for(int i=0;i<8;i++)if(minecraft.options.keyHotbarSlots[i].matches(event)){if(!selectable(i))return false;scrollMode=true;scrollSlot=i;if(state==State.SELECT_BOX){selectedBox=toolboxes.get(i);state=State.SELECT_ITEM;}else{ToolboxHandlerClient.cooldown=2;onClose();}return true;}return super.keyPressed(event);}
    @Override public boolean keyReleased(KeyEvent event){if(ToolboxHandlerClient.TOOLBELT.matches(event)){onClose();return true;}return super.keyReleased(event);}
    @Override public boolean isPauseScreen(){return false;}
}
