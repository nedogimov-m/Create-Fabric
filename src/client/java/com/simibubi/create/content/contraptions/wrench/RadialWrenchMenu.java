package com.simibubi.create.content.contraptions.wrench;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Source hold/release radial rotation, property scrolling, click commit and right-click/Escape cancellation. */
public final class RadialWrenchMenu extends Screen {
    private final BlockState state;
    private final BlockPos pos;
    private final Level level;
    private final List<RadialWrenchRotation.Choice> properties;
    private List<BlockState> states;
    private int propertyIndex,selectedIndex,ticksOpen;
    private boolean submitted;
    public static Optional<RadialWrenchMenu> tryCreateFor(BlockState state,BlockPos pos,Level level){var choices=RadialWrenchRotation.propertiesFor(state);return choices.isEmpty()?Optional.empty():Optional.of(new RadialWrenchMenu(state,pos,level,choices));}
    public static void registerRotationProperty(Property<?> property,String label){RadialWrenchRotation.registerRotationProperty(property,label);}
    public static void registerBlacklistedBlock(net.minecraft.resources.Identifier id){RadialWrenchRotation.registerBlacklistedBlock(id);}
    private RadialWrenchMenu(BlockState state,BlockPos pos,Level level,List<RadialWrenchRotation.Choice> properties){super(Component.translatable("create.keyinfo.rotate_menu"));this.state=state;this.pos=pos.immutable();this.level=level;this.properties=properties;initProperty();}
    private void initProperty(){states=new ArrayList<>(RadialWrenchRotation.states(state,properties.get(propertyIndex).property()));selectedIndex=states.indexOf(state);if(selectedIndex<0)selectedIndex=0;}
    private boolean current(){return minecraft!=null&&minecraft.level==level&&minecraft.player!=null&&!minecraft.player.isSpectator()&&minecraft.player.getMainHandItem().is(AllItems.WRENCH)&&LoadedLevelAccess.isLoaded(level,pos)&&LoadedLevelAccess.blockState(level,pos)==state;}
    @Override public void tick(){ticksOpen++;if(!current())onClose();}
    private void hover(double x,double y){double dx=x-width/2d,dy=y-height/2d;if(dx*dx+dy*dy<=45*45||states.isEmpty())return;double sector=360d/states.size();double angle=Math.toDegrees(Math.atan2(dx,-dy));selectedIndex=(int)Math.floor(((angle+360+sector/2)%360)/sector);}
    private Component label(int index){String value=properties.get(index).label();return switch(value){case "Axis","Facing","Target","Vertical"->Component.translatable("create.wrench_menu.property."+value.toLowerCase(Locale.ROOT));default->Component.literal(value);};}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){int alpha=(int)(Math.min(1,(ticksOpen+partial)/20f)*160);gui.fill(0,0,width,height,alpha<<24|0x101018);}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        hover(mouseX,mouseY);int cx=width/2,cy=height/2;
        RadialWrenchGeometry.ring(gui,cx,cy,50,110,states.size(),selectedIndex);
        RadialWrenchGeometry.indicator(gui,cx,cy,mouseX,mouseY);
        for(int index=0;index<states.size();index++){
            double angle=Math.toRadians(index*360d/states.size()-90);int x=cx+(int)Math.round(Math.cos(angle)*80),y=cy+(int)Math.round(Math.sin(angle)*80);
            try{RadialWrenchPortrait.extract(gui,states.get(index),LoadedLevelAccess.blockEntity(level,pos),x,y,partial,minecraft.player.getXRot(),minecraft.player.getYRot()+180);}
            catch(Exception failure){Create.LOGGER.warn("Failed to extract blockstate in RadialWrenchMenu",failure);states.remove(index);selectedIndex=0;break;}
            if(index==selectedIndex)gui.centeredText(font,RadialWrenchRotation.value(states.get(index),properties.get(propertyIndex).property()),x,y+21,0xFFEEEEEE);
        }
        RadialWrenchGeometry.center(gui,cx,cy);
        gui.centeredText(font,Component.translatable("create.wrench_menu.changing"),cx,cy-10,0xFFEEEEEE);
        gui.centeredText(font,label(propertyIndex),cx,cy+3,0xFFFFFF88);
        if(propertyIndex>0){gui.centeredText(font,"↑",cx,cy-46,0xFFEEEEEE);gui.centeredText(font,label(propertyIndex-1),cx,cy-30,0xFFEEEEEE);}
        if(propertyIndex+1<properties.size()){gui.centeredText(font,label(propertyIndex+1),cx,cy+24,0xFFEEEEEE);gui.centeredText(font,"↓",cx,cy+38,0xFFEEEEEE);}
        super.extractRenderState(gui,mouseX,mouseY,partial);
    }
    private void submit(){
        if(submitted)return;submitted=true;
        if(current()&&!states.isEmpty()&&selectedIndex>=0&&selectedIndex<states.size()&&states.get(selectedIndex)!=state&&minecraft.getConnection()!=null){var property=properties.get(propertyIndex).property();ClientPlayNetworking.send(new RadialWrenchMenuSubmitPacket(pos,state,property.getName(),RadialWrenchRotation.value(states.get(selectedIndex),property)));}
        onClose();
    }
    @Override public boolean keyReleased(KeyEvent event){if(RadialWrenchHandler.ROTATE_MENU.matches(event)){submit();return true;}return super.keyReleased(event);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(RadialWrenchHandler.ROTATE_MENU.matchesMouse(event)){submit();return true;}return super.mouseReleased(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){hover(event.x(),event.y());if(event.button()==InputConstants.MOUSE_BUTTON_LEFT){submit();return true;}if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT){onClose();return true;}return super.mouseClicked(event,twice);}
    @Override public boolean mouseScrolled(double x,double y,double scrollX,double scrollY){if(properties.size()<2||scrollY==0)return super.mouseScrolled(x,y,scrollX,scrollY);int next=propertyIndex+(scrollY>0?-1:1);if(next<0||next>=properties.size())return false;propertyIndex=next;initProperty();return true;}
    @Override public void removed(){RadialWrenchHandler.COOLDOWN=2;super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
