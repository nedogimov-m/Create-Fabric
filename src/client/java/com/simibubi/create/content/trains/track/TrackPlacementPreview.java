package com.simibubi.create.content.trains.track;

import com.simibubi.create.*;
import com.simibubi.create.foundation.mixin.client.HudTrackPlacementAccessor;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.*;

/** Actual source turn/S-bend/slope preview and 5x5 nearby placement hints through native extracted Vulkan gizmos. */
public final class TrackPlacementPreview {
    public record Frame(DrawableGizmoPrimitives primitives,BlockPos target,boolean valid,String message,int lines,int hintQuads,int requiredTracks,int requiredGirders,boolean hasTracks,boolean hasGirders,int warmup,boolean maximised,Item pavingItem,int requiredPavement,boolean hasPavement,int pavementCells){}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest;
    private static float animation;
    private static int warmup;
    private static BlockPos lastTarget;
    private static boolean lastMaximised;
    public static Frame latest(){return latest;}
    private static int mix(float blend){int a=0xEA5C2B,b=0x95CD41;int r=(int)(((a>>16)&255)*(1-blend)+((b>>16)&255)*blend),g=(int)(((a>>8)&255)*(1-blend)+((b>>8)&255)*blend),blue=(int)((a&255)*(1-blend)+(b&255)*blend);return 0xFF000000|r<<16|g<<8|blue;}
    private static void line(DrawableGizmoPrimitives output,Vec3 start,Vec3 end,int color,float width){if(start.distanceToSqr(end)>1e-12)output.addLine(start,end,color,width);}
    private static Frame empty(){return new Frame(new DrawableGizmoPrimitives(),null,false,null,0,0,0,0,false,false,0,false,null,0,true,0);}
    private static TrackPlacement.PlacementInfo inspect(Minecraft client,BlockPos pos,BlockState state,ItemStack stack,boolean maximised){return TrackPlacement.preview(client.level,client.player,pos,state,stack,client.player.getOffhandItem().is(AllBlocks.METAL_GIRDER.asItem()),maximised);}
    private static void tick(Minecraft client){
        if(client.player==null||client.level==null||client.gui.screen()!=null||client.player.isSpectator()||!client.player.mayBuild()||client.gui.hud.isHidden()||!(client.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK){latest=empty();warmup=0;lastTarget=null;return;}
        var player=client.player;var stack=player.getMainHandItem();if(!(stack.getItem() instanceof TrackBlockItem item)||!stack.has(AllDataComponents.TRACK_CONNECTING_FROM)){latest=empty();warmup=0;lastTarget=null;return;}
        var pos=hit.getBlockPos();if(!LoadedLevelAccess.isLoaded(client.level,pos)){latest=empty();warmup=0;lastTarget=null;return;}var state=LoadedLevelAccess.blockState(client.level,pos);
        if(!(state.getBlock() instanceof ITrackBlock)){
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);var adjusted=item.updatePlacementContext(context);if(adjusted==null||!LoadedLevelAccess.isLoaded(client.level,adjusted.getClickedPos())){latest=empty();warmup=0;lastTarget=null;return;}pos=adjusted.getClickedPos();for(var side:Direction.values())if(!LoadedLevelAccess.isLoaded(client.level,pos.relative(side))){latest=empty();warmup=0;lastTarget=null;return;}state=item.getBlock().getStateForPlacement(adjusted);if(state==null){latest=empty();warmup=0;lastTarget=null;return;}
        }
        boolean maximised=client.options.keySprint.isDown();var info=inspect(client,pos,state,stack,maximised);
        if(!pos.equals(lastTarget)||lastMaximised!=maximised)warmup=0;lastTarget=pos.immutable();lastMaximised=maximised;
        if(info.valid&&(maximised||info.end1Extent>0&&info.end2Extent>0))warmup=Math.min(20,warmup+1);else warmup=0;
        animation+=(info.valid?1-animation:-animation)*.25f;int color=mix(animation),lines=0,hints=0;var output=new DrawableGizmoPrimitives();var up=new Vec3(0,4/16d,0);
        if(info.end1!=null&&info.end2!=null){
            for(boolean first:new boolean[]{true,false}){
                var endpoint=first?info.end1:info.end2;var axis=first?info.axis1:info.axis2;var normal=first?info.normal1:info.normal2;int extent=info.valid?(first?info.end1Extent:info.end2Extent):0;
                var direction=axis.normalize();var side=normal.cross(direction).scale(15/16d);var offset=direction.scale(.125);var extension=direction.scale((extent-(first&&info.curve==null&&extent>0?2:0))*axis.length());
                for(int sign:new int[]{-1,1}){var start=endpoint.add(side.scale(sign)).add(up);line(output,start.subtract(offset),start.add(extension),color,2);lines++;}
            }
        }
        if(info.curve!=null){var curve=info.curve;Vec3 previousA=null,previousB=null;float size=animation*7/8f+1/8f;int segments=curve.getSegmentCount();
            for(int i=0;i<=segments;i++){double t=i/(double)segments;var point=curve.getPosition(t);Vec3 before=curve.getPosition(Math.max(0,t-1e-4)),after=curve.getPosition(Math.min(1,t+1e-4));var derivative=after.subtract(before).normalize();var normal=curve.getNormal(t).cross(derivative).scale(15/16d);var a=point.add(normal).add(up);var b=point.subtract(normal).add(up);
                if(previousA!=null){Vec3 middleA=a.add(previousA).scale(.5),middleB=b.add(previousB).scale(.5);line(output,middleA.lerp(previousA,size),middleA.lerp(a,size),color,2);line(output,middleB.lerp(previousB,size),middleB.lerp(b,size),color,2);lines+=2;}previousA=a;previousB=b;
            }
        }
        for(var entry:info.desiredPavement.entrySet()){
            var stateToPlace=entry.getValue();double height=stateToPlace.getOptionalValue(SlabBlock.TYPE).orElse(SlabType.DOUBLE)==SlabType.BOTTOM?.5:1;var corner=Vec3.atLowerCornerOf(entry.getKey()).add(0,height+.008,0);int pavementColor=info.valid?0xAA95CD41:0xAAEA5C2B;
            line(output,corner,corner.add(1,0,0),pavementColor,1);line(output,corner.add(1,0,0),corner.add(1,0,1),pavementColor,1);line(output,corner.add(1,0,1),corner.add(0,0,1),pavementColor,1);line(output,corner.add(0,0,1),corner,pavementColor,1);lines+=4;
        }
        if(hit.getDirection()==Direction.UP){
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var at=pos.offset(x,0,z);if(!LoadedLevelAccess.isLoaded(client.level,at))continue;var candidate=inspect(client,at,state,stack,maximised);int hint=candidate.valid?0x6695CD41:0x66EA5C2B;var bottom=Vec3.atLowerCornerOf(at).add(0,.005,0);
                for(int a=0;a<4;a++)for(int b=0;b<4;b++)if((a+b)%2==0){var v=bottom.add(a/4d,0,b/4d);output.addQuad(v,v.add(0,0,.25),v.add(.25,0,.25),v.add(.25,0,0),hint);hints++;}
            }
        }
        latest=new Frame(output,pos.immutable(),info.valid,info.message,lines,hints,info.requiredTracks,info.requiredGirders,info.hasRequiredTracks,info.hasRequiredGirders,warmup,maximised,info.pavingItem,info.requiredPavement,info.hasRequiredPavement,info.desiredPavement.size());
        if(info.valid)player.sendOverlayMessage(Component.translatable("create.track.valid_connection").withStyle(ChatFormatting.GREEN));else if(info.message!=null)player.sendOverlayMessage(Component.translatable(info.message).withStyle(info.message.equals("create.track.second_point")?ChatFormatting.WHITE:ChatFormatting.RED));
    }
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(TrackPlacementPreview::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{latest=null;animation=0;warmup=0;lastTarget=null;FRAMES.clear();});
        LevelExtractionEvents.END_EXTRACTION.register(context->FRAMES.put(context.levelState(),latest==null?empty():latest));
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives().submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("track_placement"),(gui,delta)->{
            var frame=latest;var client=Minecraft.getInstance();if(frame==null||frame.target()==null||client.player==null||client.gui.screen()!=null||client.gui.hud.isHidden()||client.player.isSpectator())return;int y=gui.guiHeight()-86,x=gui.guiWidth()/2-38;
            if(!client.player.isCreative()&&(frame.requiredTracks()>0||frame.requiredGirders()>0||frame.requiredPavement()>0)){
                gui.item(new ItemStack(client.player.getMainHandItem().getItem()),x,y);gui.text(client.font,Integer.toString(frame.requiredTracks()),x+18,y+4,frame.hasTracks()?0xFF95CD41:0xFFEA5C2B,false);
                if(frame.pavingItem()!=null&&frame.requiredPavement()>0){gui.item(new ItemStack(frame.pavingItem()),x+48,y);gui.text(client.font,Integer.toString(frame.requiredPavement()),x+66,y+4,frame.hasPavement()?0xFF95CD41:0xFFEA5C2B,false);}
                if(frame.requiredGirders()>0&&frame.pavingItem()!=AllBlocks.METAL_GIRDER.asItem()){int offset=frame.requiredPavement()>0?96:48;gui.item(new ItemStack(AllBlocks.METAL_GIRDER),x+offset,y);gui.text(client.font,Integer.toString(frame.requiredGirders()),x+offset+18,y+4,frame.hasGirders()?0xFF95CD41:0xFFEA5C2B,false);}
            }
            if(frame.warmup()>=4&&((HudTrackPlacementAccessor)client.gui.hud).create$trackToolHighlightTimer()<=0){var text=Component.translatable("create.track.hold_for_smooth_curve",Component.keybind("key.sprint").withStyle(frame.maximised()?ChatFormatting.WHITE:ChatFormatting.GRAY));int alpha=(int)(Math.clamp((frame.warmup()-4)/3f,.1f,1)*255);gui.text(client.font,text,(gui.guiWidth()-client.font.width(text))/2,gui.guiHeight()-61,alpha<<24|0x4ADB4A,false);}
        });
    }
    private TrackPlacementPreview(){}
}
