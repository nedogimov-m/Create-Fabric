package com.simibubi.create.api.contraption.storage.item.chest;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.*;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.List;

/** Each chest half owns exactly its own slots; only interaction combines the halves. */
public class ChestMountedStorage extends SimpleMountedStorage {
    public static final MapCodec<ChestMountedStorage> CODEC = codec(ChestMountedStorage::new);
    public ChestMountedStorage(MountedItemInventory inventory) { super(AllMountedStorageTypes.CHEST, inventory); }
    @Override public List<MountedItemStorage> getMenuStorages(BlockPos pos, Contraption contraption) {
        var info = contraption.getBlocks().get(pos);
        if (info == null || !(info.state().getBlock() instanceof ChestBlock)) return List.of(this);
        var state = info.state(); var chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) return List.of(this);
        var otherPos = pos.relative(ChestBlock.getConnectedDirection(state)); var other = contraption.getBlocks().get(otherPos);
        if (other == null || other.state().getBlock() != state.getBlock() || other.state().getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING) || other.state().getValue(ChestBlock.TYPE) != chestType.getOpposite()) return List.of(this);
        var storage = contraption.getStorage().getAllItemStorages().get(otherPos);
        return storage instanceof ChestMountedStorage ? chestType == ChestType.RIGHT ? List.of(this, storage) : List.of(storage, this) : List.of(this);
    }
}
