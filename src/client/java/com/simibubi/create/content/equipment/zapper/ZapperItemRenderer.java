package com.simibubi.create.content.equipment.zapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.*;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import org.joml.Matrix4f;

public abstract class ZapperItemRenderer extends EquipmentItemRenderer {
    public record Emission(Mesh mesh,int light){}
    public static class ZapperState extends State {public final Map<Draw,Emission> emission=new IdentityHashMap<>(); public ItemStackRenderState selectedItem;public Matrix4f selectedTransform;}
    protected void renderBlockUsed(ItemStack stack,ZapperState state){var selected=stack.get(AllDataComponents.SHAPER_BLOCK_USED);if(selected==null)return;if(selected.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock){
        var client=Minecraft.getInstance();state.selectedItem=new ItemStackRenderState();client.getItemModelResolver().updateForTopItem(state.selectedItem,new ItemStack(selected.getBlock()),ItemDisplayContext.NONE,client.level,client.player,42);var pose=new PoseStack();pose.translate(-.3,-.45,0);pose.scale(.25f,.25f,.25f);state.selectedTransform=new Matrix4f(pose.last().pose());var extents=new ArrayList<org.joml.Vector3fc>();state.selectedItem.visitExtents(point->extents.add(state.selectedTransform.transformPosition(point,new org.joml.Vector3f())));state.extraExtents=extents.toArray(org.joml.Vector3fc[]::new);return;
    }var model=Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(selected);var parts=new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();model.collectParts(RandomSource.create(42),parts);var quads=new ArrayList<BakedQuad>();for(var part:parts){quads.addAll(part.getQuads(null));for(var face:Direction.values())quads.addAll(part.getQuads(face));}var pose=new PoseStack();pose.translate(-.3,-.45,0);pose.scale(.25f,.25f,.25f);pose.translate(-.5,-.5,-.5);state.draws.add(new Draw(ItemQuads.split(List.copyOf(quads)),new Matrix4f(pose.last().pose())));}
    protected float getAnimationProgress(float partial,boolean left,boolean main){return net.minecraft.util.Mth.clamp(EquipmentClient.ZAPPER.getAnimation(main^left,partial)*5,0,1);}
    protected void glowing(ZapperState state,String model,PoseStack pose,int light,boolean translucent){draw(state,model,pose);var draw=state.draws.getLast();var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();for(var quad:draw.quads().all()){emitter.fromBakedQuad(quad).emissive(true).itemRenderType(translucent?RenderTypes.itemTranslucent(TextureAtlas.LOCATION_BLOCKS):RenderTypes.itemCutout(TextureAtlas.LOCATION_BLOCKS)).emit();}state.emission.put(draw,new Emission(mesh.immutableCopy(),light));}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline){if(!(state instanceof ZapperState zapper)){super.submit(state,pose,collector,light,overlay,foil,outline);return;}pose.pushPose();pose.translate(.5,.5,.5);if(zapper.selectedItem!=null){pose.pushPose();pose.mulPose(zapper.selectedTransform);zapper.selectedItem.submit(pose,collector,light,overlay,outline);pose.popPose();}for(var draw:state.draws){pose.pushPose();pose.mulPose(draw.transform());var effect=zapper.emission.get(draw);if(effect==null)collector.submitItem(pose,state.display,light,overlay,outline,new int[0],draw.quads(),foil?ItemStackRenderState.FoilType.STANDARD:ItemStackRenderState.FoilType.NONE);else collector.submitItem(pose,state.display,effect.light,overlay,outline,new int[0],ItemQuads.EMPTY,effect.mesh,foil?ItemStackRenderState.FoilType.STANDARD:ItemStackRenderState.FoilType.NONE);pose.popPose();}pose.popPose();}
}
