package com.simibubi.create.api.contraption.storage.item;

import com.mojang.serialization.Codec;
import com.simibubi.create.impl.contraption.storage.MountedStorageAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/** Public Create mounted inventory API expressed in Fabric transactional slots. */
public abstract class MountedItemStorage extends MountedStorageAccess<ItemVariant> implements SlottedStorage<ItemVariant> {
    public static final Codec<MountedItemStorage> CODEC = Codec.lazyInitialized(() -> MountedItemStorageType.CODEC.dispatch(storage -> storage.type, type -> type.codec));
    public static final StreamCodec<RegistryFriendlyByteBuf, MountedItemStorage> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public final MountedItemStorageType<?> type;
    protected MountedItemStorage(MountedItemStorageType<?> type) { super(ItemVariant.blank()); this.type = Objects.requireNonNull(type); }
    @Override protected abstract SlottedStorage<ItemVariant> delegate();
    @Override public int getSlotCount() { return delegate().getSlotCount(); }
    @Override public SingleSlotStorage<ItemVariant> getSlot(int slot) { return guardSlot(delegate().getSlot(slot)); }
    public ItemStack getStackInSlot(int slot) { var view = getSlot(slot); return view.isResourceBlank() ? ItemStack.EMPTY : view.getResource().toStack((int)view.getAmount()); }
    public abstract void setStackInSlot(int slot, ItemStack stack);
    public java.util.List<MountedItemStorage> getMenuStorages(BlockPos pos, com.simibubi.create.content.contraptions.Contraption contraption) { return java.util.List.of(this); }
    public net.minecraft.world.@Nullable MenuProvider createMenuProvider(net.minecraft.network.chat.Component name, java.util.List<MountedItemStorage> storages, java.util.function.Predicate<net.minecraft.world.entity.player.Player> stillValid, java.util.function.Consumer<net.minecraft.world.entity.player.Player> onClose) {
        return com.simibubi.create.api.contraption.storage.item.menu.MountedStorageMenus.createGeneric(name, storages, stillValid, onClose);
    }
    public abstract void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be);
}
