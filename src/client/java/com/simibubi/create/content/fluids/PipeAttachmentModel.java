package com.simibubi.create.content.fluids;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;
import net.minecraft.core.Direction;

/** Chunk workers consume immutable server-supplied attachment choices; no capability lookup occurs during rendering. */
public final class PipeAttachmentModel extends WrapperBlockStateModel {
    private PipeAttachmentModel(BlockStateModel model){super(model);}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->{
        var block=bake.state().getBlock();return block instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock||block instanceof com.simibubi.create.content.fluids.pipes.IAxisPipe||block instanceof com.simibubi.create.content.fluids.pump.PumpBlock?new PipeAttachmentModel(model):model;
    }));}
    private record Key(PipeAttachmentModel model,Object wrapped,Object data){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,level.getBlockEntityRenderData(pos));}
    private void emit(BlockStateModel model,QuadEmitter out,RandomSource random,Predicate<Direction> cull){if(model==null)return;var parts=new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();model.collectParts(random,parts);for(var part:parts)part.emitQuads(out,cull);}
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        wrapped.emitQuads(out,level,pos,state,random,cull);
        if(!(level.getBlockEntityRenderData(pos) instanceof FluidTransportBehaviour.PipeRenderData data))return;
        var models=Minecraft.getInstance().getModelManager();
        if(data.casing())emit(models.getModel(AllPartialModels.FLUID_PIPE_CASING),out,random,cull);
        for(var side:Direction.values())for(var part:data.attachments().get(side.ordinal()).parts())emit(models.getModel(AllPartialModels.PIPE_ATTACHMENTS.get(part).get(side)),out,random,cull);
    }
}
