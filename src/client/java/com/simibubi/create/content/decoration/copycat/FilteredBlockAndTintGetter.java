package com.simibubi.create.content.decoration.copycat;

import java.util.function.Predicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.*;

/** Pinned connectivity mask; native immutable render attachments are forwarded without reading live BEs. */
public final class FilteredBlockAndTintGetter implements BlockAndTintGetter {
    private final BlockAndTintGetter wrapped;private final Predicate<BlockPos> filter;
    public FilteredBlockAndTintGetter(BlockAndTintGetter wrapped,Predicate<BlockPos> filter){this.wrapped=wrapped;this.filter=filter;}
    @Override public BlockEntity getBlockEntity(BlockPos pos){return filter.test(pos)?wrapped.getBlockEntity(pos):null;}
    @Override public Object getBlockEntityRenderData(BlockPos pos){return filter.test(pos)?wrapped.getBlockEntityRenderData(pos):null;}
    @Override public BlockState getBlockState(BlockPos pos){return filter.test(pos)?wrapped.getBlockState(pos):Blocks.AIR.defaultBlockState();}
    @Override public FluidState getFluidState(BlockPos pos){return filter.test(pos)?wrapped.getFluidState(pos):Fluids.EMPTY.defaultFluidState();}
    @Override public int getHeight(){return wrapped.getHeight();}
    @Override public int getMinY(){return wrapped.getMinY();}
    @Override public LevelLightEngine getLightEngine(){return wrapped.getLightEngine();}
    @Override public int getBrightness(LightLayer layer,BlockPos pos){return wrapped.getBrightness(layer,pos);}
    @Override public int getRawBrightness(BlockPos pos,int darken){return wrapped.getRawBrightness(pos,darken);}
    @Override public CardinalLighting cardinalLighting(){return wrapped.cardinalLighting();}
    @Override public int getBlockTint(BlockPos pos,ColorResolver resolver){return wrapped.getBlockTint(pos,resolver);}
}
