package com.simibubi.create.content.fluids.tank.storage.creative;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class CreativeFluidTankMountedStorage extends WrapperMountedFluidStorage<CreativeSmartFluidTank> {
    public static final MapCodec<CreativeFluidTankMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative mounted capacity")).fieldOf("capacity").forGetter(storage -> storage.wrapped.getCapacity()),
        FluidVariant.CODEC.fieldOf("variant").forGetter(storage -> storage.wrapped.getResource())
    ).apply(i, CreativeFluidTankMountedStorage::new));
    public CreativeFluidTankMountedStorage(long capacity, FluidVariant fluid) {
        super(AllMountedStorageTypes.CREATIVE_FLUID_TANK, new CreativeSmartFluidTank(capacity, ignored -> {}));
        wrapped.setContainedFluid(new FluidStack(fluid, capacity));
    }
    @Override public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) { /* Supply is immutable while mounted. */ }
    public static CreativeFluidTankMountedStorage fromTank(CreativeFluidTankBlockEntity tank) { return new CreativeFluidTankMountedStorage(tank.getTankInventory().getCapacity(), tank.getTankInventory().getResource()); }
}
