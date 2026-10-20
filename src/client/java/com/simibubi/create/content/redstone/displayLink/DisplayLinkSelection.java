package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;

/** Actual selected target/multiblock outline, extracted per frame and submitted without live world/item lookup. */
public final class DisplayLinkSelection {
    public record Frame(BlockPos target,AABB bounds,int lines,DrawableGizmoPrimitives primitives){}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest;
    public static Frame latest(){return latest;}
    private static Frame extract(){
        var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.screen()!=null)return null;var stack=client.player.getMainHandItem();if(!(stack.getItem()instanceof ClickToLinkBlockItem))return null;var selection=stack.get(AllDataComponents.CLICK_TO_LINK_DATA);if(selection==null||!selection.selectedDim().equals(client.level.dimension().identifier())||!LoadedLevelAccess.isLoaded(client.level,selection.selectedPos()))return null;var pos=selection.selectedPos();var target=DisplayTarget.get(client.level,pos);var shape=LoadedLevelAccess.blockState(client.level,pos).getShape(client.level,pos);var bounds=target==null?(shape.isEmpty()?new AABB(pos):shape.bounds().move(pos)):target.getMultiblockBounds(client.level,pos);var output=new DrawableGizmoPrimitives();int lines=0;
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){var first=new double[]{bounds.minX,bounds.minY,bounds.minZ};var second=first.clone();second[axis]=axis==0?bounds.maxX:axis==1?bounds.maxY:bounds.maxZ;int u=(axis+1)%3,v=(axis+2)%3;double[] max={bounds.maxX,bounds.maxY,bounds.maxZ};first[u]=second[u]=a==0?first[u]:max[u];first[v]=second[v]=b==0?first[v]:max[v];output.addLine(new Vec3(first[0],first[1],first[2]),new Vec3(second[0],second[1],second[2]),0xFFFFCB74,2);lines++;}return new Frame(pos,bounds,lines,output);
    }
    public static void register(){ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{FRAMES.clear();latest=null;});LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract();if(frame==null)FRAMES.remove(context.levelState());else FRAMES.put(context.levelState(),frame);latest=frame;});LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives().submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});}
    private DisplayLinkSelection(){}
}
