package com.simibubi.create.content.fluids.tank.storage.creative;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class CreativeFluidTankMountedStorageType extends MountedFluidStorageType<CreativeFluidTankMountedStorage> {
    public CreativeFluidTankMountedStorageType() { super(CreativeFluidTankMountedStorage.CODEC); }
    @Override public @Nullable CreativeFluidTankMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        return be instanceof CreativeFluidTankBlockEntity tank && tank.isController() ? CreativeFluidTankMountedStorage.fromTank(tank) : null;
    }
}
