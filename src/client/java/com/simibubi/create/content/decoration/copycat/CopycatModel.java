package com.simibubi.create.content.decoration.copycat;

import com.simibubi.create.AllBlocks;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Real Fabric chunk geometry from immutable native material data and the actual current material model. */
public abstract class CopycatModel extends WrapperBlockStateModel {
    protected final SpriteFinder finder;
    protected CopycatModel(BlockStateModel wrapped,SpriteFinder finder){super(wrapped);this.finder=finder;}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        var state=bake.state();if(!state.is(AllBlocks.COPYCAT_PANEL)&&!state.is(AllBlocks.COPYCAT_STEP)&&!state.is(AllBlocks.COPYCAT_BARS))return model;var finder=bake.baker().materials().spriteFinder(net.minecraft.data.AtlasIds.BLOCKS);
        if(state.is(AllBlocks.COPYCAT_PANEL))return new CopycatPanelModel(model,finder);if(state.is(AllBlocks.COPYCAT_STEP))return new CopycatStepModel(model,finder);if(state.is(AllBlocks.COPYCAT_BARS))return new CopycatBarsModel(model,finder);return model;
    }));}
    public static BlockState getMaterial(BlockAndTintGetter level,BlockPos pos){return level.getBlockEntityRenderData(pos) instanceof CopycatBlockEntity.RenderData data?data.material():AllBlocks.COPYCAT_BASE.defaultBlockState();}
    public static BlockStateModel getModelOf(BlockState state){return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);}
    protected static BlockAndTintGetter filtered(BlockAndTintGetter level,BlockPos pos,BlockState state){return state.getBlock() instanceof CopycatBlock block?new FilteredBlockAndTintGetter(level,target->block.canConnectTexturesToward(level,pos,target,state)):level;}
    private static List<Integer> colors(BlockAndTintGetter level,BlockPos pos,BlockState material){return Minecraft.getInstance().getBlockColors().getTintSources(material).stream().map(tint->tint.colorInWorld(material,level,pos)).toList();}
    protected static int occluded(BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material){
        if(!(state.getBlock() instanceof CopycatBlock block))return 0;int mask=0;
        for(var face:Direction.values()){var target=pos.relative(face);var neighbor=level.getBlockState(target);boolean hidden=neighbor.getBlock() instanceof CopycatBlock other&&other.hidesNeighborFace(level,target,neighbor,state,face.getOpposite());if(hidden||block.canFaceBeOccluded(state,face)&&!Block.shouldRenderFace(material,neighbor,face))mask|=1<<face.ordinal();}
        return mask;
    }
    private record Key(CopycatModel owner,BlockState material,BlockStateModel materialModel,Object materialKey,int occluded,List<Integer> tints){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var material=getMaterial(level,pos);var view=filtered(level,pos,state);var model=getModelOf(material);var key=model.createGeometryKey(view,pos,material,random);return key==null?null:new Key(this,material,model,key,occluded(level,pos,state,material),colors(view,pos,material));}
    @Override public Material.Baked particleMaterial(BlockAndTintGetter level,BlockPos pos,BlockState state){var material=getMaterial(level,pos);return getModelOf(material).particleMaterial(filtered(level,pos,state),pos,material);}
    @Override public int materialFlags(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var material=getMaterial(level,pos);return getModelOf(material).materialFlags(filtered(level,pos,state),pos,material,random);}
    @Override public boolean hasMaterialFlag(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,int flag){return (materialFlags(level,pos,state,random)&flag)!=0;}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){var material=getMaterial(level,pos);renderMaterial(out,level,pos,state,material,random,occluded(level,pos,state,material));}
    protected void renderMaterial(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material,RandomSource random,int occluded){
        var view=filtered(level,pos,state);var tint=colors(view,pos,material);boolean emissive=material.emissiveRendering();var source=Renderer.get().mutableMesh();getModelOf(material).emitQuads(source.emitter(),view,pos,material,random,side->false);
        out.pushTransform(quad->{var face=quad.cullFace();if(face!=null&&(occluded&(1<<face.ordinal()))!=0)return false;int index=quad.tintIndex();if(index>=0&&index<tint.size()){int color=tint.get(index)|0xFF000000;for(int vertex=0;vertex<4;vertex++)quad.color(vertex,ARGB.multiply(quad.color(vertex),color));}quad.tintIndex(-1);quad.cullFace(null);if(emissive)quad.emissive(true);return true;});
        try{emitCropped(out,source.immutableCopy(),view,pos,state,material,random);}finally{out.popTransform();}
    }
    protected abstract void emitCropped(QuadEmitter out,Mesh source,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockState material,RandomSource random);
}
