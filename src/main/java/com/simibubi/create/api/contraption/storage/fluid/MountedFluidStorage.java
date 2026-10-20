package com.simibubi.create.api.contraption.storage.fluid;

import com.mojang.serialization.Codec;
import com.simibubi.create.impl.contraption.storage.MountedStorageAccess;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

public abstract class MountedFluidStorage extends MountedStorageAccess<FluidVariant> {
    public static final Codec<MountedFluidStorage> CODEC = Codec.lazyInitialized(() -> MountedFluidStorageType.CODEC.dispatch(storage -> storage.type, type -> type.codec));
    public static final StreamCodec<RegistryFriendlyByteBuf, MountedFluidStorage> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public final MountedFluidStorageType<?> type;
    protected MountedFluidStorage(MountedFluidStorageType<?> type) { super(FluidVariant.blank()); this.type = Objects.requireNonNull(type); }
    public abstract void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be);
}
