package com.simibubi.create.content.schematics.client;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Native source file-name prompt; only successful actual export dismisses the selection. */
public final class SchematicPromptScreen extends Screen {
    private final SchematicAndQuillHandler handler;
    private EditBox name;
    private int left,top;
    public SchematicPromptScreen(SchematicAndQuillHandler handler){super(Component.translatable("create.schematicAndQuill.title"));this.handler=handler;}
    @Override protected void init(){
        left=(width-AllGuiTextures.SCHEMATIC_PROMPT.getWidth())/2;top=(height-AllGuiTextures.SCHEMATIC_PROMPT.getHeight())/2;
        name=addRenderableWidget(new EditBox(font,left+49,top+28,131,10,Component.translatable("create.schematicAndQuill.title")));name.setBordered(false);name.setMaxLength(35);name.setTextColor(-1);setInitialFocus(name);
        addRenderableWidget(new SchematicIconButton(left+7,top+55,AllIcons.I_TRASH,Component.translatable("create.action.discard"),()->{handler.discard();onClose();}));
        addRenderableWidget(new SchematicIconButton(left+158,top+55,AllIcons.I_CONFIRM,Component.translatable("create.action.saveToFile"),()->confirm(false)));
        addRenderableWidget(new SchematicIconButton(left+180,top+55,AllIcons.I_SCHEMATIC,Component.translatable("create.schematicAndQuill.convert"),()->confirm(true)));
    }
    private void confirm(boolean convert){if(handler.saveSchematic(name.getValue(),convert))onClose();}
    @Override public boolean keyPressed(KeyEvent event){if(event.isConfirmation()&&name.isFocused()){confirm(false);return true;}return super.keyPressed(event);}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int x,int y,float partial){AllGuiTextures.SCHEMATIC_PROMPT.render(gui,left,top);gui.text(font,title,left+(205-font.width(title))/2,top+4,0xFF505050,false);gui.item(new ItemStack(AllItems.SCHEMATIC),left+22,top+24);super.extractRenderState(gui,x,y,partial);}
    @Override public boolean isPauseScreen(){return false;}
}
