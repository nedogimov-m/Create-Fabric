package com.simibubi.create.content.contraptions.piston;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
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

/** Held wrench mode editing; native release-to-confirm uses the actual server panel ray. */
public final class PistonSettingsClient {
    private static BlockPos held;
    private static Direction face;
    private static int ticks;
    public static Component label(MovementMode mode){return Component.translatable("create.contraptions.movement_mode."+mode.name().toLowerCase(java.util.Locale.ROOT));}
    private static boolean targeted(Minecraft client,BlockPos pos){return client.level!=null&&client.player!=null&&!client.player.isSpectator()&&client.player.mayBuild()&&!client.player.isShiftKeyDown()&&client.player.getMainHandItem().is(AllItems.WRENCH)&&client.level.getBlockEntity(pos) instanceof LinearActuatorBlockEntity&&client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&PistonSettings.panelHit(client.level.getBlockState(pos),pos,hit);}
    public static void register(){
        PistonSettings.CLIENT_START=(pos,side)->{if(held==null){held=pos;face=side;ticks=0;}};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(held==null)return;if(client.gui.screen()!=null||!client.options.keyUse.isDown()||!targeted(client,held)||((BlockHitResult)client.hitResult).getDirection()!=face){held=null;return;}
            if(ticks++<5)return;var pos=held;held=null;client.gui.setScreen(new ModeScreen(pos,((LinearActuatorBlockEntity)client.level.getBlockEntity(pos)).getMovementMode()));
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("piston_mode"),(graphics,delta)->{
            var client=Minecraft.getInstance();if(client.gui.hud.isHidden()||client.gui.screen()!=null||!(client.hitResult instanceof BlockHitResult hit)||!targeted(client,hit.getBlockPos()))return;
            var piston=(LinearActuatorBlockEntity)client.level.getBlockEntity(hit.getBlockPos());graphics.centeredText(client.font,label(piston.getMovementMode()),graphics.guiWidth()/2,graphics.guiHeight()-88,0xFFFBDC7D);graphics.centeredText(client.font,Component.translatable("create.gui.value_settings.hold_to_edit"),graphics.guiWidth()/2,graphics.guiHeight()-76,0xFFFFFFFF);
        });
    }
    public static final class ModeScreen extends Screen {
        private final BlockPos pos;
        private int selected,left,top;
        public ModeScreen(BlockPos pos,MovementMode mode){super(Component.translatable("create.contraptions.movement_mode"));this.pos=pos.immutable();selected=mode.ordinal();}
        public BlockPos position(){return pos;}
        public Vec2 coordinate(MovementMode mode){return new Vec2(left+170,top+mode.ordinal()*24+10);}
        @Override protected void init(){left=width/2-170;top=height/2-32;warp();}
        private void warp(){var point=coordinate(MovementMode.values()[selected]);var window=minecraft.getWindow();double scaleX=(double)window.getScreenWidth()/window.getGuiScaledWidth();double scaleY=(double)window.getScreenHeight()/window.getGuiScaledHeight();InputConstants.releaseMouse(window,point.x*scaleX,point.y*scaleY);minecraft.mouseHandler.onMove(window.handle(),point.x*scaleX,point.y*scaleY,0,0);}
        @Override public void mouseMoved(double x,double y){selected=Math.clamp((int)((y-top)/24),0,2);}
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy){selected=Math.floorMod(selected-(int)Math.signum(dy),3);warp();return true;}
        @Override public void tick(){if(minecraft.player==null||minecraft.level==null||!(minecraft.level.getBlockEntity(pos) instanceof LinearActuatorBlockEntity)||!minecraft.player.getMainHandItem().is(AllItems.WRENCH))onClose();}
        @Override public boolean mouseReleased(MouseButtonEvent event){if(minecraft.options.keyUse.matchesMouse(event)){save();return true;}return super.mouseReleased(event);}
        @Override public boolean keyReleased(KeyEvent event){if(minecraft.options.keyUse.matches(event)){save();return true;}return super.keyReleased(event);}
        @Override public boolean keyPressed(KeyEvent event){if(event.isEscape())return super.keyPressed(event);if(event.isConfirmation()){save();return true;}if(event.isUp()||event.isDown()){selected=Math.floorMod(selected+(event.isDown()?1:-1),3);warp();return true;}return super.keyPressed(event);}
        public void save(){ClientPlayNetworking.send(new PistonSettings.SetMode(pos,selected));onClose();}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
            graphics.fill(left-12,top-32,left+352,top+102,0xEE302D36);graphics.centeredText(font,title,width/2,top-24,0xFFEDEDED);
            for(var mode:MovementMode.values()){var point=coordinate(mode);if(selected==mode.ordinal())graphics.fill(left,top+selected*24,left+340,top+selected*24+22,0xFF64573E);graphics.centeredText(font,label(mode),(int)point.x,(int)point.y-4,selected==mode.ordinal()?0xFFFBDC7D:0xFFFFFFFF);}
            graphics.centeredText(font,Component.translatable("create.gui.value_settings.release_to_confirm",Component.keybind("key.use")),width/2,top+84,0xFFFFFFFF);
        }
    }
    private PistonSettingsClient(){}
}
