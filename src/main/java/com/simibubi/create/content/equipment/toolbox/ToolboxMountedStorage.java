package com.simibubi.create.content.equipment.toolbox;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolboxMountedStorage extends WrapperMountedItemStorage<ToolboxInventory> {
    public static final MapCodec<ToolboxMountedStorage> CODEC=ToolboxInventory.CODEC.xmap(ToolboxMountedStorage::new,storage->storage.wrapped).fieldOf("value");
    public ToolboxMountedStorage(ToolboxInventory inventory){super(AllMountedStorageTypes.TOOLBOX,inventory.copy());}
    @Override public void setStackInSlot(int slot,ItemStack stack){if(!isAvailable())return;boolean changed=!ItemStack.matches(wrapped.getStackInSlot(slot),stack);wrapped.setStackInSlot(slot,stack);if(changed)notifyDirectChange();}
    @Override public void unmount(Level level,BlockState state,BlockPos pos,BlockEntity owner){if(owner instanceof ToolboxBlockEntity box)box.readInventory(wrapped);}
    /** Original moving toolbox interaction explicitly refuses the generic mounted container UI. */
    @Override public net.minecraft.world.MenuProvider createMenuProvider(net.minecraft.network.chat.Component name,java.util.List<MountedItemStorage> storages,java.util.function.Predicate<net.minecraft.world.entity.player.Player> valid,java.util.function.Consumer<net.minecraft.world.entity.player.Player> close){return null;}
    public ToolboxInventory inventorySnapshot(){return wrapped.frozenCopy();}
    public static ToolboxMountedStorage fromToolbox(ToolboxBlockEntity toolbox){return new ToolboxMountedStorage(toolbox.inventory);}
    public static ToolboxMountedStorage fromLegacy(HolderLookup.Provider registries,CompoundTag tag){return new ToolboxMountedStorage(ToolboxInventory.fromLegacy(registries,tag));}
}
