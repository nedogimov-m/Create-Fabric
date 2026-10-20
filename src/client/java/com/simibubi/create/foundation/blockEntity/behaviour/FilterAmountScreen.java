package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/** Source filter amount board, using native GUI extraction and platform input. */
public final class FilterAmountScreen extends Screen {
    private static final Identifier TEXTURE=Create.asResource("textures/gui/value_settings.png");
    private final BlockPos pos;
    private final Direction face;
    private final InteractionHand hand;
    private final int maximum;
    private final boolean price;
    private int step(){return price?10:16;}
    private int left,top,barWidth,labelWidth,ticksOpen,row,column;
    private boolean shift;
    public FilterAmountScreen(BlockPos pos,Direction face,InteractionHand hand,int amount,boolean upTo,int maximum){super(Component.translatable(isPrice(pos)?"create.table_cloth.price_per_order":"create.logistics.filter.extracted_amount"));this.pos=pos.immutable();this.face=face;this.hand=hand;this.maximum=maximum;price=isPrice(pos);row=price?1:upTo?0:1;column=amount;}

    private static boolean isPrice(BlockPos pos){var level=net.minecraft.client.Minecraft.getInstance().level;return level!=null&&level.getBlockState(pos).getBlock() instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlock;}
    private Component label(int row){return Component.translatable(price?"create.table_cloth.amount":"create.logistics.filter."+(row==0?"up_to":"exactly"));}
    public BlockPos position(){return pos;}
    @Override protected void init(){
        labelWidth=Math.max(font.width(label(0)),font.width(label(1)));barWidth=maximum+1+(maximum/step()+1)*4;int panelWidth=labelWidth+14+barWidth+10;left=(width-panelWidth)/2;top=(height-22)/2;
        var coordinate=getCoordinateOfValue(row,column);warp(coordinate);
    }
    private void warp(Vec2 point){var window=minecraft.getWindow();double scaleX=(double)window.getScreenWidth()/window.getGuiScaledWidth();double scaleY=(double)window.getScreenHeight()/window.getGuiScaledHeight();InputConstants.releaseMouse(window,point.x*scaleX,point.y*scaleY);minecraft.mouseHandler.onMove(window.handle(),point.x*scaleX,point.y*scaleY,0,0);}
    public Vec2 getCoordinateOfValue(int row,int column){float x=left+((Math.max(1,column)-1)/step())*4+column+1.5f+labelWidth+18;if(column%step()==0)x+=2;if(column>0)x+=4;return new Vec2(x,top+((price?0:row)+0.5f)*11-0.5f);}
    public int closestValue(double x,double y,boolean milestones){
        row=price?1:Math.abs(getCoordinateOfValue(0,0).y-y)<=Math.abs(getCoordinateOfValue(1,0).y-y)?0:1;
        double best=Double.MAX_VALUE;int closest=0;for(int value=0;value<=maximum;value+=milestones?step():1){double distance=Math.abs(getCoordinateOfValue(row,value).x-x);if(distance>best)break;best=distance;closest=value;}
        column=closest;return (row==0?-1:1)*Math.max(1,column);
    }
    @Override public void tick(){ticksOpen++;if(minecraft.player==null||minecraft.level==null||FilteringSettings.resolve(minecraft.level,pos)==null||!FilteringSettings.resolve(minecraft.level,pos).isCountVisible())onClose();}
    @Override public void mouseMoved(double x,double y){closestValue(x,y,shift);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(minecraft.options.keyUse.matchesMouse(event)){saveAndClose(event.x(),event.y());return true;}return super.mouseReleased(event);}
    @Override public boolean keyReleased(KeyEvent event){shift=event.hasShiftDown();if(minecraft.options.keyUse.matches(event)){saveAndClose(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));return true;}return super.keyReleased(event);}
    @Override public boolean keyPressed(KeyEvent event){
        shift=event.hasShiftDown();if(event.isEscape())return super.keyPressed(event);
        if(event.isConfirmation()){saveAndClose(getCoordinateOfValue(row,column).x,getCoordinateOfValue(row,column).y);return true;}
        if(event.isLeft()||event.isRight())column=Math.clamp(column+(event.isRight()?1:-1)*(shift?step():1),1,maximum);else if(event.isUp()||event.isDown())row=price?1:event.isUp()?0:1;else return super.keyPressed(event);warp(getCoordinateOfValue(row,column));return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){shift=minecraft.hasShiftDown();closestValue(x,y,shift);column=Math.clamp(column+(int)Math.signum(dy)*(shift?step():1),1,maximum);warp(getCoordinateOfValue(row,column));return true;}
    public void saveAndClose(double x,double y){int value=closestValue(x,y,shift);ClientPlayNetworking.send(new FilteringSettings.SetFilter(pos,face,hand,Math.max(1,Math.abs(value)),row==0));onClose();}
    @Override public boolean isPauseScreen(){return false;}
    private static void blit(GuiGraphicsExtractor graphics,int x,int y,int w,int h,float u,float v,int sourceW,int sourceH){graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,TEXTURE,x,y,u,v,w,h,sourceW,sourceH,256,256);}
    private static void crop(GuiGraphicsExtractor graphics,int x,int y,int w,int h,int u,int v){if(w>0&&h>0)blit(graphics,x,y,w,h,u,v,w,h);}
    private static void stretch(GuiGraphicsExtractor graphics,int x,int y,int w,int h,int u,int v,int sourceW,int sourceH){if(w>0&&h>0)blit(graphics,x,y,w,h,u,v,sourceW,sourceH);}
    private static void frame(GuiGraphicsExtractor graphics,int x,int y,int w,int h){
        crop(graphics,x,y,4,4,65,9);crop(graphics,x+w-4,y,4,4,70,9);crop(graphics,x,y+h-4,4,4,65,19);crop(graphics,x+w-4,y+h-4,4,4,70,19);
        stretch(graphics,x,y+4,3,h-8,65,14,3,4);stretch(graphics,x+w-3,y+4,3,h-8,71,14,3,4);crop(graphics,x+4,y,w-8,3,0,24);crop(graphics,x+4,y+h-3,w-8,3,0,27);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fillGradient(0,0,width,height,0x50101010,0x50101010);
        var tip=GoggleTooltip.translate("gui.value_settings.release_to_confirm",Component.keybind("key.use"));int boardWidth=labelWidth+14+barWidth+10,bgWidth=Math.max(boardWidth+13,Math.max(font.width(title),font.width(tip))+10),bgX=left-11-(bgWidth-boardWidth-13)/2;
        int reveal=(int)(bgWidth*Math.clamp((ticksOpen+partial)/4,0,1));stretch(graphics,bgX+(bgWidth-reveal)/2,top-17,reveal,price?44:55,80,9,1,1);
        if(reveal>font.width(title))graphics.centeredText(font,title,bgX+bgWidth/2,top-14,0xFFDDDDDD);if(reveal>font.width(tip))graphics.centeredText(font,tip,bgX+bgWidth/2,top+(price?17:28),0xFFDDDDDD);
        int valueX=left+labelWidth+18;frame(graphics,left+labelWidth+14,top-3,barWidth+8,price?16:27);stretch(graphics,left+labelWidth+17,top,barWidth+2,price?10:21,75,9,1,1);
        for(int r=price?1:0;r<2;r++){int y=top+(price?0:r)*11;crop(graphics,left-4,y,labelWidth+8,11,0,31);for(int x=0;x<barWidth;x+=248)crop(graphics,valueX+x,y+1,Math.min(248,barWidth-x),8,7,0);for(int m=0;m<=maximum/step();m++)crop(graphics,valueX+m*(step()+4),y+1,7,8,0,0);graphics.text(font,label(r),left,y+1,0xFF442000,false);}
        frame(graphics,left-7,top-3,labelWidth+14,price?16:27);if(ticksOpen<1)return;shift=minecraft.hasShiftDown();closestValue(mouseX,mouseY,shift);
        var cursor=getCoordinateOfValue(row,column);var text=row==0&&column==maximum?Component.translatable("create.logistics.filter.any_amount_short"):Component.literal((price?"":row==0?"\u2264":"=")+Math.max(1,column));int cursorWidth=font.width(text)/2*2+3,x=(int)cursor.x-cursorWidth/2,y=(int)cursor.y-7;crop(graphics,x-3,y,3,14,0,9);crop(graphics,x,y,cursorWidth,14,4,9);crop(graphics,x+cursorWidth,y,3,14,61,9);graphics.text(font,text,x+2,y+3,0xFF442000,false);
    }
}
