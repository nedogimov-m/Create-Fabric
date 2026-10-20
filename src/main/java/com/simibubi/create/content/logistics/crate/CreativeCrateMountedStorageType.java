package com.simibubi.create.content.logistics.crate;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class CreativeCrateMountedStorageType extends MountedItemStorageType<CreativeCrateMountedStorage> {
    public CreativeCrateMountedStorageType() { super(CreativeCrateMountedStorage.CODEC); }
    @Override public @Nullable CreativeCrateMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        return be instanceof CreativeCrateBlockEntity crate ? new CreativeCrateMountedStorage(crate.filtering.getFilter()) : null;
    }
}
