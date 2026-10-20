package com.simibubi.create.content.fluids.tank.storage;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class FluidTankMountedStorageType extends MountedFluidStorageType<FluidTankMountedStorage> {
    public FluidTankMountedStorageType() { super(FluidTankMountedStorage.CODEC); }
    @Override public @Nullable FluidTankMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        return be instanceof FluidTankBlockEntity tank && tank.isController() ? FluidTankMountedStorage.fromTank(tank) : null;
    }
}
