package com.simibubi.create.content.decoration.palettes;

import com.simibubi.create.*;
import com.simibubi.create.foundation.block.connected.OmnidirectionalCT;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Real native chunk-model UV shifting; geometry cache keys contain the immutable six-face context. */
public final class RailwayGlassCTModel extends WrapperBlockStateModel {
    private record Shift(TextureAtlasSprite original,TextureAtlasSprite connected){}
    private final SpriteFinder finder;
    private final List<Shift> shifts;
    private RailwayGlassCTModel(BlockStateModel model,SpriteFinder finder,List<Shift> shifts){super(model);this.finder=finder;this.shifts=List.copyOf(shifts);}
    public static void register(){
        FluidRenderingRegistry.setBlockTransparency(AllBlocks.FRAMED_GLASS,true);
        ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
            var state=bake.state();boolean casing=state.is(AllBlocks.RAILWAY_CASING);if(!casing&&!state.is(AllBlocks.FRAMED_GLASS))return model;
            var materials=bake.baker().materials();var shifts=new ArrayList<Shift>();
            for(var path:casing?List.of("railway_casing_side","railway_casing"):List.of("palettes/framed_glass")){
                var original=materials.get(new Material(Create.asResource("block/"+path)),()->"Create source casing/glass CT").sprite();var connected=materials.get(new Material(Create.asResource("block/"+path+"_connected")),()->"Create source casing/glass CT sheet").sprite();shifts.add(new Shift(original,connected));
            }
            return new RailwayGlassCTModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),shifts);
        }));
    }
    public static List<Integer> indices(BlockAndTintGetter level,BlockPos pos,BlockState state){return Arrays.stream(Direction.values()).map(face->OmnidirectionalCT.build(level,pos,state,face).textureIndex()).toList();}
    private record Key(RailwayGlassCTModel model,Object original,List<Integer> indices){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var original=wrapped.createGeometryKey(level,pos,state,random);return original==null?null:new Key(this,original,indices(level,pos,state));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        var indices=indices(level,pos,state);out.pushTransform(quad->{var source=finder.find(quad);Shift selected=null;for(var shift:shifts)if(shift.original==source){selected=shift;break;}if(selected==null)return true;var target=selected.connected;int tile=indices.get(quad.lightFace().ordinal());
            for(int vertex=0;vertex<4;vertex++){float u=(quad.u(vertex)-source.getU0())/(source.getU1()-source.getU0()),v=(quad.v(vertex)-source.getV0())/(source.getV1()-source.getV0());quad.uv(vertex,target.getU((u+tile%8)/8),target.getV((v+tile/8)/8));}return true;});
        try{wrapped.emitQuads(out,level,pos,state,random,cull);}finally{out.popTransform();}
    }
}
