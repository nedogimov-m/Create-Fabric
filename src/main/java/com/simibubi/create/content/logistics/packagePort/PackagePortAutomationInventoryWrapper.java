package com.simibubi.create.content.logistics.packagePort;
import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
/** Incoming mail can leave; only outgoing mail can enter through automation. */
public final class PackagePortAutomationInventoryWrapper implements Storage<ItemVariant> {
    private final PackagePortBlockEntity owner;
    public PackagePortAutomationInventoryWrapper(PackagePortBlockEntity owner){this.owner=owner;}
    private boolean incoming(ItemVariant resource){String filter=owner.getFilterString();return filter!=null&&PackageItem.matchAddress(resource.toStack(),filter);}
    @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return !resource.isBlank()&&PackageItem.isPackage(resource.toStack())&&!incoming(resource)?owner.inventory.insert(resource,amount,tx):0;}
    @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return !resource.isBlank()&&incoming(resource)?owner.inventory.extract(resource,amount,tx):0;}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){var views=new ArrayList<StorageView<ItemVariant>>();owner.inventory.storage.forEach(view->views.add(new StorageView<>(){
        @Override public boolean isResourceBlank(){return view.isResourceBlank();}
        @Override public ItemVariant getResource(){return view.getResource();}
        @Override public long getAmount(){return view.getAmount();}
        @Override public long getCapacity(){return view.getCapacity();}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return incoming(resource)?view.extract(resource,amount,tx):0;}
    }));return views.iterator();}
    @Override public long getVersion(){return owner.inventory.getVersion();}
}
