package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

/** Source floor identifiers/descriptions/door mode use actual native widgets and one confirmed server edit. */
public final class ElevatorContactScreen extends Screen {
    private final ElevatorContactEditPacket.Open opening;
    private EditBox shortName,longName;private DoorControl doorMode;private Button door;private int left,top;private boolean sent;
    public ElevatorContactScreen(ElevatorContactEditPacket.Open opening){super(Component.translatable("create.elevator_contact.title"));this.opening=opening;doorMode=opening.doorMode();}
    public BlockPos position(){return opening.pos();}
    public EditBox shortNameBox(){return shortName;}
    public EditBox longNameBox(){return longName;}
    public DoorControl doorMode(){return doorMode;}
    public Vec2 fieldCenter(boolean description){var field=description?longName:shortName;return new Vec2(field.getX()+field.getWidth()/2f,field.getY()+field.getHeight()/2f);}
    @Override protected void init(){
        left=width/2-120;top=height/2-88;shortName=new EditBox(font,left+170,top+27,58,18,Component.translatable("create.elevator_contact.floor_identifier"));shortName.setMaxLength(4);shortName.setValue(opening.shortName());longName=new EditBox(font,left+12,top+64,216,18,Component.translatable("create.elevator_contact.floor_description"));longName.setMaxLength(90);longName.setValue(opening.longName());addRenderableWidget(shortName);addRenderableWidget(longName);
        door=addRenderableWidget(Button.builder(doorLabel(),button->{var modes=DoorControl.values();doorMode=modes[(doorMode.ordinal()+1)%modes.length];button.setMessage(doorLabel());}).bounds(left+12,top+109,216,20).build());addRenderableWidget(Button.builder(Component.literal("✓"),button->confirm()).bounds(left+206,top+142,22,20).build());setInitialFocus(shortName);shortName.setCursorPosition(shortName.getValue().length());shortName.setHighlightPos(0);
    }
    private Component doorLabel(){return Component.translatable("create.contraption.door_control."+doorMode.name().toLowerCase(Locale.ROOT));}
    public void confirm(){if(sent||minecraft.player==null||minecraft.level==null||!(minecraft.level.getBlockEntity(opening.pos()) instanceof ElevatorContactBlockEntity))return;sent=true;ClientPlayNetworking.send(new ElevatorContactEditPacket(opening.pos(),shortName.getValue(),longName.getValue(),doorMode));onClose();}
    @Override public boolean keyPressed(KeyEvent event){if(event.isConfirmation()){confirm();return true;}return super.keyPressed(event);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(door!=null&&door.isMouseOver(x,y)&&vertical!=0){var modes=DoorControl.values();doorMode=modes[Math.floorMod(doorMode.ordinal()+(int)Math.signum(vertical),modes.length)];door.setMessage(doorLabel());return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void tick(){if(minecraft.player==null||minecraft.level==null||minecraft.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(opening.pos()))>64||!(minecraft.level.getBlockEntity(opening.pos()) instanceof ElevatorContactBlockEntity))onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fill(left,top,left+240,top+176,0xF0302D36);graphics.centeredText(font,title,width/2,top+8,0xFFEDEDED);graphics.text(font,Component.translatable("create.elevator_contact.floor_identifier"),left+12,top+32,0xFFB8B1A8,false);graphics.text(font,Component.translatable("create.elevator_contact.floor_description"),left+12,top+53,0xFFB8B1A8,false);graphics.text(font,Component.translatable("create.contraption.door_control"),left+12,top+97,0xFFB8B1A8,false);if(!opening.currentFloor().isBlank())graphics.text(font,Component.literal(opening.currentFloor()),left+12,top+148,0xFFFBDC7D,false);super.extractRenderState(graphics,mouseX,mouseY,partial);
    }
}
