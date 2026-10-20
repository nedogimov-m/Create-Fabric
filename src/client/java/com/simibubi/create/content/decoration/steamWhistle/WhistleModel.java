package com.simibubi.create.content.decoration.steamWhistle;

import com.simibubi.create.AllBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

/** An extension hides the base whistle's top rim, including side quads tagged with cullface=up. */
public final class WhistleModel extends WrapperBlockStateModel {
    private WhistleModel(BlockStateModel model){super(model);}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->bake.state().is(AllBlocks.STEAM_WHISTLE)?new WhistleModel(model):model));}
    private record Key(WhistleModel model,Object wrapped,boolean covered){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,level.getBlockState(pos.above()).is(AllBlocks.STEAM_WHISTLE_EXTENSION));}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cullTest){
        boolean covered=level.getBlockState(pos.above()).is(AllBlocks.STEAM_WHISTLE_EXTENSION);
        out.pushTransform(quad->!covered||quad.cullFace()!=Direction.UP);
        wrapped.emitQuads(out,level,pos,state,random,side->covered&&side==Direction.UP||cullTest.test(side));out.popTransform();
    }
}
