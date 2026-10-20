package com.simibubi.create.content.redstone.thresholdSwitch;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native typed and wheel inputs, live server measurements, source item/stack/bucket steps and autosave. */
public final class ThresholdSwitchScreen extends AbstractContainerScreen<ThresholdSwitchMenu> {
    private int upper,lower,seenRevision,dirtyTicks=-1,pendingRevision=-1;
    private boolean stacks,inverted,updating,closing;
    private EditBox upperBox,lowerBox;
    private Button measure,invert;
    public ThresholdSwitchScreen(ThresholdSwitchMenu menu,Inventory inventory,Component title){super(menu,inventory,title,242,174);readSettings();}
    private void readSettings(){var data=menu.opening();upper=data.upper();lower=data.lower();stacks=data.stacks();inverted=data.inverted();seenRevision=data.revision();clamp(false);if(upper!=data.upper()||lower!=data.lower())dirtyTicks=0;}
    public int upperThreshold(){return upper;}
    public int lowerThreshold(){return lower;}
    public boolean inStacks(){return stacks;}
    private int step(){return menu.opening().kind()==ThresholdSwitchBlockEntity.ThresholdType.FLUID?1000:menu.opening().kind()==ThresholdSwitchBlockEntity.ThresholdType.ITEM&&stacks?64:1;}
    private void changed(){if(!updating)dirtyTicks=0;}
    private void clamp(boolean upperChanged){var data=menu.opening();if(data.kind()!=ThresholdSwitchBlockEntity.ThresholdType.ITEM)stacks=false;if(!data.ready())return;upper=Math.clamp(upper,data.min(),data.max());lower=Math.clamp(lower,data.min(),data.max());if(data.max()>data.min()&&upper<=lower){if(upperChanged)lower=(int)Math.max(data.min(),(long)upper-1);else upper=(int)Math.min(data.max(),(long)lower+1);if(upper<=lower){upper=data.max();lower=data.max()-1;}}}
    @Override protected void init(){
        super.init();seenRevision=menu.opening().revision();upperBox=field(91,43,true);lowerBox=field(91,68,false);
        measure=addRenderableWidget(Button.builder(unitLabel(),button->{stacks=!stacks;changed();refreshFields();button.setMessage(unitLabel());}).bounds(leftPos+154,topPos+43,74,20).build());measure.visible=menu.opening().kind()==ThresholdSwitchBlockEntity.ThresholdType.ITEM;measure.active=menu.opening().ready();
        invert=addRenderableWidget(Button.builder(invertLabel(),button->{inverted=!inverted;changed();button.setMessage(invertLabel());send();}).bounds(leftPos+14,topPos+140,186,20).build());invert.active=menu.opening().ready();
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+208,topPos+140,20,20).build());
    }
    private EditBox field(int x,int y,boolean high){var field=new EditBox(font,leftPos+x,topPos+y,57,20,Component.translatable(high?"create.gui.threshold_switch.upper_threshold":"create.gui.threshold_switch.lower_threshold"));field.setMaxLength(12);field.setValue(Integer.toString((high?upper:lower)/step()));field.setEditable(menu.opening().ready());field.setResponder(text->{if(updating)return;try{long value=Long.parseLong(text)*step();if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)return;if(high)upper=(int)value;else lower=(int)value;clamp(high);updating=true;var other=high?lowerBox:upperBox;if(other!=null)other.setValue(Integer.toString((high?lower:upper)/step()));updating=false;changed();}catch(NumberFormatException ignored){}});return addRenderableWidget(field);}
    private Component unitLabel(){return Component.translatable("create.schedule.condition.threshold."+(stacks?"stacks":"items"));}
    private Component invertLabel(){return Component.translatable(inverted?"create.threshold_switch.inverted":"create.threshold_switch.normal");}
    private void refreshFields(){if(upperBox==null)return;updating=true;upperBox.setValue(Integer.toString(upper/step()));lowerBox.setValue(Integer.toString(lower/step()));updating=false;}
    private void send(){if(dirtyTicks<0||pendingRevision==menu.opening().revision()||minecraft.player==null||!menu.stillValid(minecraft.player)||!menu.opening().ready())return;clamp(false);ClientPlayNetworking.send(new ConfigureThresholdSwitchPacket(menu.containerId,menu.opening().pos(),menu.opening().revision(),upper,lower,stacks,inverted));pendingRevision=menu.opening().revision();dirtyTicks=-1;}
    @Override protected void containerTick(){super.containerTick();if(!menu.stillValid(minecraft.player)){onClose();return;}var data=menu.opening();if(seenRevision!=data.revision()){
        boolean hadPending=pendingRevision>=0;pendingRevision=-1;if(dirtyTicks<0&&(!hadPending||data.upper()==upper&&data.lower()==lower&&data.stacks()==stacks&&data.inverted()==inverted))readSettings();else{clamp(false);dirtyTicks=0;}rebuildWidgets();
    }if(closing){if(!data.ready()){closing=false;super.onClose();return;}if(pendingRevision<0){if(dirtyTicks>=0)send();else{closing=false;super.onClose();return;}}}if(dirtyTicks>=0&&++dirtyTicks>=20)send();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical!=0&&menu.opening().ready()&&(upperBox.isMouseOver(x,y)||lowerBox.isMouseOver(x,y))){boolean high=upperBox.isMouseOver(x,y);long delta=(long)Math.signum(vertical)*step()*(minecraft.hasShiftDown()?10:1);if(high)upper=(int)Math.clamp((long)upper+delta,Integer.MIN_VALUE,Integer.MAX_VALUE);else lower=(int)Math.clamp((long)lower+delta,Integer.MIN_VALUE,Integer.MAX_VALUE);clamp(high);refreshFields();changed();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(KeyEvent event){if(event.isEscape())return super.keyPressed(event);if(getFocused()instanceof EditBox edit&&edit.canConsumeInput()){edit.keyPressed(event);return true;}if(event.isConfirmation()){onClose();return true;}return super.keyPressed(event);}
    @Override public void onClose(){if(pendingRevision>=0&&dirtyTicks>=0&&menu.opening().ready()){closing=true;return;}send();super.onClose();}
    @Override public void removed(){send();super.removed();}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);gui.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFFDFD0AC);gui.fill(leftPos+5,topPos+20,leftPos+237,topPos+132,0xFFB4A78E);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/threshold_switch.png"),leftPos+5,topPos+21,0,153,24,24,256,256);gui.item(menu.opening().target(),leftPos+9,topPos+25);}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){var data=menu.opening();gui.text(font,title,(imageWidth-font.width(title))/2,6,0xFF3D3C48,false);gui.text(font,Component.translatable("create.threshold_switch.on_at"),14,48,0xFF303030,false);gui.text(font,Component.translatable("create.threshold_switch.off_at"),14,73,0xFF303030,false);if(!data.ready())gui.text(font,Component.translatable("create.threshold_switch.unavailable"),14,104,0xFF592424,false);else{gui.text(font,data.currentText(),14,100,0xFF303030,false);gui.text(font,data.minText().copy().append(" … ").append(data.maxText()),14,114,0xFF303030,false);}gui.text(font,Component.literal((data.raw()?"●":"○")+"  →  "+(data.powered()?"●":"○")),164,80,data.powered()?0xFFB01C12:0xFF454545,false);if(data.kind()==ThresholdSwitchBlockEntity.ThresholdType.FLUID)gui.text(font,Component.translatable("create.schedule.condition.threshold.buckets"),154,48,0xFF303030,false);}
    public static void register(){net.minecraft.client.gui.screens.MenuScreens.register(ThresholdSwitchMenu.TYPE,ThresholdSwitchScreen::new);net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(com.simibubi.create.AllBlockEntityTypes.THRESHOLD_SWITCH,ThresholdSwitchRenderer::new);ClientPlayNetworking.registerGlobalReceiver(ThresholdSwitchSnapshotPacket.TYPE,(packet,context)->{var player=context.client().player;if(player!=null&&player.containerMenu instanceof ThresholdSwitchMenu menu&&menu.containerId==packet.containerId()&&menu.opening().pos().equals(packet.opening().pos()))menu.updateSnapshot(packet.opening());});}
}
