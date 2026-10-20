package com.simibubi.create.content.logistics.tableCloth;

import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Corner pieces are visible only when both bordering sides are visible. */
public final class TableClothModel extends WrapperBlockStateModel {
    private TableClothModel(BlockStateModel wrapped){super(wrapped);}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->bake.state().getBlock() instanceof TableClothBlock?new TableClothModel(model):model));}
    private static int visible(BlockAndTintGetter level,BlockPos pos,BlockState state){int mask=0;for(var side:Direction.Plane.HORIZONTAL)if(Block.shouldRenderFace(state,level.getBlockState(pos.relative(side)),side))mask|=1<<side.ordinal();return mask;}
    private record Key(TableClothModel model,Object original,int sides){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var original=wrapped.createGeometryKey(level,pos,state,random);return original==null?null:new Key(this,original,visible(level,pos,state));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        int mask=visible(level,pos,state);out.pushTransform(quad->{if(quad.cullFace()!=null)return true;float x=0,z=0;for(int i=0;i<4;i++){x+=quad.x(i)/4;z+=quad.z(i)/4;}if(x>=0&&x<=1||z>=0&&z<=1)return true;var a=x<0?Direction.WEST:Direction.EAST;var b=z<0?Direction.NORTH:Direction.SOUTH;return (mask&(1<<a.ordinal()))!=0&&(mask&(1<<b.ordinal()))!=0;});wrapped.emitQuads(out,level,pos,state,random,cull);out.popTransform();
    }
}
