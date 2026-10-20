package com.simibubi.create.content.contraptions;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.*;
import com.simibubi.create.api.contraption.storage.item.*;
import com.simibubi.create.content.fluids.tank.storage.FluidTankMountedStorage;
import com.simibubi.create.foundation.item.MountedItemInventory;
import com.simibubi.create.impl.contraption.storage.MountedStorageAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jspecify.annotations.Nullable;

/** Separate mutable ownership, source per-block lookup, exposure/fuel rules and storage NBT. */
public class MountedStorageManager {
    private final Map<BlockPos, MountedItemStorage> itemBuilder = new LinkedHashMap<>();
    private final Map<BlockPos, MountedFluidStorage> fluidBuilder = new LinkedHashMap<>();
    private Map<BlockPos, MountedItemStorage> allItemStorages;
    private MountedItemStorageWrapper items, fuelItems;
    private MountedFluidStorageWrapper fluids;
    private final List<Storage<ItemVariant>> external = new ArrayList<>();
    private Storage<ItemVariant> allItems;
    private Set<BlockPos> interactablePositions = Set.of();
    private boolean owned;
    private long generation;

    public void reset() {
        MountedItemInventory.requireNoTransaction(); generation++; owned = false;
        itemBuilder.clear(); fluidBuilder.clear(); external.clear(); allItemStorages = null;
        items = fuelItems = null; fluids = null; allItems = null; interactablePositions = Set.of();
    }
    public void setOwned(boolean owned) { MountedItemInventory.requireNoTransaction(); this.owned = owned; }
    public boolean ownsContents() { return owned; }
    private void bind(MountedStorageAccess<?> storage) {
        long epoch = generation; storage.bindOwner(() -> owned && generation == epoch);
    }
    public void addStorage(MountedItemStorage storage, BlockPos pos) {
        if (allItemStorages != null) throw new IllegalStateException("Mounted storages already initialized");
        var previous = itemBuilder.put(pos.immutable(), storage); if (previous != null) previous.bindOwner(() -> false); bind(storage);
    }
    public void addStorage(MountedFluidStorage storage, BlockPos pos) {
        if (allItemStorages != null) throw new IllegalStateException("Mounted storages already initialized");
        var previous = fluidBuilder.put(pos.immutable(), storage); if (previous != null) previous.bindOwner(() -> false); bind(storage);
    }
    public void addBlock(Level level, BlockState state, BlockPos globalPos, BlockPos localPos, @Nullable BlockEntity be) {
        var itemType = MountedItemStorageType.REGISTRY.get(state.getBlock());
        if (itemType != null) { var storage = itemType.mount(level, state, globalPos, be); if (storage != null) addStorage(storage, localPos); }
        var fluidType = MountedFluidStorageType.REGISTRY.get(state.getBlock());
        if (fluidType != null) { var storage = fluidType.mount(level, state, globalPos, be); if (storage != null) addStorage(storage, localPos); }
    }
    public void initialize() {
        if (allItemStorages != null) return;
        allItemStorages = Collections.unmodifiableMap(new LinkedHashMap<>(itemBuilder));
        var exposed = new LinkedHashMap<BlockPos, MountedItemStorage>(); var fuel = new LinkedHashMap<BlockPos, MountedItemStorage>();
        allItemStorages.forEach((pos, storage) -> { if (!storage.type.is(AllMountedStorageTypes.INTERNAL)) { exposed.put(pos, storage); if (!storage.type.is(AllMountedStorageTypes.FUEL_BLACKLIST)) fuel.put(pos, storage); } });
        items = new MountedItemStorageWrapper(exposed); fuelItems = fuel.isEmpty() ? null : new MountedItemStorageWrapper(fuel);
        fluids = new MountedFluidStorageWrapper(fluidBuilder); allItems = items;
    }
    private void assertInitialized() { if (allItemStorages == null) throw new IllegalStateException("MountedStorageManager is uninitialized"); }
    public Map<BlockPos, MountedItemStorage> getAllItemStorages() { assertInitialized(); return allItemStorages; }
    public MountedItemStorageWrapper getMountedItems() { assertInitialized(); return items; }
    public @Nullable MountedItemStorageWrapper getFuelItems() { assertInitialized(); return fuelItems; }
    public MountedFluidStorageWrapper getFluids() { assertInitialized(); return fluids; }
    public Storage<ItemVariant> getAllItems() { assertInitialized(); return allItems; }
    public Set<BlockPos> getInteractablePositions() { return interactablePositions; }
    /** The entity/controller supplies the live position/distance predicate when handling a validated click. */
    public net.minecraft.world.@Nullable MenuProvider createMenuProvider(Contraption contraption, BlockPos localPos, java.util.function.Predicate<net.minecraft.world.entity.player.Player> stillValid, java.util.function.Consumer<net.minecraft.world.entity.player.Player> onClose) {
        assertInitialized(); var storage = allItemStorages.get(localPos); var info = contraption.getBlocks().get(localPos);
        if (!owned || storage == null || info == null || storage instanceof com.simibubi.create.content.logistics.vault.ItemVaultMountedStorage || storage instanceof com.simibubi.create.content.logistics.depot.storage.DepotMountedStorage) return null;
        return storage.createMenuProvider(net.minecraft.network.chat.Component.translatable("create.contraptions.moving_container", info.state().getBlock().getName()), storage.getMenuStorages(localPos, contraption), stillValid, onClose);
    }
    public void attachExternal(Storage<ItemVariant> storage) {
        assertInitialized(); MountedItemInventory.requireNoTransaction();
        var guard = new MountedStorageAccess<ItemVariant>(ItemVariant.blank()) { @Override protected Storage<ItemVariant> delegate() { return storage; } };
        bind(guard); external.add(guard);
        var all = new ArrayList<Storage<ItemVariant>>(); all.add(items); all.addAll(external); allItems = new CombinedStorage<>(List.copyOf(all));
    }
    public void writeTankContents(Map<BlockPos, StructureBlockInfo> blocks, HolderLookup.Provider registries) {
        assertInitialized();
        fluids.storages.forEach((pos, storage) -> {
            if (storage instanceof FluidTankMountedStorage tank) { var info = blocks.get(pos); if (info != null && info.nbt() != null) tank.writeBlockEntityData(info.nbt(), registries); }
        });
    }
    public void unmount(Level level, StructureBlockInfo info, BlockPos globalPos, @Nullable BlockEntity be) {
        assertInitialized();
        var item = allItemStorages.get(info.pos());
        if (item != null && item.type == MountedItemStorageType.REGISTRY.get(info.state().getBlock())) item.unmount(level, info.state(), globalPos, be);
        var fluid = fluids.storages.get(info.pos());
        // Native tank contents were written before controller remapping or sideways splitting.
        if (fluid != null && !(fluid instanceof FluidTankMountedStorage) && fluid.type == MountedFluidStorageType.REGISTRY.get(info.state().getBlock())) fluid.unmount(level, info.state(), globalPos, be);
    }
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        MountedItemInventory.requireNoTransaction();
        assertInitialized(); var itemList = new ListTag(); var fluidList = new ListTag();
        allItemStorages.forEach((pos, storage) -> { if (!clientPacket || storage instanceof SyncedMountedStorage) itemList.add(entry(pos, MountedItemStorage.CODEC, storage, registries)); });
        fluids.storages.forEach((pos, storage) -> { if (!clientPacket || storage instanceof SyncedMountedStorage) fluidList.add(entry(pos, MountedFluidStorage.CODEC, storage, registries)); });
        nbt.remove("items"); nbt.remove("fluids"); nbt.remove("interactable_positions");
        if (!itemList.isEmpty()) nbt.put("items", itemList); if (!fluidList.isEmpty()) nbt.put("fluids", fluidList);
        if (clientPacket) {
            var positions = new LinkedHashSet<>(allItemStorages.keySet()); positions.addAll(fluids.storages.keySet()); var list = new ListTag();
            for (var pos : positions) list.add(position(pos)); nbt.put("interactable_positions", list);
        }
    }
    /** Entity integration will deliver these deltas; ordinary container contents remain private. */
    public CompoundTag collectSync(HolderLookup.Provider registries) {
        MountedItemInventory.requireNoTransaction();
        assertInitialized(); var data = new CompoundTag(); var itemList = new ListTag(); var fluidList = new ListTag(); var cleaned = new ArrayList<SyncedMountedStorage>();
        allItemStorages.forEach((pos, storage) -> { if (storage instanceof SyncedMountedStorage synced && synced.isDirty()) { itemList.add(entry(pos, MountedItemStorage.CODEC, storage, registries)); cleaned.add(synced); } });
        fluids.storages.forEach((pos, storage) -> { if (storage instanceof SyncedMountedStorage synced && synced.isDirty()) { fluidList.add(entry(pos, MountedFluidStorage.CODEC, storage, registries)); cleaned.add(synced); } });
        if (!itemList.isEmpty()) data.put("items", itemList); if (!fluidList.isEmpty()) data.put("fluids", fluidList);
        cleaned.forEach(SyncedMountedStorage::markClean); return data;
    }
    public void afterSync(Contraption contraption) {
        allItemStorages.forEach((pos, storage) -> { if (storage instanceof SyncedMountedStorage synced) synced.afterSync(contraption, pos); });
        fluids.storages.forEach((pos, storage) -> { if (storage instanceof SyncedMountedStorage synced) synced.afterSync(contraption, pos); });
    }
    public void handleSync(CompoundTag changes, HolderLookup.Provider registries, Contraption contraption) {
        var knownInteractions=interactablePositions;
        var merged = new CompoundTag(); write(merged, registries, true);
        for (var key : List.of("items", "fluids")) {
            var entries = new LinkedHashMap<BlockPos, CompoundTag>();
            for (var value : merged.getListOrEmpty(key)) if (value instanceof CompoundTag entry) entries.put(readPosition(entry, "pos"), entry);
            for (var value : changes.getListOrEmpty(key)) {
                if (!(value instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid storage delta");
                var pos = readPosition(entry, "pos"); if (!contraption.getBlocks().containsKey(pos)) throw new IllegalArgumentException("Storage delta outside contraption");
                entries.put(pos, entry.copy());
            }
            var list = new ListTag(); entries.values().forEach(list::add); merged.put(key, list);
        }
        boolean wasOwned = owned; read(merged, registries, true, contraption);interactablePositions=knownInteractions;setOwned(wasOwned); afterSync(contraption);
    }
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, @Nullable Contraption contraption) {
        // Decode fully before changing live ownership; malformed storage must not silently erase items.
        var decodedItems = new LinkedHashMap<BlockPos, MountedItemStorage>(); var decodedFluids = new LinkedHashMap<BlockPos, MountedFluidStorage>();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (var value : nbt.getListOrEmpty("items")) {
            if (!(value instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid mounted item entry");
            var pos = readPosition(entry, "pos"); var storage = MountedItemStorage.CODEC.parse(ops, entry.getCompoundOrEmpty("storage")).getOrThrow();
            if (decodedItems.put(pos, storage) != null) throw new IllegalArgumentException("Duplicate mounted item position");
        }
        for (var value : nbt.getListOrEmpty("fluids")) {
            if (!(value instanceof CompoundTag entry)) throw new IllegalArgumentException("Invalid mounted fluid entry");
            var pos = readPosition(entry, "pos"); var storage = MountedFluidStorage.CODEC.parse(ops, entry.getCompoundOrEmpty("storage")).getOrThrow();
            if (decodedFluids.put(pos, storage) != null) throw new IllegalArgumentException("Duplicate mounted fluid position");
        }
        var positions = new LinkedHashSet<BlockPos>();
        for (var value : nbt.getListOrEmpty("interactable_positions")) if (value instanceof CompoundTag entry) positions.add(readCoordinates(entry));
        if (contraption != null) {
            for (var pos : decodedItems.keySet()) if (!contraption.getBlocks().containsKey(pos)) throw new IllegalArgumentException("Mounted inventory outside contraption");
            for (var pos : decodedFluids.keySet()) if (!contraption.getBlocks().containsKey(pos)) throw new IllegalArgumentException("Mounted tank outside contraption");
        }
        reset(); decodedItems.forEach((pos, storage) -> addStorage(storage, pos)); decodedFluids.forEach((pos, storage) -> addStorage(storage, pos)); initialize(); interactablePositions = Set.copyOf(positions);
    }
    private static <T> CompoundTag entry(BlockPos pos, Codec<T> codec, T storage, HolderLookup.Provider registries) {
        var result = new CompoundTag(); result.put("pos", position(pos)); result.put("storage", codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), storage).getOrThrow()); return result;
    }
    private static CompoundTag position(BlockPos pos) { var data = new CompoundTag(); data.putInt("X", pos.getX()); data.putInt("Y", pos.getY()); data.putInt("Z", pos.getZ()); return data; }
    private static BlockPos readCoordinates(CompoundTag tag) {
        if (!tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) throw new IllegalArgumentException("Incomplete mounted position");
        return new BlockPos(tag.getIntOr("X", 0), tag.getIntOr("Y", 0), tag.getIntOr("Z", 0));
    }
    private static BlockPos readPosition(CompoundTag tag, String key) {
        if (tag.get(key) instanceof CompoundTag value) return readCoordinates(value);
        return tag.read(key, BlockPos.CODEC).orElseThrow(() -> new IllegalArgumentException("Invalid mounted position"));
    }
}
