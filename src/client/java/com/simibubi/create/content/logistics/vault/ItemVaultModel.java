package com.simibubi.create.content.logistics.vault;

import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
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

/** Source rectangle CT orientation, extracted from immutable chunk render data. */
public final class ItemVaultModel extends WrapperBlockStateModel {
    private final SpriteFinder finder;
    private final Map<TextureAtlasSprite,TextureAtlasSprite> medium,large;
    private ItemVaultModel(BlockStateModel wrapped,SpriteFinder finder,Map<TextureAtlasSprite,TextureAtlasSprite> medium,Map<TextureAtlasSprite,TextureAtlasSprite> large){super(wrapped);this.finder=finder;this.medium=Map.copyOf(medium);this.large=Map.copyOf(large);}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        if(!(bake.state().getBlock() instanceof ItemVaultBlock))return model;var materials=bake.baker().materials();var medium=new HashMap<TextureAtlasSprite,TextureAtlasSprite>();var large=new HashMap<TextureAtlasSprite,TextureAtlasSprite>();
        for(var face:new String[]{"front","side","top","bottom"}){var base=materials.get(new Material(Create.asResource("block/vault/vault_"+face+"_small")),()->"Vault texture").sprite();medium.put(base,materials.get(new Material(Create.asResource("block/vault/vault_"+face+"_medium")),()->"Vault connected texture").sprite());large.put(base,materials.get(new Material(Create.asResource("block/vault/vault_"+face+"_large")),()->"Large vault connected texture").sprite());}
        return new ItemVaultModel(model,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),medium,large);
    }));}
    private boolean connects(BlockAndTintGetter level,BlockPos pos,BlockState state,BlockPos other){return level.getBlockState(other)==state&&level.getBlockEntityRenderData(pos) instanceof ItemVaultBlockEntity.VaultRenderData a&&level.getBlockEntityRenderData(other) instanceof ItemVaultBlockEntity.VaultRenderData b&&a.controller().equals(b.controller());}
    private record Face(int tile,boolean culled){}
    private List<Face> context(BlockAndTintGetter level,BlockPos pos,BlockState state){
        var list=new ArrayList<Face>();var axis=state.getValue(ItemVaultBlock.HORIZONTAL_AXIS);boolean alongX=axis==Direction.Axis.X;
        for(var face:Direction.values()){
            Direction h=face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.WEST,v=face.getAxis().isHorizontal()?Direction.UP:Direction.NORTH;
            if(face.getAxis().isVertical()&&alongX){h=h.getClockWise();v=v.getClockWise();}else if(face.getAxis()!=axis&&!face.getAxis().isVertical()){v=Direction.get(alongX?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE,axis);h=Direction.get(face.getAxisDirection(),Direction.Axis.Y);}
            if(face.getAxisDirection()==Direction.AxisDirection.POSITIVE)h=h.getOpposite();if(face==Direction.DOWN){h=h.getOpposite();v=v.getOpposite();}
            boolean up=connects(level,pos,state,pos.relative(v)),down=connects(level,pos,state,pos.relative(v.getOpposite())),left=connects(level,pos,state,pos.relative(h.getOpposite())),right=connects(level,pos,state,pos.relative(h));int x=left&&right?2:left?3:right?1:0,y=up&&down?1:up?2:down?0:3;
            list.add(new Face(x+y*4,connects(level,pos,state,pos.relative(face))));
        }
        return List.copyOf(list);
    }
    private record Key(ItemVaultModel owner,Object wrapped,List<Face> faces,boolean large){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,context(level,pos,state),state.getValue(ItemVaultBlock.LARGE));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        var faces=context(level,pos,state);var shifts=state.getValue(ItemVaultBlock.LARGE)?large:medium;
        out.pushTransform(quad->{var data=faces.get(quad.lightFace().ordinal());if(data.culled)return false;var source=finder.find(quad);var target=shifts.get(source);if(target==null)return true;for(int i=0;i<4;i++){float u=(quad.u(i)-source.getU0())/(source.getU1()-source.getU0()),v=(quad.v(i)-source.getV0())/(source.getV1()-source.getV0());quad.uv(i,target.getU((u+data.tile%4)/4),target.getV((v+data.tile/4)/4));}return true;});wrapped.emitQuads(out,level,pos,state,random,cull);out.popTransform();
    }
}
