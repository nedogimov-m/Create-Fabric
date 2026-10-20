package com.simibubi.create.content.schematics.cannon;

import com.simibubi.create.content.schematics.client.SchematicIconButton;
import com.simibubi.create.foundation.gui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native source controls/options, genuine synchronized cannon status, fuel and checklist outputs. */
public final class SchematicannonScreen extends AbstractContainerScreen<SchematicannonMenu> {
    private final SchematicIconButton[] states=new SchematicIconButton[3],replace=new SchematicIconButton[4];
    private final List<SchematicIconButton> settings=new ArrayList<>();
    private SchematicIconButton missing,entities;
    private boolean show;
    public SchematicannonScreen(SchematicannonMenu menu,Inventory inventory,Component title){super(menu,inventory,title,213,251);}
    private SchematicIconButton button(int x,int y,AllIcons icon,String key,Runnable action){return addRenderableWidget(new SchematicIconButton(leftPos+x,topPos+y,icon,Component.translatable("create."+key),action));}
    @Override protected void init(){
        super.init();settings.clear();
        states[2]=button(75,85,AllIcons.I_PLAY,"gui.schematicannon.play",()->send(2));states[1]=button(93,85,AllIcons.I_PAUSE,"gui.schematicannon.pause",()->send(1));states[0]=button(111,85,AllIcons.I_STOP,"gui.schematicannon.stop",()->send(0));
        button(180,111,AllIcons.I_CONFIRM,"action.confirm",this::onClose);button(8,111,AllIcons.I_PLACEMENT_SETTINGS,"gui.schematicannon.showOptions",()->{show=!show;updateButtons();});
        AllIcons[] icons={AllIcons.I_DONT_REPLACE,AllIcons.I_REPLACE_SOLID,AllIcons.I_REPLACE_ANY,AllIcons.I_REPLACE_EMPTY};String[] labels={"dontReplaceSolid","replaceWithSolid","replaceWithAny","replaceWithEmpty"};
        for(int i=0;i<4;i++){int option=i;replace[i]=button(33+i*18,111,icons[i],"gui.schematicannon.option."+labels[i],()->send(10+option));settings.add(replace[i]);}
        missing=button(111,111,AllIcons.I_SKIP_MISSING,"gui.schematicannon.option.skipMissing",()->send(20));entities=button(135,111,AllIcons.I_SKIP_BLOCK_ENTITIES,"gui.schematicannon.option.skipBlockEntities",()->send(21));settings.add(missing);settings.add(entities);updateButtons();
    }
    private void send(int option){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,option);}
    private void updateButtons(){for(var setting:settings)setting.visible=show;var be=menu.owner;for(int i=0;i<3;i++){states[i].selected=be.state.ordinal()==i;states[i].active=be.state.ordinal()!=i&&(i!=1||be.state==SchematicannonBlockEntity.State.RUNNING);}for(int i=0;i<4;i++)replace[i].selected=be.replaceMode==i;missing.selected=be.skipMissing;entities.selected=!be.replaceBlockEntities;}
    @Override protected void containerTick(){super.containerTick();updateButtons();}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        super.extractBackground(gui,mouseX,mouseY,partial);AllGuiTextures.SCHEMATICANNON_TOP.render(gui,leftPos,topPos);AllGuiTextures.SCHEMATICANNON_BOTTOM.render(gui,leftPos,topPos+42);AllGuiTextures.PLAYER_INVENTORY.render(gui,leftPos+29,topPos+143);
        var be=menu.owner;if(be.hasCreativeCrate)AllGuiTextures.SCHEMATICANNON_FUEL_CREATIVE.render(gui,leftPos+36,topPos+19);else AllGuiTextures.SCHEMATICANNON_FUEL.cropped(gui,leftPos+36,topPos+19,(int)(47*Math.clamp(be.remainingFuel/(float)Math.max(1,be.getShotsPerGunpowder()),0,1)),16);
        AllGuiTextures.SCHEMATICANNON_CHECKLIST_PROGRESS.cropped(gui,leftPos+154,topPos+20,(int)(16*be.bookPrintingProgress),14);AllGuiTextures.SCHEMATICANNON_PROGRESS.cropped(gui,leftPos+44,topPos+64,(int)(114*be.schematicProgress),16);
        if(be.missingItem!=null&&!be.missingItem.isEmpty())gui.item(be.missingItem,leftPos+128,topPos+49);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,(205-font.width(title))/2,4,0xFF505050,false);var text=Component.translatable("create.schematicannon.status."+menu.owner.statusMsg);gui.text(font,text,103-font.width(text)/2,53,0xFFDDEEFF,false);gui.text(font,playerInventoryTitle,37,149,0xFF404040,false);}
    @Override protected void extractTooltip(GuiGraphicsExtractor gui,int x,int y){
        var be=menu.owner;
        if(isHovering(36,19,47,16,x,y)){long backup=(long)be.remainingFuel+(long)be.inventory.getItem(4).getCount()*be.getShotsPerGunpowder();gui.setComponentTooltipForNextFrame(font,List.of(Component.translatable("create.gui.schematicannon.gunpowderLevel",be.hasCreativeCrate?100:(int)(100*Math.clamp(be.remainingFuel/(float)Math.max(1,be.getShotsPerGunpowder()),0,1))),Component.translatable("create.gui.schematicannon.shotsRemaining",be.remainingFuel),Component.translatable("create.gui.schematicannon.shotsRemainingWithBackup",backup)),x,y);return;}
        if(be.missingItem!=null&&isHovering(128,49,16,16,x,y)){gui.setTooltipForNextFrame(font,be.missingItem,x,y);return;}
        if(hoveredSlot!=null&&!hoveredSlot.hasItem()){var name=switch(hoveredSlot.index){case 0->"schematic";case 2->"listPrinter";case 4->"gunpowder";default->null;};if(name!=null){gui.setTooltipForNextFrame(Component.translatable("create.gui.schematicannon.slot."+name),x,y);return;}}
        super.extractTooltip(gui,x,y);
    }
}
