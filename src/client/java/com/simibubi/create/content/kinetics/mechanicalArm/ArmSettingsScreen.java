package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The three actual source policies, with normal native SDL widget input and keyboard navigation. */
public final class ArmSettingsScreen extends Screen {
    private final BlockPos pos;
    private ArmBlockEntity.SelectionMode selected;
    private int left,top;
    public ArmSettingsScreen(BlockPos pos,ArmBlockEntity.SelectionMode mode){super(Component.translatable("create.logistics.when_multiple_outputs_available"));this.pos=pos.immutable();selected=mode;}
    public BlockPos position(){return pos;}
    public ArmBlockEntity.SelectionMode selected(){return selected;}
    @Override protected void init(){
        left=(width-240)/2;top=(height-116)/2;
        for(var mode:ArmBlockEntity.SelectionMode.values())addRenderableWidget(Button.builder(Component.translatable(mode.getTranslationKey()),button->selected=mode).bounds(left+30,top+24+mode.ordinal()*22,204,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->save()).bounds(left+68,top+94,104,20).build());
    }
    public void save(){ClientPlayNetworking.send(new ArmNetworking.Settings(pos,selected));onClose();}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isConfirmation()){save();return true;}
        if(event.isUp()||event.isDown()){selected=ArmBlockEntity.SelectionMode.values()[Math.floorMod(selected.ordinal()+(event.isDown()?1:-1),3)];return true;}
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(dy==0)return false;selected=ArmBlockEntity.SelectionMode.values()[Math.floorMod(selected.ordinal()+(dy>0?-1:1),3)];return true;}
    @Override public void tick(){if(minecraft.level==null||minecraft.player==null||!minecraft.player.isWithinBlockInteractionRange(pos,1)||!(minecraft.level.getBlockEntity(pos) instanceof ArmBlockEntity))onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        gui.fillGradient(0,0,width,height,0x60101010,0x60101010);gui.fill(left-8,top-6,left+248,top+122,0xEF302725);gui.centeredText(font,title,width/2,top+5,0xFFFBDC7D);
        for(var mode:ArmBlockEntity.SelectionMode.values()){
            int y=top+26+mode.ordinal()*22;gui.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/icons.png"),left+8,y,ArmInteractionPointHandler.ICON_U[mode.ordinal()],ArmInteractionPointHandler.ICON_V[mode.ordinal()],16,16,256,256);
            if(mode==selected)gui.fill(left+3,y-2,left+5,y+18,0xFFFBDC7D);
        }
        super.extractRenderState(gui,mouseX,mouseY,partial);
    }
}
