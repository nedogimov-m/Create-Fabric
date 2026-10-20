package com.simibubi.create.foundation.blockEntity.behaviour.inventory;

import com.simibubi.create.foundation.blockEntity.behaviour.*;
import java.lang.ref.WeakReference;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Fabric's unknown-version default changes on every query, so unversioned handlers never stall. */
public final class VersionedInventoryTrackerBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<VersionedInventoryTrackerBehaviour> TYPE=new BehaviourType<>();
    private WeakReference<Storage<?>> ignored=new WeakReference<>(null);
    private long version;
    public VersionedInventoryTrackerBehaviour(BlockEntity entity){super(entity);}
    public boolean stillWaiting(Storage<?> storage){return storage!=null&&ignored.get()==storage&&storage.getVersion()==version;}
    public void awaitNewVersion(Storage<?> storage){if(storage==null){reset();return;}ignored=new WeakReference<>(storage);version=storage.getVersion();}
    public void reset(){ignored.clear();version=Long.MIN_VALUE;}
    @Override public BehaviourType<?> getType(){return TYPE;}
}
