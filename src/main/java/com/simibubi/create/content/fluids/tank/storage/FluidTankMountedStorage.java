package com.simibubi.create.content.fluids.tank.storage;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

public class FluidTankMountedStorage extends WrapperMountedFluidStorage<FluidTankMountedStorage.Handler> implements SyncedMountedStorage {
    private static final Codec<Long> NON_NEGATIVE = Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative mounted fluid amount"));
    private record FluidData(FluidVariant variant, long amount) {}
    private static final Codec<FluidData> FLUID = RecordCodecBuilder.create(i -> i.group(
        FluidVariant.CODEC.fieldOf("variant").forGetter(FluidData::variant), NON_NEGATIVE.fieldOf("amount").forGetter(FluidData::amount)
    ).apply(i, FluidData::new));
    private record Contents(long capacity, FluidData fluid) {}
    private static final Codec<Contents> CONTENTS = RecordCodecBuilder.create(i -> i.group(
        NON_NEGATIVE.fieldOf("capacity").forGetter(Contents::capacity), FLUID.fieldOf("fluid").forGetter(Contents::fluid)
    ).apply(i, Contents::new));
    public static final MapCodec<FluidTankMountedStorage> CODEC = CONTENTS.comapFlatMap(data -> {
        if (data.fluid.amount > data.capacity || data.fluid.variant.isBlank() && data.fluid.amount != 0) return DataResult.error(() -> "Invalid mounted tank contents");
        return DataResult.success(new FluidTankMountedStorage(data.capacity, new FluidStack(data.fluid.variant, data.fluid.amount)));
    }, storage -> new Contents(storage.getCapacity(), new FluidData(storage.getFluid().variant(), storage.getFluid().getAmount()))).fieldOf("value");
    private boolean dirty;
    public FluidTankMountedStorage(long capacity, FluidStack fluid) {
        super(AllMountedStorageTypes.FLUID_TANK, new Handler(capacity));
        wrapped.setFluid(fluid); wrapped.changed = () -> dirty = true;
    }
    public FluidStack getFluid() { return wrapped.getFluid(); }
    public long getCapacity() { return wrapped.getCapacity(); }
    @Override public boolean isDirty() { return dirty; }
    @Override public void markClean() { dirty = false; }
    @Override public void afterSync(com.simibubi.create.content.contraptions.Contraption contraption, BlockPos localPos) {
        if (contraption.getBlockEntityClientSide(localPos) instanceof FluidTankBlockEntity tank) {
            tank.getTankInventory().setCapacity(getCapacity()); tank.getTankInventory().setFluid(getFluid());
        }
    }
    public void writeBlockEntityData(CompoundTag data, HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries);
        wrapped.save(output); data.put("TankContent", output.buildResult());
    }
    @Override public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof FluidTankBlockEntity tank && tank.isController()) tank.getTankInventory().setFluid(getFluid());
    }
    public static FluidTankMountedStorage fromTank(FluidTankBlockEntity tank) { return new FluidTankMountedStorage(tank.getTankInventory().getCapacity(), tank.getTankInventory().getFluid()); }
    public static final class Handler extends SmartFluidTank {
        private Runnable changed = () -> {};
        private long version;
        private Handler(long capacity) { super(capacity, ignored -> {}); }
        @Override protected void onFinalCommit() { version++; changed.run(); }
        @Override public long getVersion() { return version; }
    }
}
