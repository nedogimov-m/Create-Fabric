package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.createmod.catnip.outliner.Outliner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Global source value boxes use actual native world extraction and detached native submission. */
public final class ValueBoxRenderHandler {
    public record Frame(List<ValueBox.State> boxes){
        public Frame{boxes=List.copyOf(boxes);}
        public boolean hasHighlightedBox(BlockPos pos){return boxes.stream().anyMatch(box->box.highlighted()&&box.position().equals(pos));}
    }
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest=new Frame(List.of());
    private static ClientLevel level;
    private static boolean registered;
    public static Frame latest(){return latest;}
    public static boolean hasHighlightedBox(BlockPos pos){return latest.hasHighlightedBox(pos);}
    private static void clear(){
        var outliner=Outliner.getInstance();
        for(var entry:List.copyOf(outliner.getOutlines().entrySet()))if(entry.getValue().getOutline() instanceof ValueBox)outliner.remove(entry.getKey());
        FRAMES.clear();latest=new Frame(List.of());
    }
    public static void tick(Minecraft client){
        if(level!=client.level){clear();level=client.level;}
        Outliner.getInstance().tickOutlines();
        ScrollValueRenderer.tick();FilteringRenderer.tick();
    }
    public static Frame extract(Minecraft client,Vec3 camera,float partial){
        if(client.level==null||client.level!=level||client.gui.screen()!=null||client.gui.hud.isHidden()||!ValueSettingsInputHandler.canInteract(client.player))return new Frame(List.of());
        var boxes=new ArrayList<ValueBox.State>();
        for(var entry:List.copyOf(Outliner.getInstance().getOutlines().values())){
            if(!(entry.getOutline() instanceof ValueBox box)||!box.isCurrent()
                ||!client.player.isWithinBlockInteractionRange(box.interactionOrigin(),1)
                ||!client.level.mayInteract(client.player,box.interactionOrigin())||!client.level.mayInteract(client.player,box.position()))continue;
            float alpha=1;
            if(entry.isFading()){
                float previous=1+(entry.getTicksTillRemoval()+1)/8f,current=1+entry.getTicksTillRemoval()/8f;
                alpha=Mth.lerp(partial,previous,current);alpha=alpha*alpha*alpha;if(alpha<1/8f)continue;
            }
            var state=box.extractRenderState(camera,partial,alpha);if(state!=null)boxes.add(state);
        }
        return new Frame(boxes);
    }
    public static void register(){
        if(registered)return;registered=true;
        ClientTickEvents.END_CLIENT_TICK.register(ValueBoxRenderHandler::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();level=null;});
        LevelExtractionEvents.END_EXTRACTION.register(context->{
            var client=Minecraft.getInstance();float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            var frame=extract(client,context.levelState().cameraRenderState.pos,partial);FRAMES.put(context.levelState(),frame);latest=frame;
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)for(var box:frame.boxes())box.submit(context.submitNodeCollector());});
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,outline)->{var frame=FRAMES.get(context.levelState());return frame==null||frame.boxes().stream().noneMatch(ValueBox.State::highlighted);});
    }
    private ValueBoxRenderHandler(){}
}
