package com.simibubi.create.content.logistics.itemHatch;

import com.simibubi.create.AllBlocks;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Original hatch cutout-mipped layer through the actual native Fabric model emitter. */
public final class ItemHatchModels extends WrapperBlockStateModel {
 private ItemHatchModels(BlockStateModel wrapped){super(wrapped);}
 public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->bake.state().is(AllBlocks.ITEM_HATCH)?new ItemHatchModels(model):model));}
 @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){out.pushTransform(quad->{quad.chunkLayer(ChunkSectionLayer.CUTOUT);return true;});try{wrapped.emitQuads(out,level,pos,state,random,cull);}finally{out.popTransform();}}
}
