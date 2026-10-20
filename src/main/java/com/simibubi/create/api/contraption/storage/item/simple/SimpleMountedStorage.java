package com.simibubi.create.api.contraption.storage.item.simple;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.*;
import com.simibubi.create.foundation.item.MountedItemInventory;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Independent contents; mounting never retains the removed block entity's inventory. */
public class SimpleMountedStorage extends WrapperMountedItemStorage<MountedItemInventory> {
  public static final MapCodec<SimpleMountedStorage> CODEC = codec(SimpleMountedStorage::new);

  public SimpleMountedStorage(MountedItemStorageType<?> type, MountedItemInventory inventory) {
    super(type, inventory);
  }

  public SimpleMountedStorage(MountedItemInventory inventory) {
    this(AllMountedStorageTypes.SIMPLE, inventory);
  }

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    if (!isAvailable()) return;
    boolean changed = !ItemStack.matches(wrapped.getStack(slot), stack);
    wrapped.setStack(slot, stack);
    if (changed) notifyDirectChange();
  }

  @Override
  public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
    if (be instanceof Container container
        && container.getContainerSize() == wrapped.getSlotCount()) {
      for (int slot = 0; slot < wrapped.getSlotCount(); slot++)
        container.setItem(slot, wrapped.getStack(slot));
      container.setChanged();
    }
  }

  public static <T extends SimpleMountedStorage> MapCodec<T> codec(
      Function<MountedItemInventory, T> factory) {
    return MountedItemInventory.CODEC.xmap(factory, storage -> storage.wrapped).fieldOf("value");
  }
}
