package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.*;

/** Source two-corner selection and connected preview, rendered through native extract/submit gizmos. */
public final class SuperGlueSelectionHandler {
    public record Frame(BlockPos first,BlockPos hovered,Integer selected,AABB bounds,Set<BlockPos> cluster,boolean valid,int lines,DrawableGizmoPrimitives primitives){}
    private static final int PASSIVE=0xFF4D9162,HIGHLIGHT=0xFF68C586,FAIL=0xFFC5B548;
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static BlockPos first,hovered,soundSource;
    private static Set<BlockPos> cluster=Set.of(),flash=Set.of();
    private static SuperGlueEntity selected;
    private static ClientLevel level;
    private static int cooldown;
    private static boolean suppressAttack;
    private static Frame latest;
    public static Frame latest(){return latest;}
    public static BlockPos first(){return first;}
    private static boolean held(Minecraft client){return client.player!=null&&client.player.getMainHandItem().getItem() instanceof SuperGlueItem;}
    private static void hint(Minecraft client,String key,int color){client.player.sendOverlayMessage(Component.translatable("create.super_glue."+key).withColor(color&0xFFFFFF));}
    private static void clear(){first=null;hovered=null;cluster=Set.of();flash=Set.of();selected=null;cooldown=0;}
    private static void tick(Minecraft client){
        if(client.level!=level){clear();level=client.level;}
        if(client.player==null||client.level==null)return;
        if(cooldown>0)cooldown--;
        if(!held(client)){if(first!=null)clear();selected=null;return;}
        selected=null;soundSource=null;
        var eye=client.player.getEyePosition();var end=eye.add(client.player.getLookAngle().scale(client.player.blockInteractionRange()+1));
        if(first==null){double nearest=Double.POSITIVE_INFINITY;for(var glue:client.level.getEntitiesOfClass(SuperGlueEntity.class,client.player.getBoundingBox().inflate(32,16,32))){var clip=glue.getBoundingBox().clip(eye,end);if(clip.isEmpty())continue;double distance=eye.distanceToSqr(clip.get());if(distance>nearest)continue;nearest=distance;selected=glue;soundSource=BlockPos.containing(clip.get());}}
        BlockPos next=client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK?hit.getBlockPos():null;
        if(first==null||next==null){hovered=next;cluster=Set.of();return;}
        if(!first.closerThan(next,24)){hovered=next;cluster=Set.of();hint(client,"too_far",FAIL);return;}
        // Recompute when either geometry or existing glue changes, not just when the cursor changes blocks.
        hovered=next;var group=SuperGlueSelectionHelper.searchGlueGroup(client.level,first,hovered,true);cluster=group==null?Set.of():Set.copyOf(group);
        boolean reachable=cluster.contains(hovered),afford=SuperGlueSelectionHelper.collectGlueFromInventory(client.player,1,true);
        String key=client.player.isShiftKeyDown()?"click_to_discard":!reachable?"cannot_reach":!afford?"not_enough":"click_to_confirm";
        hint(client,key,reachable&&afford&&!client.player.isShiftKeyDown()?HIGHLIGHT:FAIL);
    }
    public static boolean onUse(Minecraft client){
        if(!held(client)||!client.player.mayBuild()||client.player.isSpectator())return false;
        if(client.player.isShiftKeyDown()){if(first!=null){clear();hint(client,"abort",FAIL);return true;}return false;}
        if(!(client.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return false;
        var pos=hit.getBlockPos();
        var blockState=client.level.getBlockState(pos);
        if(blockState.getBlock() instanceof AbstractChassisBlock chassis&&chassis.handlesGlueUse(blockState,hit.getDirection()))return false;
        if(first==null){first=pos;hovered=null;cluster=Set.of();SuperGlueItem.spawnParticles(client.level,pos,hit.getDirection(),true);hint(client,"first_pos",HIGHLIGHT);client.level.playLocalSound(pos,SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,.75f,1,false);return true;}
        if(!first.closerThan(pos,24)){hint(client,"too_far",FAIL);return true;}
        var group=SuperGlueSelectionHelper.searchGlueGroup(client.level,first,pos,true);
        if(group==null||!group.contains(pos)){hint(client,"cannot_reach",FAIL);return true;}
        if(!SuperGlueSelectionHelper.collectGlueFromInventory(client.player,1,true)){hint(client,"not_enough",FAIL);return true;}
        ClientPlayNetworking.send(new SuperGlueSelectionPacket(first,pos));flash=Set.copyOf(group);cooldown=40;first=null;hovered=null;cluster=Set.of();return true;
    }
    public static boolean onAttack(Minecraft client){
        if(!held(client)||!client.player.mayBuild()||client.player.isSpectator()||selected==null||selected.isRemoved())return false;
        ClientPlayNetworking.send(new SuperGlueRemovalPacket(selected.getId(),soundSource==null?selected.blockPosition():soundSource));selected=null;cooldown=0;suppressAttack=true;return true;
    }
    public static boolean suppressAttack(Minecraft client){if(!client.options.keyAttack.isDown())suppressAttack=false;return suppressAttack;}
    private static int box(DrawableGizmoPrimitives output,AABB box,int color,float width){var points=new Vec3[8];for(int i=0;i<8;i++)points[i]=new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ);for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)output.addLine(points[i],points[i|bit],color,width);return 12;}
    private static Frame extract(Minecraft client){
        var output=new DrawableGizmoPrimitives();int lines=0;AABB bounds=null;boolean valid=false;Integer id=null;
        if(held(client)&&client.level!=null&&client.gui.screen()==null){
            if(first==null)for(var glue:client.level.getEntitiesOfClass(SuperGlueEntity.class,client.player.getBoundingBox().inflate(32,16,32))){boolean highlight=cooldown==0&&glue==selected;lines+=box(output,glue.getBoundingBox(),highlight?HIGHLIGHT:PASSIVE,highlight?3:1);if(highlight)id=glue.getId();}
            if(first!=null&&hovered!=null){bounds=SuperGlueEntity.span(first,hovered);valid=first.closerThan(hovered,24)&&cluster.contains(hovered)&&SuperGlueSelectionHelper.collectGlueFromInventory(client.player,1,true)&&!client.player.isShiftKeyDown();lines+=box(output,bounds,valid?HIGHLIGHT:FAIL,3);for(var pos:cluster)lines+=box(output,new AABB(pos),PASSIVE,1);}
            if(cooldown>0)for(var pos:flash)lines+=box(output,new AABB(pos),0xFFB5F2C6,1);
        }
        return new Frame(first,hovered,id,bounds,cluster,valid,lines,output);
    }
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(SuperGlueSelectionHandler::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();level=null;latest=null;FRAMES.clear();suppressAttack=false;});
        ClientPlayNetworking.registerGlobalReceiver(SuperGlueHandler.Feedback.TYPE,(packet,context)->{if(context.player()!=null)hint(context.client(),packet.key(),packet.key().equals("success")?HIGHLIGHT:FAIL);});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract(Minecraft.getInstance());FRAMES.put(context.levelState(),frame);latest=frame;});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives().submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
    }
    private SuperGlueSelectionHandler(){}
}
