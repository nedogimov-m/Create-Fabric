package com.simibubi.create.content.logistics.depot;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Source0..64 stack threshold with native input, including the original wildcard0. */
public final class EjectorSettingsScreen extends Screen {
    private final BlockPos pos;
    private int selected,left,top;
    private ThresholdSlider slider;
    public EjectorSettingsScreen(BlockPos pos,int value){super(Component.translatable("create.weighted_ejector.stack_size"));this.pos=pos.immutable();selected=value;}
    public int selected(){return selected;}
    public BlockPos position(){return pos;}
    private Component valueText(){return Component.literal(selected==0?"*":Integer.toString(selected));}
    @Override protected void init(){
        left=(width-210)/2;top=(height-88)/2;
        slider=addRenderableWidget(new ThresholdSlider());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->save()).bounds(left+55,top+57,100,20).build());
    }
    private final class ThresholdSlider extends AbstractSliderButton {
        ThresholdSlider(){super(left+12,top+27,186,20,valueText(),selected/64d);}
        void updateThreshold(){value=selected/64d;setMessage(valueText());}
        @Override protected void updateMessage(){setMessage(valueText());}
        @Override protected void applyValue(){selected=Math.clamp((int)Math.round(value*64),0,64);setMessage(valueText());}
    }
    public void save(){ClientPlayNetworking.send(new EjectorNetworking.Settings(pos,selected));onClose();}
    @Override public boolean keyPressed(KeyEvent event){if(event.isConfirmation()){save();return true;}return super.keyPressed(event);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(dy==0)return false;selected=Math.clamp(selected+(dy>0?1:-1),0,64);slider.updateThreshold();return true;}
    @Override public void tick(){if(minecraft.level==null||minecraft.player==null||!minecraft.player.isWithinBlockInteractionRange(pos,1)||!(minecraft.level.getBlockEntity(pos) instanceof EjectorBlockEntity))onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        gui.fillGradient(0,0,width,height,0x60101010,0x60101010);gui.fill(left-6,top-6,left+216,top+88,0xEF302725);gui.centeredText(font,title,width/2,top+8,0xFFFBDC7D);
        super.extractRenderState(gui,mouseX,mouseY,partial);
    }
}
