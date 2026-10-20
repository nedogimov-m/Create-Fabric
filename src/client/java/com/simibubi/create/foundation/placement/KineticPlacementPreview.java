package com.simibubi.create.foundation.placement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.mixin.client.BlockItemPlacementPreviewInvoker;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native nonloading assisted-placement prediction, extracted immutable translucent model and submission. */
public final class KineticPlacementPreview {
    public enum Helper { COG_PRIMARY, SHAFT }
    public record Prediction(BlockPos source,BlockPos target,BlockState state,InteractionHand hand,Helper helper) {
        public Prediction { source=source.immutable();target=target.immutable(); }
    }
    public record Frame(Prediction prediction,Mesh mesh,float alpha,int animationTicks) {
        /** Submission consumes only this extracted snapshot and the actual extracted camera. */
        public void submit(SubmitNodeCollector collector,Vec3 camera){
            var target=prediction.target();var pose=new PoseStack();
            pose.translate(target.getX()-camera.x,target.getY()-camera.y,target.getZ()-camera.z);
            pose.translate(.5,.5,.5);pose.scale(.85f,.85f,.85f);pose.translate(-.5,-.5,-.5);
            var model=new PartialModelRenderState();model.mesh=mesh;model.translucent=true;
            model.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
        }
    }
    private static final Map<LevelRenderState,Frame> FRAMES=Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile @Nullable Frame latest;
    private static int animationTicks;
    private static boolean registered;
    public static @Nullable Frame latest(){return latest;}

    /** Reads only immediately loaded native chunks; no use/place/action callbacks or world mutation. */
    public static @Nullable Prediction predict(Minecraft client){
        var level=client.level;var player=client.player;
        if(level==null||player==null||client.gui.screen()!=null||client.gui.hud.isHidden()||player.isShiftKeyDown()
            ||player.isSpectator()||!player.mayBuild()||!(client.hitResult instanceof BlockHitResult hit)
            ||hit.getType()!=HitResult.Type.BLOCK||!LoadedLevelAccess.isLoaded(level,hit.getBlockPos())
            ||!level.mayInteract(player,hit.getBlockPos()))return null;
        var source=hit.getBlockPos();var clicked=LoadedLevelAccess.blockState(level,source);
        for(var hand:InteractionHand.values()){
            // Native input completes MAIN block use and ordinary item use before it can try OFF.
            // Only these known empty-MAIN pipelines pass without an action; never probe them by calling use.
            if(hand==InteractionHand.OFF_HAND&&!emptyMainPassesToOffhand(client,clicked))return null;
            var stack=player.getItemInHand(hand);
            if(stack.isEmpty()||!(stack.getItem() instanceof BlockItem item)||!(item.getBlock() instanceof ShaftBlock))continue;
            CogwheelBlockItem.Placement offset;Helper helper;
            if(item instanceof CogwheelBlockItem&&clicked.getBlock() instanceof ICogWheel){
                offset=CogwheelBlockItem.primaryOffset(((CogWheelBlock)item.getBlock()).isLargeCog(),level,clicked,source,hit);helper=Helper.COG_PRIMARY;
                // Source Catnip's integrated ghost retry could disagree with the actual primary item action.
                // Preserve the actual action order: a failed primary never displays that misleading fallback.
            }else{
                offset=CogwheelBlockItem.shaftOffset(player,level,clicked,source,hit);helper=Helper.SHAFT;
            }
            if(offset==null)continue;
            var target=offset.pos();
            if(!knownNeighborhood(client,target)||!level.mayInteract(player,target)
                ||!player.mayUseItemAt(target,hit.getDirection(),stack)||!item.getBlock().isEnabled(level.enabledFeatures()))return null;
            var context=CogwheelBlockItem.assistedContext(new UseOnContext(player,hand,hit),offset);
            if(!context.getClickedPos().equals(target)||!context.canPlace())return null;
            context=item.updatePlacementContext(context);
            if(context==null||!context.getClickedPos().equals(target))return null;
            var state=((BlockItemPlacementPreviewInvoker)item).create$kineticPreviewPlacementState(context);
            if(state==null)return null;
            // Native BlockItem applies this pure component after placing its validated state.
            state=stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).apply(state);
            return new Prediction(source,target,state,hand,helper);
        }
        return null;
    }
    private static boolean emptyMainPassesToOffhand(Minecraft client,BlockState clicked){
        if(!client.player.getMainHandItem().isEmpty())return false;
        return clicked.is(AllBlocks.SHAFT)||clicked.is(AllBlocks.POWERED_SHAFT)
            ||clicked.getBlock() instanceof CogWheelBlock
            ||clicked.getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
    }
    private static boolean knownNeighborhood(Minecraft client,BlockPos target){
        var level=client.level;
        if(!level.isInValidBounds(target)||!level.getWorldBorder().isWithinBounds(target))return false;
        // Native placement/survival/powered-shaft checks are bounded by two blocks around the target.
        for(int x=-2;x<=2;x+=2)for(int z=-2;z<=2;z+=2)
            if(!LoadedLevelAccess.isLoaded(level,target.offset(x,0,z)))return false;
        return true;
    }
    private static void tick(Minecraft client){
        if(predict(client)==null){animationTicks=Math.max(0,animationTicks-2);latest=null;}
        else animationTicks=Math.min(10,animationTicks+1);
    }
    private static @Nullable Frame extract(Minecraft client){
        var prediction=predict(client);if(prediction==null)return null;
        float breathing=.55f-.2f*(float)Math.cos(2*Math.PI*(System.currentTimeMillis()%2500)/2500d);
        float alpha=breathing*.75f*Math.min(animationTicks/10f,1f);
        if(alpha<=0)return null;
        var source=new PartialModelRenderState();AllPartialModels.extract(prediction.state(),source);
        if(source.mesh==null||source.mesh.size()==0)return null;
        var mutable=Renderer.get().mutableMesh();var out=mutable.emitter();int tint=Math.clamp(Math.round(alpha*255),0,255)<<24|0xFFFFFF;
        source.mesh.forEach(quad->{
            out.copyFrom(quad).chunkLayer(ChunkSectionLayer.TRANSLUCENT).cullFace(null).emissive(true).tintIndex(-1);
            for(int vertex=0;vertex<4;vertex++)out.color(vertex,ARGB.multiply(quad.color(vertex),tint));
            out.emit();
        });
        return new Frame(prediction,mutable.immutableCopy(),alpha,animationTicks);
    }
    private static void clear(){FRAMES.clear();latest=null;animationTicks=0;}
    public static void register(){
        if(registered)return;registered=true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client,level)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(KineticPlacementPreview::tick);
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract(Minecraft.getInstance());latest=frame;if(frame==null)FRAMES.remove(context.levelState());else FRAMES.put(context.levelState(),frame);});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.submit(context.submitNodeCollector(),context.levelState().cameraRenderState.pos);});
    }
    private KineticPlacementPreview(){}
}
