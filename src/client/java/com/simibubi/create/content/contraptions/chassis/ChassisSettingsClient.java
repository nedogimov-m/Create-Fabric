package com.simibubi.create.content.contraptions.chassis;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;

/** Actual held-wrench range editing, retaining source 1-based values and Ctrl group semantics. */
public final class ChassisSettingsClient {
    private static BlockPos held;
    private static Direction face;
    private static int ticks;
    private static boolean targeted(Minecraft client,BlockPos pos){return client.player!=null&&client.level!=null&&client.player.mayBuild()&&!client.player.isSpectator()&&!client.player.isShiftKeyDown()&&client.player.getMainHandItem().is(AllItems.WRENCH)&&client.level.getBlockEntity(pos) instanceof ChassisBlockEntity&&client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&ChassisSettings.panelHit(pos,hit);}
    public static void register(){
        ChassisRangeDisplay.register();ChassisCTModel.register();StickerRenderer.registerModels();
        ClientPlayNetworking.registerGlobalReceiver(ChassisSettings.RangeLimit.TYPE,(packet,context)->{ChassisSettings.installClientMaximum(packet.maximum());});
        ChassisSettings.CLIENT_START=(pos,side)->{if(held==null){held=pos;face=side;ticks=0;}};
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(held==null)return;if(client.gui.screen()!=null||!client.options.keyUse.isDown()||!targeted(client,held)||((BlockHitResult)client.hitResult).getDirection()!=face){held=null;return;}if(ticks++<5)return;var pos=held;held=null;client.gui.setScreen(new RangeScreen(pos,((ChassisBlockEntity)client.level.getBlockEntity(pos)).getRange()));});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("chassis_range"),(graphics,delta)->{
            var client=Minecraft.getInstance();if(client.gui.hud.isHidden()||client.gui.screen()!=null||!(client.hitResult instanceof BlockHitResult hit)||!targeted(client,hit.getBlockPos()))return;
            var chassis=(ChassisBlockEntity)client.level.getBlockEntity(hit.getBlockPos());var label=Component.translatable(chassis.getBlockState().getBlock() instanceof RadialChassisBlock?"create.contraptions.chassis.radius":"create.contraptions.chassis.range");graphics.centeredText(client.font,label.append(": "+chassis.getRange()),graphics.guiWidth()/2,graphics.guiHeight()-88,0xFFFBDC7D);graphics.centeredText(client.font,Component.translatable("create.gui.value_settings.hold_to_edit"),graphics.guiWidth()/2,graphics.guiHeight()-76,0xFFFFFFFF);
        });
    }
    public static final class RangeScreen extends Screen {
        private final BlockPos pos;
        private int selected,left,top,barWidth;
        private boolean bulk;
        public RangeScreen(BlockPos pos,int value){super(Component.translatable(Minecraft.getInstance().level.getBlockState(pos).getBlock() instanceof RadialChassisBlock?"create.contraptions.chassis.radius":"create.contraptions.chassis.range"));this.pos=pos.immutable();selected=Math.clamp(value,1,Math.max(1,ChassisSettings.maximumForCurrentSide()));}
        public BlockPos position(){return pos;}
        public int selection(){return selected;}
        public boolean bulk(){return bulk;}
        public Vec2 coordinate(int value){return new Vec2(left+(Math.clamp(value,1,Math.max(1,ChassisSettings.maximumForCurrentSide()))-1)*barWidth/(float)Math.max(1,ChassisSettings.maximumForCurrentSide()-1),top);}
        @Override protected void init(){barWidth=(int)Math.min(320L,Math.max(80L,(long)ChassisSettings.maximumForCurrentSide()*10));left=width/2-barWidth/2;top=height/2;warp();preview();}
        private void warp(){var p=coordinate(selected);var window=minecraft.getWindow();double scaleX=(double)window.getScreenWidth()/window.getGuiScaledWidth();double scaleY=(double)window.getScreenHeight()/window.getGuiScaledHeight();InputConstants.releaseMouse(window,p.x*scaleX,p.y*scaleY);minecraft.mouseHandler.onMove(window.handle(),p.x*scaleX,p.y*scaleY,0,0);}
        private void preview(){bulk=com.simibubi.create.AllKeys.ctrlDown();ChassisRangeDisplay.show(pos,bulk,selected);}
        private void select(double x){selected=Math.clamp(1+(int)Math.round((x-left)*Math.max(1,ChassisSettings.maximumForCurrentSide()-1)/barWidth),1,Math.max(1,ChassisSettings.maximumForCurrentSide()));preview();}
        @Override public void tick(){if(minecraft.player==null||minecraft.level==null||!(minecraft.level.getBlockEntity(pos) instanceof ChassisBlockEntity)||!minecraft.player.getMainHandItem().is(AllItems.WRENCH)){onClose();return;}preview();}
        @Override public void mouseMoved(double x,double y){select(x);}
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy){selected=Math.clamp(selected+(int)Math.signum(dy),1,Math.max(1,ChassisSettings.maximumForCurrentSide()));warp();preview();return true;}
        @Override public boolean mouseReleased(MouseButtonEvent event){if(minecraft.options.keyUse.matchesMouse(event)){save();return true;}return super.mouseReleased(event);}
        @Override public boolean keyReleased(KeyEvent event){if(minecraft.options.keyUse.matches(event)){save();return true;}preview();return super.keyReleased(event);}
        @Override public boolean keyPressed(KeyEvent event){if(event.isEscape())return super.keyPressed(event);if(event.isConfirmation()){save();return true;}if(event.isLeft()||event.isRight()){selected=Math.clamp(selected+(event.isRight()?1:-1),1,Math.max(1,ChassisSettings.maximumForCurrentSide()));warp();preview();return true;}preview();return super.keyPressed(event);}
        public void save(){ClientPlayNetworking.send(new ChassisSettings.SetRange(pos,selected,com.simibubi.create.AllKeys.ctrlDown()));onClose();}
        @Override public void onClose(){ChassisRangeDisplay.clear();super.onClose();}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
            graphics.fillGradient(0,0,width,height,0x50101010,0x50101010);graphics.fill(left-20,top-38,left+barWidth+20,top+48,0xEE302D36);graphics.centeredText(font,title,width/2,top-32,0xFFEDEDED);graphics.fill(left,top-2,left+barWidth,top+3,0xFF887A62);
            int maximum=ChassisSettings.maximumForCurrentSide();long step=Math.max(1L,((long)maximum-1)/Math.max(1,barWidth));for(long value=1;value<=maximum;value+=step){var p=coordinate((int)value);graphics.fill((int)p.x,top-4,(int)p.x+1,top+5,0xFFE7D7BA);}
            var cursor=coordinate(selected);graphics.fill((int)cursor.x-4,top-7,(int)cursor.x+5,top+8,0xFFFBDC7D);graphics.centeredText(font,Component.translatable("create.contraptions.chassis.distance").append(": "+selected),width/2,top+12,0xFFFBDC7D);graphics.centeredText(font,Component.translatable("create.gui.value_settings.release_to_confirm",Component.keybind("key.use")),width/2,top+28,0xFFEDEDED);
        }
    }
    private ChassisSettingsClient(){}
}
