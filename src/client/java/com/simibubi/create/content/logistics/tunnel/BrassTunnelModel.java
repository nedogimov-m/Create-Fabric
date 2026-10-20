package com.simibubi.create.content.logistics.tunnel;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

/** Connected top material from immutable Fabric render data, safe on chunk workers. */
public final class BrassTunnelModel extends WrapperBlockStateModel {
    private final SpriteFinder finder;
    private final TextureAtlasSprite original,connected;
    private BrassTunnelModel(BlockStateModel model,SpriteFinder finder,TextureAtlasSprite original,TextureAtlasSprite connected){super(model);this.finder=finder;this.original=original;this.connected=connected;}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        if(!bake.state().is(AllBlocks.BRASS_TUNNEL))return model;var materials=bake.baker().materials();return new BrassTunnelModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),materials.get(new Material(Create.asResource("block/tunnel/brass_tunnel_top")),()->"Brass tunnel top").sprite(),materials.get(new Material(Create.asResource("block/tunnel/brass_tunnel_top_connected")),()->"Connected brass tunnel top").sprite());
    }));}
    private record Key(BrassTunnelModel model,Object wrapped,BrassTunnelBlockEntity.RenderData data){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,level.getBlockEntityRenderData(pos) instanceof BrassTunnelBlockEntity.RenderData data?data:null);}
    @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        var data=level.getBlockEntityRenderData(pos) instanceof BrassTunnelBlockEntity.RenderData render?render:null;
        if(data==null||!data.distributing()){wrapped.emitQuads(emitter,level,pos,state,random,cull);return;}
        int tile=(data.left()?1:0)+(data.right()?2:0);
        emitter.pushTransform(quad->{
            if(quad.lightFace()!=Direction.UP||finder.find(quad)!=original)return true;
            for(int vertex=0;vertex<4;vertex++){float u=(quad.u(vertex)-original.getU0())/(original.getU1()-original.getU0()),v=(quad.v(vertex)-original.getV0())/(original.getV1()-original.getV0());quad.uv(vertex,connected.getU((u+tile%2)/2),connected.getV((v+tile/2)/2));}return true;
        });wrapped.emitQuads(emitter,level,pos,state,random,cull);emitter.popTransform();
    }
}
