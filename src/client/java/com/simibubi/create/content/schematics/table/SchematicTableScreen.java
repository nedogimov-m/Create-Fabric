package com.simibubi.create.content.schematics.table;

import com.simibubi.create.content.schematics.SchematicFiles;
import com.simibubi.create.content.schematics.client.*;
import com.simibubi.create.foundation.gui.*;
import com.simibubi.create.foundation.gui.widget.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original atlas, actual local file selection, real menu fee and correlated upload progress. */
public final class SchematicTableScreen extends AbstractContainerScreen<SchematicTableMenu> {
    private ScrollInput selection;
    private Label label;
    private SchematicIconButton upload;
    private float progress,previous;
    public SchematicTableScreen(SchematicTableMenu menu,Inventory inventory,Component title){super(menu,inventory,title,214,197);}
    @Override protected void init(){
        super.init();label=addRenderableWidget(new Label(leftPos+51,topPos+28,Component.empty()).withShadow());
        upload=addRenderableWidget(new SchematicIconButton(leftPos+44,topPos+58,AllIcons.I_CONFIRM,Component.translatable("create.action.confirm"),()->{var files=SchematicsClient.LOADER.getAvailableSchematics();if(selection!=null&&selection.getState()<files.size())SchematicsClient.LOADER.startNewUpload(files.get(selection.getState()).getString());}));
        addRenderableWidget(new SchematicIconButton(leftPos+20,topPos+23,AllIcons.I_OPEN_FOLDER,Component.translatable("create.gui.schematicTable.open_folder"),()->com.mojang.blaze3d.Blaze3D.openPath(SchematicFiles.localRoot())));
        addRenderableWidget(new SchematicIconButton(leftPos+206,topPos+23,AllIcons.I_REFRESH,Component.translatable("create.gui.schematicTable.refresh"),this::refresh));refresh();
    }
    private void refresh(){if(selection!=null)removeWidget(selection);SchematicsClient.LOADER.refresh();var files=SchematicsClient.LOADER.getAvailableSchematics();selection=null;label.text=Component.empty();if(!files.isEmpty())selection=addRenderableWidget(new SelectionScrollInput(leftPos+45,topPos+23,139,18).forOptions(files).titled(Component.translatable("create.gui.schematicTable.availableSchematics")).writingTo(label));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return getChildAt(x,y).filter(child->child.mouseScrolled(x,y,horizontal,vertical)).isPresent()||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override protected void containerTick(){
        super.containerTick();boolean finished=menu.getSlot(1).hasItem(),busy=menu.owner.isUploading||SchematicsClient.LOADER.isUploading();upload.active=selection!=null&&!busy&&!finished&&menu.getSlot(0).hasItem();
        if(selection!=null){selection.visible=!busy&&!finished;selection.tick();if(selection.visible)selection.writingTo(label);}
        if(busy)label.text=Component.literal(SchematicsClient.LOADER.uploadingFile().isEmpty()?menu.owner.uploadingSchematic:SchematicsClient.LOADER.uploadingFile());
        previous=progress;progress=finished?1:busy?progress+((menu.owner.isUploading?menu.owner.uploadingProgress:SchematicsClient.LOADER.progress())-progress)*.5f:0;
    }
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        super.extractBackground(gui,mouseX,mouseY,partial);AllGuiTextures.SCHEMATIC_TABLE.render(gui,leftPos,topPos);AllGuiTextures.PLAYER_INVENTORY.render(gui,leftPos+20,topPos+109);
        AllGuiTextures.SCHEMATIC_TABLE_PROGRESS.cropped(gui,leftPos+70,topPos+59,(int)(84*(previous+(progress-previous)*partial)),16);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){var heading=menu.owner.isUploading?Component.translatable("create.gui.schematicTable.uploading"):menu.getSlot(1).hasItem()?Component.translatable("create.gui.schematicTable.finished"):title;gui.text(font,heading,(206-font.width(heading))/2,4,0xFF505050,false);if(selection==null)gui.text(font,Component.translatable("create.gui.schematicTable.noSchematics"),54,28,0xFFD3D3D3,false);gui.text(font,playerInventoryTitle,28,115,0xFF404040,false);}
    @Override public void removed(){SchematicsClient.LOADER.cancel(true);super.removed();}
}
