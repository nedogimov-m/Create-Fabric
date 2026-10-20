package com.simibubi.create.api.contraption.storage.fluid;

import com.mojang.serialization.*;
import com.simibubi.create.api.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class MountedFluidStorageType<T extends MountedFluidStorage> {
    public static final Codec<MountedFluidStorageType<?>> CODEC = CreateBuiltInRegistries.MOUNTED_FLUID_STORAGE_TYPE.byNameCodec();
    public static final SimpleRegistry<Block, MountedFluidStorageType<?>> REGISTRY = SimpleRegistry.create();
    public final MapCodec<? extends T> codec;
    protected MountedFluidStorageType(MapCodec<? extends T> codec) { this.codec = codec; }
    public abstract @Nullable T mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be);
}
