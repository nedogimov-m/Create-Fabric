package net.createmod.catnip.outliner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Native world counterpart of the complete singleton geometry contract; ValueBox and local Ponder keep their own renderers. */
public final class OutlinerRenderHandler {
    /** Fully resolved native model; no ItemStack, live level or block entity is retained by submission. */
    public record Item(Vec3 position,float scale,ItemStackRenderState state) {}
    public record Frame(Vec3 camera,List<Outline.Geometry> outlines,List<Item> items){
        public Frame{outlines=List.copyOf(outlines);items=List.copyOf(items);}
        public void submit(SubmitNodeCollector collector){
            var pose=new PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);
            for(var geometry:outlines)OutlineRenderer.submit(pose,collector,geometry);
            for(var item:items){pose.pushPose();try{pose.translate(item.position);pose.scale(item.scale,item.scale,item.scale);item.state.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);}finally{pose.popPose();}}
        }
    }
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest=new Frame(Vec3.ZERO,List.of(),List.of());
    private static ClientLevel level;
    private static boolean registered;
    public static Frame latest(){return latest;}
    private static boolean supported(Outline outline){return !(outline instanceof ValueBox);}
    private static void clear(){var outliner=Outliner.getInstance();for(var entry:List.copyOf(outliner.getOutlines().entrySet()))if(supported(entry.getValue().getOutline()))outliner.remove(entry.getKey());FRAMES.clear();latest=new Frame(Vec3.ZERO,List.of(),List.of());}
    public static void register(){
        if(registered)return;registered=true;
        // ValueBoxRenderHandler remains the single global tickOutlines owner; no double fade/tick.
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(level!=client.level){clear();level=client.level;}});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();level=null;});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var client=Minecraft.getInstance();var camera=context.levelState().cameraRenderState.pos;float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);latest=extract(client,camera,partial);FRAMES.put(context.levelState(),latest);});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.submit(context.submitNodeCollector());});
    }
    private static Frame extract(Minecraft client,Vec3 camera,float partial){
        if(client.level==null||client.level!=level)return new Frame(camera,List.of(),List.of());
        var outlines=new ArrayList<Outline.Geometry>();var items=new ArrayList<Item>();
        for(var geometry:Outliner.getInstance().extract(OutlinerRenderHandler::supported,camera,partial)){
            // Source ItemOutline's fading scale and the exact local Ponder native FIXED item convention.
            for(var item:geometry.items()){
                var state=new ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(state,item.stack(),ItemDisplayContext.FIXED,client.level,null,0);
                if(!state.isEmpty())items.add(new Item(item.position(),item.scale(),state));
            }
            // Retain only detached geometry; source stack copies never escape into a render frame.
            outlines.add(new Outline.Geometry(geometry.segments(),geometry.faces(),List.of(),geometry.color(),geometry.width(),geometry.light(),geometry.disableNormals()));
        }
        return new Frame(camera,outlines,items);
    }
    private OutlinerRenderHandler(){}
}
