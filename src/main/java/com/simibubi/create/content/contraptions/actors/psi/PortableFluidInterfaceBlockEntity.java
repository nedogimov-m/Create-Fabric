package com.simibubi.create.content.contraptions.actors.psi;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.Contraption;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PortableFluidInterfaceBlockEntity extends PortableStorageInterfaceBlockEntity {
    private Storage<FluidVariant> storage = Storage.empty();
    public PortableFluidInterfaceBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.PORTABLE_FLUID_INTERFACE, pos, state); }
    public PortableFluidInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Storage<FluidVariant> storage() { return storage; }
    @Override protected void connectStorage(Contraption contraption, long generation) {
        storage = new InterfaceStorage<>(this, contraption.entity, generation, contraption.getStorage().getFluids(), FluidVariant.blank());
    }
    @Override protected void clearStorage() { storage = Storage.empty(); }
}
