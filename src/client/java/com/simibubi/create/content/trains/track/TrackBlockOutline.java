package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlocks;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** Actual loaded curve ray picking and immutable native Vulkan selection outlines. */
public final class TrackBlockOutline {
    public record Frame(TrackCurveSelection.Hit selection,int lines,float breakProgress,DrawableGizmoPrimitives primitives){}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static TrackCurveSelection.Hit result;
    private static Frame latest;
    public static TrackCurveSelection.Hit result(){return result;}
    public static Frame latest(){return latest;}
    public static void pickCurves(Minecraft client,float partial){
        result=null;if(client.level==null||!(client.getCameraEntity() instanceof LocalPlayer player)||player.isSpectator())return;
        var from=player.getEyePosition(partial);double range=player.blockInteractionRange()+1;var to=from.add(player.getViewVector(partial).scale(range));
        double nearest=client.hitResult==null||client.hitResult.getType()==HitResult.Type.MISS?range*range:from.distanceToSqr(client.hitResult.getLocation());
        for(var endpoint:TrackCurveIndex.loaded(client.level))for(var curve:endpoint.getConnections().values()){
            if(!curve.primary)continue;var selected=TrackCurveSelection.trace(curve,from,to);if(selected==null)continue;double distance=from.distanceToSqr(selected.point());if(distance>=nearest)continue;nearest=distance;result=selected;
        }
        if(result!=null){client.hitResult=BlockHitResult.miss(result.point(),Direction.UP,BlockPos.containing(result.point()));client.crosshairPickEntity=null;}
    }
    private static Frame extract(Minecraft client){
        var output=new DrawableGizmoPrimitives();var selected=result;int lines=0;float progress=CurvedTrackInteraction.progress();
        if(selected!=null&&client.level!=null&&client.player!=null&&!client.gui.hud.isHidden()&&!client.player.isSpectator()&&client.gui.screen()==null){
            int color=client.player.getMainHandItem().is(AllBlocks.TRACK.asItem())?0x99FF2040:progress>0?0xAAFFFFFF:0x99000000;
            var points=new Vec3[8];for(int i=0;i<8;i++)points[i]=selected.anchor().add(selected.side().scale((i&1)==0?-1.375:1.375)).add(selected.up().scale((i&2)==0?-.125:.125)).add(selected.direction().scale((i&4)==0?-.5:.5));
            for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){output.addLine(points[i],points[i|bit],color,2);lines++;}
        }
        return new Frame(selected,lines,progress,output);
    }
    public static void register(){
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{result=null;latest=null;FRAMES.clear();});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract(Minecraft.getInstance());latest=frame;FRAMES.put(context.levelState(),frame);});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives().submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,outline)->{var frame=FRAMES.get(context.levelState());return frame==null||frame.selection()==null;});
    }
    private TrackBlockOutline(){}
}
