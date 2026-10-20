package com.simibubi.create.impl.contraption.storage;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Mounted handles cease to own contents as soon as their structure returns to the world. */
public abstract class MountedStorageAccess<T extends TransferVariant<?>> implements Storage<T> {
  private BooleanSupplier owner = () -> true;
  protected final T blank;
  private java.util.function.Consumer<TransactionContext> committedExchange = transaction -> {};
  private Runnable directChange = () -> {};

  /** Actual owning manager observes successful operations while the native transaction is open. */
  public final void bindExchangeObserver(
      java.util.function.Consumer<TransactionContext> exchanged, Runnable direct) {
    com.simibubi.create.foundation.item.MountedItemInventory.requireNoTransaction();
    committedExchange = Objects.requireNonNull(exchanged);
    directChange = Objects.requireNonNull(direct);
  }

  private long observe(long moved, TransactionContext transaction) {
    if (moved > 0) committedExchange.accept(transaction);
    return moved;
  }

  protected final void notifyDirectChange() {
    directChange.run();
  }

  protected MountedStorageAccess(T blank) {
    this.blank = blank;
  }

  protected abstract Storage<T> delegate();

  public final void bindOwner(BooleanSupplier owner) {
    this.owner = Objects.requireNonNull(owner);
  }

  public final boolean isAvailable() {
    return owner.getAsBoolean();
  }

  @Override
  public boolean supportsInsertion() {
    return isAvailable() && delegate().supportsInsertion();
  }

  @Override
  public boolean supportsExtraction() {
    return isAvailable() && delegate().supportsExtraction();
  }

  @Override
  public long insert(T resource, long amount, TransactionContext tx) {
    StoragePreconditions.notBlankNotNegative(resource, amount);
    return isAvailable() ? observe(delegate().insert(resource, amount, tx), tx) : 0;
  }

  @Override
  public long extract(T resource, long amount, TransactionContext tx) {
    StoragePreconditions.notBlankNotNegative(resource, amount);
    return isAvailable() ? observe(delegate().extract(resource, amount, tx), tx) : 0;
  }

  @Override
  public long getVersion() {
    return isAvailable() ? delegate().getVersion() : Long.MIN_VALUE;
  }

  @Override
  public Iterator<StorageView<T>> iterator() {
    var result = new ArrayList<StorageView<T>>();
    delegate().forEach(view -> result.add(guard(view)));
    return result.iterator();
  }

  protected final StorageView<T> guard(StorageView<T> view) {
    return new StorageView<>() {
      @Override
      public boolean isResourceBlank() {
        return !isAvailable() || view.isResourceBlank();
      }

      @Override
      public T getResource() {
        return isAvailable() ? view.getResource() : blank;
      }

      @Override
      public long getAmount() {
        return isAvailable() ? view.getAmount() : 0;
      }

      @Override
      public long getCapacity() {
        return isAvailable() ? view.getCapacity() : 0;
      }

      @Override
      public long extract(T resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return isAvailable() ? observe(view.extract(resource, amount, tx), tx) : 0;
      }

      @Override
      public StorageView<T> getUnderlyingView() {
        return view.getUnderlyingView();
      }
    };
  }

  protected final SingleSlotStorage<T> guardSlot(SingleSlotStorage<T> slot) {
    var view = guard(slot);
    return new SingleSlotStorage<>() {
      @Override
      public boolean isResourceBlank() {
        return view.isResourceBlank();
      }

      @Override
      public T getResource() {
        return view.getResource();
      }

      @Override
      public long getAmount() {
        return view.getAmount();
      }

      @Override
      public long getCapacity() {
        return view.getCapacity();
      }

      @Override
      public boolean supportsInsertion() {
        return isAvailable() && slot.supportsInsertion();
      }

      @Override
      public boolean supportsExtraction() {
        return isAvailable() && slot.supportsExtraction();
      }

      @Override
      public long insert(T resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return isAvailable() ? observe(slot.insert(resource, amount, tx), tx) : 0;
      }

      @Override
      public long extract(T resource, long amount, TransactionContext tx) {
        return view.extract(resource, amount, tx);
      }

      @Override
      public long getVersion() {
        return isAvailable() ? slot.getVersion() : Long.MIN_VALUE;
      }

      @Override
      public StorageView<T> getUnderlyingView() {
        return view.getUnderlyingView();
      }
    };
  }
}
