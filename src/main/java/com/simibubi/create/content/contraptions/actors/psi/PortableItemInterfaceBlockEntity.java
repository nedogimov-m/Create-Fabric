package com.simibubi.create.content.contraptions.actors.psi;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.Contraption;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PortableItemInterfaceBlockEntity extends PortableStorageInterfaceBlockEntity {
    private Storage<ItemVariant> storage = Storage.empty();
    public PortableItemInterfaceBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.PORTABLE_STORAGE_INTERFACE, pos, state); }
    public PortableItemInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Storage<ItemVariant> storage() { return storage; }
    @Override protected void connectStorage(Contraption contraption, long generation) {
        storage = new InterfaceStorage<>(this, contraption.entity, generation, contraption.getStorage().getAllItems(), ItemVariant.blank());
    }
    @Override protected void clearStorage() { storage = Storage.empty(); }
}
