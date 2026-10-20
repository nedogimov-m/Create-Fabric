package com.simibubi.create.content.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import org.joml.*;

/** Actual source partials become native item quads during extraction, including native foil passes. */
public abstract class EquipmentItemRenderer implements SpecialModelRenderer<EquipmentItemRenderer.State> {
    public record Draw(ItemQuads quads,Matrix4f transform){}
    public static class State {
        public final List<Draw> draws=new ArrayList<>();
        public ItemDisplayContext display;
        public Vector3fc[] extents;
        public Vector3fc[] extraExtents=new Vector3fc[0];
    }
    private record Cached(BlockStateModel model,ItemQuads quads){}
    private static final Map<ExtraModelKey<BlockStateModel>,Cached> CACHE=new HashMap<>();
    protected final Map<String,ExtraModelKey<BlockStateModel>> models=new LinkedHashMap<>();
    private volatile ModelRenderProperties properties;
    protected void model(String name,String path){models.put(name,ExtraModelKey.create(()->"create:equipment/"+path));}
    protected abstract String modelPath(String name);
    protected abstract State extract(ItemStack stack,ItemDisplayContext display,float partial,float time);
    protected void draw(State state,String name,PoseStack pose){draw(state,models.get(name),pose);}
    public static void draw(State state,ExtraModelKey<BlockStateModel> key,PoseStack pose){
        var model=Minecraft.getInstance().getModelManager().getModel(key);if(model==null)throw new IllegalStateException("Unbaked equipment partial "+key);
        var cached=CACHE.get(key);if(cached==null||cached.model!=model){var parts=new ArrayList<BlockStateModelPart>();model.collectParts(RandomSource.create(42),parts);var quads=new ArrayList<BakedQuad>();for(var part:parts){for(var face:Direction.values())quads.addAll(part.getQuads(face));quads.addAll(part.getQuads(null));}cached=new Cached(model,ItemQuads.split(List.copyOf(quads)));CACHE.put(key,cached);}
        pose.pushPose();pose.translate(-.5,-.5,-.5);state.draws.add(new Draw(cached.quads,new Matrix4f(pose.last().pose())));pose.popPose();
    }
    public Matrix4f cameraTransform(ItemDisplayContext context){var state=properties;if(state==null)throw new IllegalStateException("Actual equipment model properties not baked");var pose=new PoseStack();state.transforms().getTransform(context).apply(context.leftHand(),pose.last());return new Matrix4f(pose.last().pose());}
    private static void finish(State state){
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY),point=new Vector3f();
        for(var draw:state.draws)for(var quad:draw.quads.all())for(int i=0;i<4;i++){draw.transform.transformPosition(quad.position(i),point);point.add(.5f,.5f,.5f);min.min(point);max.max(point);}
        for(var extent:state.extraExtents){point.set(extent).add(.5f,.5f,.5f);min.min(point);max.max(point);}
        if(!Float.isFinite(min.x)){min.set(0);max.set(1);}state.extents=new Vector3fc[]{new Vector3f(min.x,min.y,min.z),new Vector3f(max.x,min.y,min.z),new Vector3f(min.x,max.y,min.z),new Vector3f(min.x,min.y,max.z),new Vector3f(max.x,max.y,min.z),new Vector3f(max.x,min.y,max.z),new Vector3f(min.x,max.y,max.z),new Vector3f(max.x,max.y,max.z)};
    }
    public void register(String id){ModelLoadingPlugin.register(context->{
        models.forEach((name,key)->context.addModel(key,SimpleUnbakedExtraModel.blockStateModel(Create.asResource(modelPath(name)))));
        context.modifyItemModelAfterBake().register((original,baked)->{
            if(!baked.itemId().equals(Create.asResource(id)))return original;
            var resolved=baked.bakingContext().blockModelBaker().getModel(Create.asResource("item/"+id));
            var renderProperties=ModelRenderProperties.fromResolvedModel(baked.bakingContext().blockModelBaker(),resolved,resolved.getTopTextureSlots());properties=renderProperties;var transform=new Matrix4f(baked.transformation());var modelIdentity=new Object();
            return (renderState,stack,resolver,display,level,owner,seed)->{
                var client=Minecraft.getInstance();float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false),time=client.level==null?partial:client.level.getGameTime()+partial;
                var state=extract(stack,display,partial,time);state.display=display;finish(state);renderState.appendModelIdentityElement(modelIdentity);renderState.appendModelIdentityElement(state);if(stack.hasFoil())renderState.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);renderState.setAnimated();var layer=renderState.newLayer();renderProperties.applyToLayer(layer,display);layer.setLocalTransform(transform);layer.setFoilType(stack.hasFoil()?ItemStackRenderState.FoilType.STANDARD:ItemStackRenderState.FoilType.NONE);layer.setupSpecialModel(this,state);layer.setExtents(()->state.extents);
            };
        });
    });}
    @Override public State extractArgument(ItemStack stack){var state=extract(stack,ItemDisplayContext.NONE,1,0);state.display=ItemDisplayContext.NONE;finish(state);return state;}
    @Override public void getExtents(Consumer<Vector3fc> output){output.accept(new Vector3f(0,0,-3.5f));output.accept(new Vector3f(1,1,1));}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline){if(state==null)return;pose.pushPose();pose.translate(.5,.5,.5);for(var draw:state.draws){pose.pushPose();pose.mulPose(draw.transform);collector.submitItem(pose,state.display,light,overlay,outline,new int[0],draw.quads,foil?ItemStackRenderState.FoilType.STANDARD:ItemStackRenderState.FoilType.NONE);pose.popPose();}pose.popPose();}
}
