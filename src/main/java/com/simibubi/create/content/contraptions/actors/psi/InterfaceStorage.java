package com.simibubi.create.content.contraptions.actors.psi;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/** A connection-specific Fabric handle. A cached view never silently switches owners. */
final class InterfaceStorage<T extends TransferVariant<?>> implements Storage<T> {
    private final PortableStorageInterfaceBlockEntity endpoint;
    private final AbstractContraptionEntity entity;
    private final long generation;
    private final Storage<T> delegate;
    private final T blank;
    private boolean transferred;
    private final SnapshotParticipant<Boolean> transfer = new SnapshotParticipant<>() {
        @Override protected Boolean createSnapshot() { return transferred; }
        @Override protected void readSnapshot(Boolean snapshot) { transferred = snapshot; }
        @Override protected void onFinalCommit() {
            boolean notify = transferred; transferred = false;
            if (notify && live()) endpoint.onContentTransferred();
        }
    };
    InterfaceStorage(PortableStorageInterfaceBlockEntity endpoint, AbstractContraptionEntity entity, long generation, Storage<T> delegate, T blank) {
        this.endpoint = endpoint; this.entity = entity; this.generation = generation; this.delegate = delegate; this.blank = blank;
    }
    private boolean live() { return endpoint.ownsConnection(entity, generation); }
    private long transferred(long amount, TransactionContext tx) {
        if (amount > 0) { transfer.updateSnapshots(tx); transferred = true; }
        return amount;
    }
    @Override public long insert(T resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return live() ? transferred(delegate.insert(resource, amount, tx), tx) : 0;
    }
    @Override public long extract(T resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return live() ? transferred(delegate.extract(resource, amount, tx), tx) : 0;
    }
    @Override public boolean supportsInsertion() { return live() && delegate.supportsInsertion(); }
    @Override public boolean supportsExtraction() { return live() && delegate.supportsExtraction(); }
    @Override public long getVersion() { return live() ? delegate.getVersion() : Long.MIN_VALUE; }
    @Override public Iterator<StorageView<T>> iterator() {
        if (!live()) return Collections.emptyIterator();
        var views = new ArrayList<StorageView<T>>();
        for (var view : delegate) views.add(new StorageView<>() {
            @Override public boolean isResourceBlank() { return !live() || view.isResourceBlank(); }
            @Override public T getResource() { return live() ? view.getResource() : blank; }
            @Override public long getAmount() { return live() ? view.getAmount() : 0; }
            @Override public long getCapacity() { return live() ? view.getCapacity() : 0; }
            @Override public long extract(T resource, long amount, TransactionContext tx) {
                StoragePreconditions.notBlankNotNegative(resource, amount);
                return live() ? transferred(view.extract(resource, amount, tx), tx) : 0;
            }
            // Identity only: Fabric forbids extracting directly from this underlying view.
            @Override public StorageView<T> getUnderlyingView() { return view.getUnderlyingView(); }
        });
        return views.iterator();
    }
}
