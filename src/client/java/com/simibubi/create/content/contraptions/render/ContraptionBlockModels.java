package com.simibubi.create.content.contraptions.render;

import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Uses world model emission, including Fabric connected geometry, culling and native biome tints. */
public final class ContraptionBlockModels {
    private record Cached(BlockStateModel model,Object geometry,List<Integer> tints,int cullMask,Mesh mesh,boolean translucent){}
    private final Map<BlockPos,Cached> cache=new HashMap<>();
    public void clear(){cache.clear();}
    public PartialModelRenderState extract(VirtualRenderWorld level,BlockPos pos,BlockState state){
        var client=Minecraft.getInstance();var model=client.getModelManager().getBlockStateModelSet().get(state);long seed=state.getSeed(pos);
        var geometry=model.createGeometryKey(level,pos,state,RandomSource.create(seed));
        var tints=client.getBlockColors().getTintSources(state).stream().map(tint->tint.colorInWorld(state,level,pos)).toList();
        int cull=0;for(var side:Direction.values())if(!Block.shouldRenderFace(state,level.getBlockState(pos.relative(side)),side))cull|=1<<side.ordinal();
        var cached=cache.get(pos);
        if(geometry==null||cached==null||cached.model!=model||!Objects.equals(cached.geometry,geometry)||!cached.tints.equals(tints)||cached.cullMask!=cull){
            int mask=cull;var mesh=Renderer.get().mutableMesh();var out=mesh.emitter();
            out.pushTransform(quad->{int index=quad.tintIndex();if(index>=0&&index<tints.size()){int color=tints.get(index)|0xFF000000;for(int i=0;i<4;i++)quad.color(i,ARGB.multiply(quad.color(i),color));quad.tintIndex(-1);}return true;});
            model.emitQuads(out,level,pos,state,RandomSource.create(seed),side->side!=null&&(mask&(1<<side.ordinal()))!=0);out.popTransform();
            cached=new Cached(model,geometry,tints,cull,mesh.immutableCopy(),model.hasMaterialFlag(level,pos,state,RandomSource.create(seed),BakedQuad.FLAG_TRANSLUCENT));
            if(geometry!=null)cache.put(pos,cached);else cache.remove(pos);
        }
        var result=new PartialModelRenderState();result.mesh=cached.mesh;result.translucent=cached.translucent;return result;
    }
}
