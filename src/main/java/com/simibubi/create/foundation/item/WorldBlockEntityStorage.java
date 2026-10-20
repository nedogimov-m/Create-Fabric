package com.simibubi.create.foundation.item;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A world capability expires when its exact BE leaves that loaded world position. */
public final class WorldBlockEntityStorage<T extends TransferVariant<?>> implements Storage<T> {
    private record Entry(WeakReference<Storage<?>> storage,WeakReference<Storage<?>> guarded){}
    private static final Map<BlockEntity,List<Entry>> CACHE=new WeakHashMap<>();
    private final WeakReference<BlockEntity> owner;
    private final Storage<T> delegate;
    private final T blank;
    private WorldBlockEntityStorage(BlockEntity owner,Storage<T> delegate,T blank){this.owner=new WeakReference<>(owner);this.delegate=delegate;this.blank=blank;}
    @SuppressWarnings("unchecked") public static synchronized <T extends TransferVariant<?>> Storage<T> of(BlockEntity owner,Storage<T> storage,T blank){
        if(storage==null||owner==null)return storage;if(storage instanceof WorldBlockEntityStorage<?>)return storage;
        var entries=CACHE.computeIfAbsent(owner,key->new ArrayList<>());entries.removeIf(entry->entry.storage().get()==null||entry.guarded().get()==null);
        for(var entry:entries)if(entry.storage().get()==storage)return (Storage<T>)entry.guarded().get();
        var guarded=new WorldBlockEntityStorage<>(owner,storage,blank);entries.add(new Entry(new WeakReference<>(storage),new WeakReference<>(guarded)));return guarded;
    }
    private boolean live(){var be=owner.get();return be!=null&&!be.isRemoved()&&be.getLevel()!=null&&LoadedLevelAccess.blockState(be.getLevel(),be.getBlockPos()).getBlock()==be.getBlockState().getBlock()&&LoadedLevelAccess.blockEntity(be.getLevel(),be.getBlockPos())==be;}
    /** For recognizing storage kinds only. Mutations must use the guarded storage, like Fabric's underlying-view contract. */
    public static Storage<?> forTypeInspection(Storage<?> storage){while(storage instanceof WorldBlockEntityStorage<?> guarded){if(!guarded.live())return null;storage=guarded.delegate;}return storage;}
    @Override public long insert(T resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return live()?delegate.insert(resource,amount,tx):0;}
    @Override public long extract(T resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return live()?delegate.extract(resource,amount,tx):0;}
    @Override public long getVersion(){return live()?delegate.getVersion():Long.MIN_VALUE;}
    @Override public boolean supportsInsertion(){return live()&&delegate.supportsInsertion();}
    @Override public boolean supportsExtraction(){return live()&&delegate.supportsExtraction();}
    @Override public Iterator<StorageView<T>> iterator(){
        if(!live())return Collections.emptyIterator();var views=new ArrayList<StorageView<T>>();for(var view:delegate)views.add(new StorageView<>(){
            @Override public boolean isResourceBlank(){return !live()||view.isResourceBlank();}
            @Override public T getResource(){return live()?view.getResource():blank;}
            @Override public long getAmount(){return live()?view.getAmount():0;}
            @Override public long getCapacity(){return live()?view.getCapacity():0;}
            @Override public long extract(T resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return live()?view.extract(resource,amount,tx):0;}
            // Fabric explicitly reserves this identity for slot comparison, not extraction.
            @Override public StorageView<T> getUnderlyingView(){return view.getUnderlyingView();}
        });return views.iterator();
    }
}
