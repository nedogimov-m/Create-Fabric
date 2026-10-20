package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Actual source mounted train cargo. Only committed native item/fluid exchanges reset its tracker.
 */
public final class TrainCargoManager extends MountedStorageManager {
  private int ticksSinceLastExchange;
  private final AtomicInteger version = new AtomicInteger();
  private final ExchangeTracker exchanges = new ExchangeTracker();
  private final Map<Storage<?>, Storage<?>> tracked = new IdentityHashMap<>();
  private MountedItemStorageWrapper rawItems, trackedItems, rawFuel, trackedFuel;
  private MountedFluidStorageWrapper rawFluids, trackedFluids;

  private void directCommittedExchange() {
    version.incrementAndGet();
    resetIdleCargoTracker();
  }

  @Override
  public void addStorage(
      com.simibubi.create.api.contraption.storage.item.MountedItemStorage actual,
      net.minecraft.core.BlockPos pos) {
    super.addStorage(actual, pos);
    actual.bindExchangeObserver(exchanges::record, this::directCommittedExchange);
  }

  @Override
  public void addStorage(
      com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage actual,
      net.minecraft.core.BlockPos pos) {
    super.addStorage(actual, pos);
    actual.bindExchangeObserver(exchanges::record, this::directCommittedExchange);
  }

  private final class ExchangeTracker extends SnapshotParticipant<Boolean> {
    private boolean pending;

    void record(TransactionContext transaction) {
      updateSnapshots(transaction);
      pending = true;
    }

    @Override
    protected Boolean createSnapshot() {
      return pending;
    }

    @Override
    protected void readSnapshot(Boolean previous) {
      pending = previous;
    }

    @Override
    protected void onFinalCommit() {
      if (pending) {
        version.incrementAndGet();
        resetIdleCargoTracker();
        pending = false;
      }
    }
  }

  private final class TrackedStorage<T> implements Storage<T> {
    private final Storage<T> actual;

    TrackedStorage(Storage<T> actual) {
      this.actual = actual;
    }

    @Override
    public boolean supportsInsertion() {
      return actual.supportsInsertion();
    }

    @Override
    public boolean supportsExtraction() {
      return actual.supportsExtraction();
    }

    @Override
    public long getVersion() {
      return actual.getVersion();
    }

    @Override
    public long insert(T resource, long amount, TransactionContext transaction) {
      long moved = actual.insert(resource, amount, transaction);
      if (moved > 0) exchanges.record(transaction);
      return moved;
    }

    @Override
    public long extract(T resource, long amount, TransactionContext transaction) {
      long moved = actual.extract(resource, amount, transaction);
      if (moved > 0) exchanges.record(transaction);
      return moved;
    }

    @Override
    public Iterator<StorageView<T>> iterator() {
      var views = new ArrayList<StorageView<T>>();
      for (var actualView : actual)
        views.add(
            new StorageView<>() {
              @Override
              public boolean isResourceBlank() {
                return actualView.isResourceBlank();
              }

              @Override
              public T getResource() {
                return actualView.getResource();
              }

              @Override
              public long getAmount() {
                return actualView.getAmount();
              }

              @Override
              public long getCapacity() {
                return actualView.getCapacity();
              }

              @Override
              public long extract(T resource, long amount, TransactionContext transaction) {
                long moved = actualView.extract(resource, amount, transaction);
                if (moved > 0) exchanges.record(transaction);
                return moved;
              }

              @Override
              public StorageView<T> getUnderlyingView() {
                return actualView.getUnderlyingView();
              }
            });
      return views.iterator();
    }
  }

  private <T> Storage<T> tracking(Storage<T> actual) {
    @SuppressWarnings("unchecked")
    Storage<T> existing = (Storage<T>) tracked.get(actual);
    if (existing != null) return existing;
    var wrapper = new TrackedStorage<>(actual);
    tracked.put(actual, wrapper);
    return wrapper;
  }

  private final class CargoItems extends MountedItemStorageWrapper {
    private final Storage<ItemVariant> actual;

    CargoItems(MountedItemStorageWrapper source) {
      super(source.storages);
      actual = tracking(source);
    }

    @Override
    public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
      return actual.insert(resource, amount, transaction);
    }

    @Override
    public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
      return actual.extract(resource, amount, transaction);
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
      return actual.iterator();
    }
  }

  private final class CargoFluids extends MountedFluidStorageWrapper {
    private final Storage<FluidVariant> actual;

    CargoFluids(MountedFluidStorageWrapper source) {
      super(source.storages);
      actual = tracking(source);
    }

    @Override
    public long insert(FluidVariant resource, long amount, TransactionContext transaction) {
      return actual.insert(resource, amount, transaction);
    }

    @Override
    public long extract(FluidVariant resource, long amount, TransactionContext transaction) {
      return actual.extract(resource, amount, transaction);
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
      return actual.iterator();
    }
  }

  @Override
  public MountedItemStorageWrapper getMountedItems() {
    var actual = super.getMountedItems();
    if (rawItems != actual) {
      rawItems = actual;
      trackedItems = new CargoItems(actual);
    }
    return trackedItems;
  }

  @Override
  public @Nullable MountedItemStorageWrapper getFuelItems() {
    var actual = super.getFuelItems();
    if (actual == null) return null;
    if (rawFuel != actual) {
      rawFuel = actual;
      trackedFuel = new CargoItems(actual);
    }
    return trackedFuel;
  }

  @Override
  public MountedFluidStorageWrapper getFluids() {
    var actual = super.getFluids();
    if (rawFluids != actual) {
      rawFluids = actual;
      trackedFluids = new CargoFluids(actual);
    }
    return trackedFluids;
  }

  @Override
  public Storage<ItemVariant> getAllItems() {
    return tracking(super.getAllItems());
  }

  @Override
  public void reset() {
    super.reset();
    tracked.clear();
    rawItems = trackedItems = rawFuel = trackedFuel = null;
    rawFluids = trackedFluids = null;
  }

  @Override
  public void write(CompoundTag data, HolderLookup.Provider registries, boolean clientPacket) {
    super.write(data, registries, clientPacket);
    data.putInt("TicksSinceLastExchange", ticksSinceLastExchange);
  }

  @Override
  public void read(
      CompoundTag data,
      HolderLookup.Provider registries,
      boolean clientPacket,
      @Nullable Contraption contraption) {
    int idle = data.getIntOr("TicksSinceLastExchange", 0);
    if (idle < 0) throw new IllegalArgumentException("Negative actual train cargo idle tracker");
    super.read(data, registries, clientPacket, contraption);
    ticksSinceLastExchange = idle;
  }

  public void resetIdleCargoTracker() {
    ticksSinceLastExchange = 0;
  }

  public void tickIdleCargoTracker() {
    if (ticksSinceLastExchange < Integer.MAX_VALUE) ticksSinceLastExchange++;
  }

  public int getTicksSinceLastExchange() {
    return ticksSinceLastExchange;
  }

  public int getVersion() {
    return version.get();
  }
}
