package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Actual block clicks build source target settings; all preview lines use native extraction/submission. */
public final class ArmInteractionPointHandler {
    public static final int[] ICON_U={64,80,96},ICON_V={64,64,64};
    private static final LinkedHashMap<BlockPos,ArmInteractionPoint> SELECTION=new LinkedHashMap<>();
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static ItemStack currentItem;
    private static Level selectedLevel;
    private static Frame latest;
    public record Frame(DrawableGizmoPrimitives primitives,int points,int lines,BlockPos arm){}
    public static List<ArmInteractionPoint> selection(){return List.copyOf(SELECTION.values());}
    public static Frame latest(){return latest;}
    private static boolean holding(Minecraft client){return client.player!=null&&client.player.getMainHandItem().getItem() instanceof ArmItem;}
    private static void begin(Minecraft client){
        var item=client.player.getMainHandItem();
        if(selectedLevel!=client.level||currentItem==null||!ItemStack.isSameItemSameComponents(currentItem,item))SELECTION.clear();
        selectedLevel=client.level;currentItem=item.copy();
    }
    public static boolean select(BlockPos pos){
        var client=Minecraft.getInstance();if(!holding(client)||client.player.isSpectator()||!client.player.mayBuild()||!client.level.mayInteract(client.player,pos))return false;
        begin(client);var point=SELECTION.get(pos);
        if(point==null){point=ArmInteractionPoint.create(client.level,pos,LoadedLevelAccess.blockState(client.level,pos));if(point==null)return false;SELECTION.put(pos.immutable(),point);}
        point.cycleMode();
        client.player.sendOverlayMessage(Component.translatable("create."+point.getMode().getTranslationKey(),LoadedLevelAccess.blockState(client.level,pos).getBlock().getName()).withColor(point.getMode().getColor()));return true;
    }
    public static boolean deselect(BlockPos pos){
        var client=Minecraft.getInstance();if(!holding(client))return false;begin(client);
        boolean removed=SELECTION.remove(pos)!=null;
        return removed||ArmInteractionPoint.isInteractable(client.level,pos,LoadedLevelAccess.blockState(client.level,pos));
    }
    public static void flushSettings(BlockPos pos,long nonce){
        var client=Minecraft.getInstance();if(client.player==null||client.level==null)return;
        int before=SELECTION.size();
        SELECTION.values().removeIf(point->point.getLevel()!=client.level||!point.getPos().closerThan(pos,ArmConfig.get(client.level))||!point.isValid());
        var data=SELECTION.values().stream().map(point->point.data(pos)).toList();
        ClientPlayNetworking.send(new ArmPlacementPacket(data,pos,nonce));
        int removed=before-SELECTION.size();
        if(removed>0)client.player.sendOverlayMessage(Component.translatable("create.mechanical_arm.points_outside_range",removed).withColor(0xFF5555));
        else if(!data.isEmpty()){int inputs=(int)data.stream().filter(point->point.mode()==ArmInteractionPoint.Mode.TAKE).count();client.player.sendOverlayMessage(Component.translatable("create.mechanical_arm.summary",inputs,data.size()-inputs));}
        SELECTION.clear();currentItem=null;
    }
    private static void tick(Minecraft client){
        if(client.level!=selectedLevel){SELECTION.clear();currentItem=null;selectedLevel=client.level;}
        if(holding(client))begin(client);else currentItem=null;
    }
    private static void line(DrawableGizmoPrimitives primitives,Vec3 start,Vec3 end,int color){primitives.addLine(start,end,color,2);}
    private static void box(DrawableGizmoPrimitives primitives,AABB bounds,int color){
        var min=new Vec3(bounds.minX,bounds.minY,bounds.minZ);var max=new Vec3(bounds.maxX,bounds.maxY,bounds.maxZ);
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            double[] start={min.x,min.y,min.z},end={min.x,min.y,min.z};
            int other=(axis+1)%3,last=(axis+2)%3;
            double[] high={max.x,max.y,max.z};start[other]=end[other]=a==0?start[other]:high[other];start[last]=end[last]=b==0?start[last]:high[last];end[axis]=high[axis];
            line(primitives,new Vec3(start[0],start[1],start[2]),new Vec3(end[0],end[1],end[2]),color);
        }
    }
    public static Frame extract(Minecraft client){
        var primitives=new DrawableGizmoPrimitives();var points=new ArrayList<ArmInteractionPoint>();BlockPos armPos=null;
        if(client.level!=null&&client.player!=null&&client.gui.screen()==null){
            if(holding(client))points.addAll(SELECTION.values());
            else if(client.player.getMainHandItem().is(AllItems.WRENCH)&&client.hitResult instanceof BlockHitResult hit&&LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof ArmBlockEntity arm){armPos=hit.getBlockPos();points.addAll(arm.inputs());points.addAll(arm.outputs());}
        }
        int rendered=0;
        for(var point:points){
            if(!point.isValid())continue;var shape=LoadedLevelAccess.blockState(client.level,point.getPos()).getShape(client.level,point.getPos());
            if(shape.isEmpty())continue;int color=0xFF000000|point.getMode().getColor();box(primitives,shape.bounds().move(point.getPos()).inflate(.005),color);rendered++;
        }
        return new Frame(primitives,rendered,rendered*12,armPos);
    }
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(ArmPlacementPacket.ClientBoundRequest.TYPE,(packet,context)->flushSettings(packet.pos(),packet.nonce()));
        ClientPlayNetworking.registerGlobalReceiver(ArmNetworking.Range.TYPE,(packet,context)->ArmConfig.installClient(packet.value()));
        ClientPlayNetworking.registerGlobalReceiver(ArmNetworking.Settings.TYPE,(packet,context)->{if(context.client().level!=null&&LoadedLevelAccess.blockEntity(context.client().level,packet.pos()) instanceof ArmBlockEntity)context.client().gui.setScreen(new ArmSettingsScreen(packet.pos(),packet.mode()));});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{SELECTION.clear();FRAMES.clear();currentItem=null;selectedLevel=null;latest=null;ArmConfig.clearClient();});
        ClientTickEvents.END_CLIENT_TICK.register(ArmInteractionPointHandler::tick);
        UseBlockCallback.EVENT.register((player,level,hand,hit)->level.isClientSide()&&hand==InteractionHand.MAIN_HAND&&select(hit.getBlockPos())?InteractionResult.SUCCESS:InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->level.isClientSide()&&hand==InteractionHand.MAIN_HAND&&deselect(pos)?InteractionResult.FAIL:InteractionResult.PASS);
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract(Minecraft.getInstance());FRAMES.put(context.levelState(),frame);latest=frame;});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives().submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("arm_selection_mode"),(gui,delta)->{
            var client=Minecraft.getInstance();if(client.level==null||client.player==null||client.gui.screen()!=null||client.gui.hud.isHidden()||!(client.hitResult instanceof BlockHitResult hit)||!(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof ArmBlockEntity arm)||!ArmBlock.settingsHit(arm.getBlockState(),hit.getBlockPos(),hit))return;
            gui.centeredText(client.font,Component.translatable(arm.getSelectionMode().getTranslationKey()),gui.guiWidth()/2,gui.guiHeight()-88,0xFFFBDC7D);
        });
    }
    private ArmInteractionPointHandler(){}
}
