package com.simibubi.create.content.redstone.diodes;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;

/** Three native timing rows with source ticks/seconds/minutes conversions and release-to-confirm. */
public final class BrassDiodeScreen extends Screen {
    private final BlockPos pos;
    private int left,top,row,column;
    private boolean shift;
    public BrassDiodeScreen(BlockPos pos,int ticks){super(GoggleTooltip.translate("logistics.redstone_interval"));this.pos=pos.immutable();var setting=BrassDiodeSettings.ValueSetting.fromTicks(ticks);row=setting.row();column=setting.value();}
    public BlockPos position(){return pos;}
    @Override protected void init(){left=(width-256)/2;top=(height-33)/2;warp(getCoordinateOfValue(row,column));}
    public Vec2 getCoordinateOfValue(int row,int value){return new Vec2(left+61+value*3,top+row*11+5);}
    private void warp(Vec2 point){var window=minecraft.getWindow();double scaleX=(double)window.getScreenWidth()/window.getGuiScaledWidth();double scaleY=(double)window.getScreenHeight()/window.getGuiScaledHeight();InputConstants.releaseMouse(window,point.x*scaleX,point.y*scaleY);minecraft.mouseHandler.onMove(window.handle(),point.x*scaleX,point.y*scaleY,0,0);}
    public BrassDiodeSettings.ValueSetting closestValue(double x,double y,boolean milestones){row=Math.clamp((int)Math.round((y-top-5)/11),0,2);int step=milestones?10:1;column=Math.clamp((int)Math.round((x-left-61)/(3*step))*step,0,60);return new BrassDiodeSettings.ValueSetting(row,column);}
    @Override public void tick(){if(minecraft.player==null||minecraft.level==null||!(LoadedLevelAccess.blockEntity(minecraft.level,pos)instanceof BrassDiodeBlockEntity)||!minecraft.player.isWithinBlockInteractionRange(pos,1))onClose();}
    @Override public void mouseMoved(double x,double y){closestValue(x,y,shift);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(minecraft.options.keyUse.matchesMouse(event)){saveAndClose(event.x(),event.y());return true;}return super.mouseReleased(event);}
    @Override public boolean keyReleased(KeyEvent event){shift=event.hasShiftDown();if(minecraft.options.keyUse.matches(event)){saveAndClose(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));return true;}return super.keyReleased(event);}
    @Override public boolean keyPressed(KeyEvent event){shift=event.hasShiftDown();if(event.isEscape())return super.keyPressed(event);if(event.isConfirmation()){var point=getCoordinateOfValue(row,column);saveAndClose(point.x,point.y);return true;}if(event.isLeft()||event.isRight())column=Math.clamp(column+(event.isRight()?1:-1)*(shift?10:1),0,60);else if(event.isUp()||event.isDown())row=Math.clamp(row+(event.isDown()?1:-1),0,2);else return super.keyPressed(event);warp(getCoordinateOfValue(row,column));return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){shift=minecraft.hasShiftDown();closestValue(x,y,shift);column=Math.clamp(column+(int)Math.signum(dy)*(shift?10:1),0,60);warp(getCoordinateOfValue(row,column));return true;}
    public void saveAndClose(double x,double y){var setting=closestValue(x,y,shift);ClientPlayNetworking.send(new BrassDiodeSettings.SetTiming(pos,setting.ticks()));onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        gui.fill(0,0,width,height,0x50101010);gui.fill(left-8,top-21,left+256,top+58,0xF05C4835);gui.centeredText(font,title,left+124,top-15,0xFFDDDDDD);
        for(int r=0;r<3;r++){int y=top+r*11;gui.fill(left+57,y,left+245,y+10,0xFFE0C39B);gui.text(font,GoggleTooltip.translate("generic.unit."+(r==0?"ticks":r==1?"seconds":"minutes")),left,y+1,0xFFFFFFFF,false);for(int value=0;value<=60;value++){var point=getCoordinateOfValue(r,value);gui.fill((int)point.x,y+(value%10==0?0:4),(int)point.x+1,y+7,0xFF795330);}}
        shift=minecraft.hasShiftDown();var setting=closestValue(mouseX,mouseY,shift);var point=getCoordinateOfValue(row,column);gui.fill((int)point.x-1,(int)point.y-5,(int)point.x+2,(int)point.y+5,0xFFCD0000);gui.centeredText(font,setting.format(),left+124,top+38,0xFFFBDC7D);
    }
}
