package com.simibubi.create.content.logistics.tunnel;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

/** Held-use mode selector with the seven original icons and native platform input. */
public final class TunnelModeScreen extends Screen {
    private final BlockPos pos;
    private int selected,left,top,ticks;
    public TunnelModeScreen(BlockPos pos,int mode){super(Component.translatable("create.logistics.when_multiple_outputs_available"));this.pos=pos.immutable();selected=mode;}
    public BlockPos position(){return pos;}
    @Override protected void init(){left=(width-7*24)/2;top=(height-24)/2;warp();}
    public Vec2 coordinate(int mode){return new Vec2(left+mode*24+12,top+12);}
    private void warp(){var point=coordinate(selected);var window=minecraft.getWindow();double scaleX=(double)window.getScreenWidth()/window.getGuiScaledWidth();double scaleY=(double)window.getScreenHeight()/window.getGuiScaledHeight();InputConstants.releaseMouse(window,point.x*scaleX,point.y*scaleY);minecraft.mouseHandler.onMove(window.handle(),point.x*scaleX,point.y*scaleY,0,0);}
    private void select(double x){selected=Math.clamp((int)((x-left)/24),0,6);}
    @Override public void mouseMoved(double x,double y){select(x);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){select(x);selected=Math.floorMod(selected+(int)Math.signum(dy),7);warp();return true;}
    @Override public boolean keyPressed(KeyEvent event){if(event.isEscape())return super.keyPressed(event);if(event.isConfirmation()){save();return true;}if(event.isLeft()||event.isRight()){selected=Math.floorMod(selected+(event.isRight()?1:-1),7);warp();return true;}return super.keyPressed(event);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(minecraft.options.keyUse.matchesMouse(event)){select(event.x());save();return true;}return super.mouseReleased(event);}
    @Override public boolean keyReleased(KeyEvent event){if(minecraft.options.keyUse.matches(event)){save();return true;}return super.keyReleased(event);}
    public void save(){ClientPlayNetworking.send(new TunnelSettings.SetMode(pos,selected));onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){ticks++;if(minecraft.level==null||!(minecraft.level.getBlockEntity(pos) instanceof BrassTunnelBlockEntity tunnel)||!tunnel.hasDistributionBehaviour())onClose();}
    public static void icon(GuiGraphicsExtractor graphics,int x,int y,int mode){graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/icons.png"),x,y,(mode+4)*16,48,16,16,256,256);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        graphics.fillGradient(0,0,width,height,0x50101010,0x50101010);graphics.fill(left-12,top-26,left+180,top+65,0xEF302725);graphics.centeredText(font,title,width/2,top-19,0xFFFBDC7D);
        for(int mode=0;mode<7;mode++){int x=left+mode*24;graphics.fill(x,top,x+23,top+24,selected==mode?0xFFC79950:0xFF655343);icon(graphics,x+4,top+4,mode);}
        graphics.centeredText(font,Component.translatable(BrassTunnelBlockEntity.SelectionMode.values()[selected].translationKey()),width/2,top+32,0xFFFFFFFF);graphics.centeredText(font,Component.translatable("create.gui.value_settings.release_to_confirm",Component.keybind("key.use")),width/2,top+49,0xFFDDDDDD);
    }
}
