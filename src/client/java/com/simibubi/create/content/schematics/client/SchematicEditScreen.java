package com.simibubi.create.content.schematics.client;

import com.simibubi.create.foundation.gui.*;
import com.simibubi.create.foundation.gui.widget.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Actual source coordinate/rotation/mirror editor with native input and extraction. */
public final class SchematicEditScreen extends Screen {
    private final SchematicHandler handler;
    private EditBox x,y,z;
    private ScrollInput rotation,mirror;
    private int left,top;
    public SchematicEditScreen(SchematicHandler handler){super(Component.literal(handler.getCurrentSchematicName()));this.handler=handler;}
    @Override protected void init(){
        left=(width-192)/2;top=(height-123)/2;
        var anchor=handler.isDeployed()?handler.getTransformation().getAnchor():minecraft.player.blockPosition();
        x=coordinate(left+50,anchor.getX());y=coordinate(left+90,anchor.getY());z=coordinate(left+130,anchor.getZ());
        var settings=handler.getTransformation().toSettings();
        var r=addRenderableWidget(new Label(left+50,top+50,Component.empty()).withShadow());
        rotation=addRenderableWidget(new SelectionScrollInput(left+45,top+45,118,18).forOptions(List.of(Component.translatable("create.schematic.rotation.none"),Component.translatable("create.schematic.rotation.cw90"),Component.translatable("create.schematic.rotation.cw180"),Component.translatable("create.schematic.rotation.cw270"))).titled(Component.translatable("create.schematic.rotation")).setState(settings.getRotation().ordinal()).writingTo(r));
        var m=addRenderableWidget(new Label(left+50,top+72,Component.empty()).withShadow());
        mirror=addRenderableWidget(new SelectionScrollInput(left+45,top+67,118,18).forOptions(List.of(Component.translatable("create.schematic.mirror.none"),Component.translatable("create.schematic.mirror.leftRight"),Component.translatable("create.schematic.mirror.frontBack"))).titled(Component.translatable("create.schematic.mirror")).setState(settings.getMirror().ordinal()).writingTo(m));
        addRenderableWidget(new SchematicIconButton(left+159,top+99,AllIcons.I_CONFIRM,Component.translatable("gui.done"),this::onClose));
    }
    private EditBox coordinate(int at,int initial){var input=addRenderableWidget(new EditBox(font,at,top+28,34,10,Component.translatable("create.schematic.position")));input.setBordered(false);input.setMaxLength(11);input.setValue(Integer.toString(initial));return input;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return getChildAt(x,y).filter(child->child.mouseScrolled(x,y,horizontal,vertical)).isPresent()||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void tick(){rotation.tick();mirror.tick();}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isPaste()){var text=minecraft.keyboardHandler.getClipboard();var parts=text.strip().split("\\s*,\\s*");if(parts.length==3)try{for(var part:parts)Integer.parseInt(part);x.setValue(parts[0]);y.setValue(parts[1]);z.setValue(parts[2]);return true;}catch(NumberFormatException ignored){}}
        return super.keyPressed(event);
    }
    @Override public void removed(){
        if(x==null)return;try{var anchor=new BlockPos(Integer.parseInt(x.getValue()),Integer.parseInt(y.getValue()),Integer.parseInt(z.getValue()));var settings=new StructurePlaceSettings().setRotation(Rotation.values()[rotation.getState()]).setMirror(Mirror.values()[mirror.getState()]);handler.getTransformation().init(anchor,settings,handler.getBounds());handler.deploy();handler.markDirty();handler.sync();}catch(NumberFormatException ignored){}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int x,int y,float partial){AllGuiTextures.SCHEMATIC.render(gui,left,top);gui.text(font,title,left+(184-font.width(title))/2,top+4,0xFF505050,false);super.extractRenderState(gui,x,y,partial);}
    @Override public boolean isPauseScreen(){return false;}
}
