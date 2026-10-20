package com.simibubi.create.content.equipment.goggles;

import com.simibubi.create.*;
import com.simibubi.create.api.equipment.goggles.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** The overlay gathers world information, then hands only text, item and geometry to the native GUI extractor. */
public final class GoggleOverlayRenderer {
    public static int hoverTicks;
    public static BlockPos lastHovered;
    public static final HudElement OVERLAY=GoggleOverlayRenderer::renderOverlay;
    public record Information(BlockPos position,ItemStack icon,List<Component> lines){}
    public record Layout(int x,int y,int width,int height,List<FormattedCharSequence> lines){}
    private GoggleOverlayRenderer(){}
    public static void register(){
        GoggleOverlaySettings.INSTANCE.load();HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("goggles"),OVERLAY);
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("create").then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("overlay")
            .executes(context->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().gui.setScreen(new GoggleConfigScreen()));return 1;})
            .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("reset").executes(context->{var settings=GoggleOverlaySettings.INSTANCE;settings.overlayOffsetX=20;settings.overlayOffsetY=0;settings.save();return 1;})))));
    }
    public static BlockPos proxiedOverlayPosition(Level level,BlockPos pos){var state=LoadedLevelAccess.blockState(level,pos);return state.getBlock() instanceof IProxyHoveringInformation proxy?proxy.getInformationSource(level,pos,state):pos;}
    public static Information collect(Level level,Player player,BlockPos hit){
        var pos=proxiedOverlayPosition(level,hit);if(pos==null||!level.isLoaded(pos))return null;
        var entity=LoadedLevelAccess.blockEntity(level,pos);boolean sneaking=player.isShiftKeyDown();var lines=new ArrayList<Component>();
        var icon=entity instanceof IHaveCustomOverlayIcon custom?custom.getIcon(sneaking):new ItemStack(AllItems.GOGGLES);
        if(entity instanceof IHaveGoggleInformation goggles&&GogglesItem.isWearingGoggles(player)){
            if(!goggles.addToGoggleTooltip(lines,sneaking))lines.clear();
        }
        if(entity instanceof IHaveHoveringInformation hover&&!(entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic&&kinetic.isOverstressed()&&!GoggleOverlaySettings.INSTANCE.enableOverstressedTooltip)){
            int start=lines.size();if(start>0)lines.add(Component.empty());
            if(!hover.addToTooltip(lines,sneaking,player))while(lines.size()>start)lines.removeLast();
        }
        return lines.isEmpty()?null:new Information(pos.immutable(),icon.copy(),List.copyOf(lines));
    }
    public static Layout layout(Minecraft client,Information info,int width,int height,int offsetX,int offsetY){
        int maxWidth=Math.min(200,Math.max(40,width-24));var lines=new ArrayList<FormattedCharSequence>();
        for(var line:info.lines())lines.addAll(client.font.split(line,maxWidth));
        int textWidth=lines.stream().mapToInt(client.font::width).max().orElse(0);int textHeight=8+(lines.size()-1)*10+(lines.size()>1?2:0);
        int x=(int)Math.clamp((long)width/2+offsetX,8,Math.max(8,width-textWidth-12));
        int y=(int)Math.clamp((long)height/2+offsetY,20,Math.max(20,height-textHeight-8));return new Layout(x,y,textWidth,textHeight,List.copyOf(lines));
    }
    public static void renderOverlay(GuiGraphicsExtractor gui,DeltaTracker delta){
        var client=Minecraft.getInstance();
        if(!com.simibubi.create.infrastructure.config.AllConfigs.client().tooltips.get()||client.player==null||client.level==null||client.gameMode==null||client.gui.hud.isHidden()||client.gameMode.isSpectator()||client.gui.screen()!=null){resetHover();return;}
        if(!(client.hitResult instanceof BlockHitResult hit)||client.hitResult.getType()!=HitResult.Type.BLOCK){resetHover();return;}
        if(com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderHandler.hasHighlightedBox(hit.getBlockPos())){resetHover();return;}
        var hovered=LoadedLevelAccess.blockState(client.level,hit.getBlockPos());
        if(client.player.mayBuild()&&!client.player.isShiftKeyDown()&&com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.supports(hovered)&&com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.activeSide(hovered,hit.getDirection())){resetHover();return;}
        var info=collect(client.level,client.player,hit.getBlockPos());if(info==null){resetHover();return;}
        if(GogglesItem.isWearingGoggles(client.player))com.simibubi.create.content.kinetics.gauge.GaugeObservationClient.observe(info.position());
        lastHovered=hit.getBlockPos().immutable();hoverTicks=Math.min(24,hoverTicks+1);
        float fade=Math.clamp((hoverTicks+delta.getGameTimeDeltaPartialTick(false))/24f,0,1);
        var settings=GoggleOverlaySettings.INSTANCE;var layout=layout(client,info,gui.guiWidth(),gui.guiHeight(),settings.overlayOffsetX,settings.overlayOffsetY);draw(gui,client,info,layout,fade);
    }
    private static void resetHover(){lastHovered=null;hoverTicks=0;}
    public static void draw(GuiGraphicsExtractor gui,Minecraft client,Information info,Layout layout,float fade){
        if(fade<=0)return;
        int x=layout.x(),y=layout.y(),right=x+layout.width()+4,bottom=y+layout.height()+4;
        gui.pose().pushMatrix();gui.pose().translate((float)(Math.pow(1-fade,3)*8),0);
        var settings=GoggleOverlaySettings.INSTANCE;
        int background=alpha(settings.overlayCustomColor?settings.overlayBackgroundColor:0xB4100010,fade),top=alpha(settings.overlayCustomColor?settings.overlayBorderColorTop:0x505000FF,fade),low=alpha(settings.overlayCustomColor?settings.overlayBorderColorBot:0x5028007F,fade);
        gui.fill(x-4,y-4,right,bottom,background);gui.fill(x-3,y-5,right-1,y-4,background);gui.fill(x-3,bottom,right-1,bottom+1,background);
        gui.fill(x-3,y-3,right-1,y-2,top);gui.fill(x-3,bottom-2,right-1,bottom-1,low);gui.fillGradient(x-4,y-3,x-3,bottom-1,top,low);gui.fillGradient(right-1,y-3,right,bottom-1,top,low);
        gui.item(info.icon(),x+10,y-20);int textY=y;
        for(int i=0;i<layout.lines().size();i++){gui.text(client.font,layout.lines().get(i),x,textY,alpha(0xFFFFFFFF,fade),true);textY+=i==0?12:10;}
        gui.pose().popMatrix();
    }
    private static int alpha(int color,float fade){return color&0xFFFFFF|Math.clamp((int)((color>>>24)*fade),0,255)<<24;}
}
