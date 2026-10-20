package com.simibubi.create.test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Addon-style block storage exercises real Fabric lookups and stable version caching. */
public final class ObserverTestFixtures implements ModInitializer {
    public static final Map<BlockPos,CountingStorage> STORAGES=new ConcurrentHashMap<>();
    @Override public void onInitialize(){ItemStorage.SIDED.registerForBlocks((level,pos,state,entity,side)->STORAGES.get(pos),Blocks.BARRIER);}
    public static final class CountingStorage extends SingleVariantStorage<ItemVariant> {
        public int probes,insertProbes;
        public boolean locked;
        private long version;
        public CountingStorage(){variant=ItemVariant.of(Items.APPLE);amount=12;}
        @Override protected ItemVariant getBlankVariant(){return ItemVariant.blank();}
        @Override protected long getCapacity(ItemVariant variant){return 64;}
        @Override protected void onFinalCommit(){version++;}
        @Override public long getVersion(){if(Transaction.isOpen())throw new IllegalStateException("Version queried inside transaction");return version;}
        public void setLocked(boolean value){if(locked!=value){locked=value;version++;}}
        @Override public long insert(ItemVariant resource,long max,TransactionContext tx){insertProbes++;return super.insert(resource,max,tx);}
        @Override public long extract(ItemVariant resource,long max,TransactionContext tx){probes++;return locked?0:super.extract(resource,max,tx);}
    }
}
