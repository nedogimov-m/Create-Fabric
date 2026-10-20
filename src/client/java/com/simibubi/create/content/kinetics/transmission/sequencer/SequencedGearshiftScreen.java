package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.simibubi.create.Create;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native widgets and GUI extraction; editing owns a copy and confirms one bounded server packet. */
public final class SequencedGearshiftScreen extends AbstractContainerScreen<SequencedGearshiftMenu> {
    private final List<Instruction> instructions;
    private final EditBox[] values=new EditBox[5];
    private boolean sent;
    public SequencedGearshiftScreen(SequencedGearshiftMenu menu,Inventory inventory,Component title){super(menu,inventory,title,173,161);instructions=new ArrayList<>(menu.opening().instructions());}
    public List<Instruction> instructions(){return List.copyOf(instructions);}
    @Override protected void init(){
        super.init();java.util.Arrays.fill(values,null);
        for(int row=0;row<instructions.size();row++){
            final int index=row;var instruction=instructions.get(row);int y=topPos+20+row*22;
            addRenderableWidget(Button.builder(Component.translatable(instruction.instruction().translationKey()),button->changeType(index)).bounds(leftPos+29,y,57,18).build());
            if(instruction.instruction().hasValueParameter()){
                var field=new EditBox(font,leftPos+88,y,29,18,Component.translatable(instruction.instruction().translationKey()));field.setMaxLength(3);field.setValue(Integer.toString(instruction.value()));
                field.setResponder(text->{try{int value=Integer.parseInt(text);var actual=instructions.get(index);if(value>=1&&value<=actual.instruction().maxValue){instructions.set(index,new Instruction(actual.instruction(),actual.speedModifier(),value));field.setTextColor(0xFFE0E0E0);}else field.setTextColor(0xFFFF6060);}catch(NumberFormatException error){field.setTextColor(0xFFFF6060);}});
                values[row]=addRenderableWidget(field);
            }
            if(instruction.instruction().hasSpeedParameter)addRenderableWidget(Button.builder(Component.literal(instruction.speedModifier().label),button->{var current=instructions.get(index);var modifiers=InstructionSpeedModifiers.values();var next=modifiers[(current.speedModifier().ordinal()+1)%modifiers.length];instructions.set(index,new Instruction(current.instruction(),next,current.value()));button.setMessage(Component.literal(next.label));}).bounds(leftPos+120,y,27,18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+140,topPos+137,18,18).build());
    }
    private void changeType(int index){
        var previous=instructions.get(index);var options=SequencerInstructions.values();var type=options[(previous.instruction().ordinal()+1)%options.length];instructions.set(index,new Instruction(type));
        if(type==SequencerInstructions.END)while(instructions.size()>index+1)instructions.removeLast();
        else if(index+1==instructions.size()&&instructions.size()<5)instructions.add(new Instruction(SequencerInstructions.END));
        rebuildWidgets();
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        for(int row=0;row<instructions.size();row++)if(values[row]!=null&&values[row].isMouseOver(x,y)){
            var instruction=instructions.get(row);var type=instruction.instruction();int amount=minecraft.hasShiftDown()?type.shiftStep:1;
            if(type==SequencerInstructions.DELAY&&(instruction.value()-(vertical<0?1:0))>=20)amount=minecraft.hasShiftDown()?100:20;
            int value=Math.clamp(instruction.value()+(int)Math.signum(vertical)*amount,1,type.maxValue);values[row].setValue(Integer.toString(value));return true;
        }return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private void send(){if(sent||minecraft.player==null||!menu.stillValid(minecraft.player))return;sent=true;ClientPlayNetworking.send(new ConfigureSequencedGearshiftPacket(menu.opening().pos(),instructions));}
    @Override public void onClose(){send();super.onClose();}
    @Override public void removed(){send();super.removed();}
    @Override protected void containerTick(){super.containerTick();if(!menu.stillValid(minecraft.player))onClose();}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/sequencer.png"),leftPos,topPos,0,0,173,161,256,256);
        for(int row=0;row<5;row++){
            var type=row<instructions.size()?instructions.get(row).instruction():null;int v=type==null?104:switch(type){case TURN_ANGLE,TURN_DISTANCE->16;case DELAY->60;case AWAIT->162;case END->82;};
            gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/sequencer.png"),leftPos,topPos+16+row*22,0,v,162,22,256,256);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,(imageWidth-font.width(title))/2,4,0xFF592424,false);}
}
