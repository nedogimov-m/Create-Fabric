package com.simibubi.create.content.logistics.packagePort;

import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;

/** Native menu/Fabric slots share the eighteen original stacks, including retained old buffers. */
public final class PackagePortInventory extends SnapshotParticipant<List<ItemStack>> implements Container {
    private record LegacySlot(int slot,ItemStack item){
        static final com.mojang.serialization.Codec<LegacySlot> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.create(instance->instance.group(com.mojang.serialization.Codec.INT.fieldOf("Slot").forGetter(LegacySlot::slot),ItemStack.MAP_CODEC.forGetter(LegacySlot::item)).apply(instance,LegacySlot::new));
    }
    private record LegacyInventory(int size,List<LegacySlot> items){
        static final com.mojang.serialization.Codec<LegacyInventory> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.create(i->i.group(com.mojang.serialization.Codec.INT.optionalFieldOf("Size",18).forGetter(LegacyInventory::size),LegacySlot.CODEC.listOf().fieldOf("Items").forGetter(LegacyInventory::items)).apply(i,LegacyInventory::new));
    }
    private static final com.mojang.serialization.Codec<List<ItemStack>> INVENTORY_CODEC=com.mojang.serialization.Codec.either(ItemStack.OPTIONAL_CODEC.listOf(),LegacyInventory.CODEC).flatXmap(PackagePortInventory::decodeInventory,value->com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Either.left(value)));
    private static com.mojang.serialization.DataResult<List<ItemStack>> decodeInventory(com.mojang.datafixers.util.Either<List<ItemStack>,LegacyInventory> encoded){
        return encoded.map(items->items.size()==18?com.mojang.serialization.DataResult.success(items):com.mojang.serialization.DataResult.error(()->"Original mail inventory must have18complete slots"),legacy->{
            if(legacy.size()!=18)return com.mojang.serialization.DataResult.error(()->"Original legacy mail inventory must have18slots");
            var result=new ArrayList<ItemStack>(Collections.nCopies(18,ItemStack.EMPTY));var seen=new HashSet<Integer>();
            for(var slot:legacy.items())if(slot.slot()<0||slot.slot()>=18||!seen.add(slot.slot()))return com.mojang.serialization.DataResult.error(()->"Invalid or duplicate original legacy mail slot");else result.set(slot.slot(),slot.item());
            return com.mojang.serialization.DataResult.success(result);
        });
    }
    private final PackagePortBlockEntity owner;
    private final List<ItemStack> items=new ArrayList<>(Collections.nCopies(18,ItemStack.EMPTY));
    private long version;
    public final Storage<ItemVariant> storage=new Storage<>(){
        @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){return PackagePortInventory.this.insert(resource,amount,tx);}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){return PackagePortInventory.this.extract(resource,amount,tx);}
        @Override public Iterator<StorageView<ItemVariant>> iterator(){return storageIterator();}
        @Override public long getVersion(){return version;}
    };
    public PackagePortInventory(PackagePortBlockEntity owner){this.owner=owner;}
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return PackageItem.isPackage(stack);}
    @Override public boolean stillValid(Player player){return !owner.isRemoved()&&player.isWithinBlockInteractionRange(owner.getBlockPos(),1);}
    @Override public void setItem(int slot,ItemStack stack){items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copy());setChanged();}
    @Override public ItemStack removeItem(int slot,int count){if(count<=0)return ItemStack.EMPTY;var item=items.get(slot);if(item.isEmpty())return ItemStack.EMPTY;var removed=item.split(Math.min(count,item.getCount()));if(item.isEmpty())items.set(slot,ItemStack.EMPTY);setChanged();return removed;}
    @Override public ItemStack removeItemNoUpdate(int slot){var item=items.set(slot,ItemStack.EMPTY);return item;}
    @Override public void setChanged(){version++;owner.inventoryChanged();}
    @Override public void clearContent(){Collections.fill(items,ItemStack.EMPTY);setChanged();}
    @Override protected List<ItemStack> createSnapshot(){return items.stream().map(ItemStack::copy).toList();}
    @Override protected void readSnapshot(List<ItemStack> snapshot){for(int i=0;i<items.size();i++)items.set(i,snapshot.get(i));}
    @Override protected void onFinalCommit(){setChanged();}
    public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0||!PackageItem.isPackage(resource.toStack()))return 0;long moved=0;for(int i=0;i<items.size()&&moved<amount;i++)if(items.get(i).isEmpty()){updateSnapshots(tx);items.set(i,resource.toStack());moved++;}return moved;}
    public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(int i=0;i<items.size()&&moved<amount;i++)if(!items.get(i).isEmpty()&&resource.equals(ItemVariant.of(items.get(i)))){int count=(int)Math.min(amount-moved,items.get(i).getCount());updateSnapshots(tx);items.get(i).shrink(count);if(items.get(i).isEmpty())items.set(i,ItemStack.EMPTY);moved+=count;}return moved;}
    public Iterator<StorageView<ItemVariant>> storageIterator(){var views=new ArrayList<StorageView<ItemVariant>>();for(int i=0;i<items.size();i++){int slot=i;views.add(new StorageView<>(){
        @Override public boolean isResourceBlank(){return items.get(slot).isEmpty();}
        @Override public ItemVariant getResource(){return ItemVariant.of(items.get(slot));}
        @Override public long getAmount(){return items.get(slot).getCount();}
        @Override public long getCapacity(){return Math.max(1,getAmount());}
        public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0||isResourceBlank()||!getResource().equals(resource))return 0;int count=(int)Math.min(amount,getAmount());updateSnapshots(tx);items.get(slot).shrink(count);if(items.get(slot).isEmpty())items.set(slot,ItemStack.EMPTY);return count;}
    });}return views.iterator();}
    public long getVersion(){return version;}
    public boolean full(){return items.stream().noneMatch(ItemStack::isEmpty);}
    public void write(ValueOutput out){out.store("Inventory",ItemStack.OPTIONAL_CODEC.listOf(),List.copyOf(items));}
    public void read(ValueInput in){
        var encoded=in.read("Inventory",com.mojang.serialization.Codec.PASSTHROUGH);var loaded=in.read("Inventory",INVENTORY_CODEC);
        if(encoded.isPresent()&&loaded.isEmpty())throw new IllegalArgumentException("Invalid original mail inventory cannot be replaced with empty cargo");
        var originals=loaded.orElse(Collections.nCopies(18,ItemStack.EMPTY));
        for(int i=0;i<items.size();i++)items.set(i,originals.get(i).copy());version++;
    }
}
