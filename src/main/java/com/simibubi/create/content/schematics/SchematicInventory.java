package com.simibubi.create.content.schematics;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;

/** Real native menu slots, full saved source stacks and Fabric transactions share one owner. */
public class SchematicInventory extends SnapshotParticipant<List<ItemStack>> implements Container {
    private record LegacySlot(int slot,ItemStack item){private static final Codec<LegacySlot> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("Slot").forGetter(LegacySlot::slot),ItemStack.MAP_CODEC.forGetter(LegacySlot::item)).apply(i,LegacySlot::new));}
    private record LegacyInventory(int size,List<LegacySlot> items){private static final Codec<LegacyInventory> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("Size").forGetter(LegacyInventory::size),LegacySlot.CODEC.listOf().fieldOf("Items").forGetter(LegacyInventory::items)).apply(i,LegacyInventory::new));}
    private final List<ItemStack> items;
    private final BiPredicate<Integer,ItemStack> accepts;
    private final IntUnaryOperator limit;
    private final Runnable changed;
    private final Predicate<Player> access;
    private final Codec<List<ItemStack>> codec;
    private long version;
    public final Storage<ItemVariant> storage=new Storage<>(){
        @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){return SchematicInventory.this.insert(resource,amount,tx);}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){return SchematicInventory.this.extract(resource,amount,tx);}
        @Override public long getVersion(){return version;}
        @Override public Iterator<StorageView<ItemVariant>> iterator(){return views();}
    };
    public SchematicInventory(int size,BiPredicate<Integer,ItemStack> accepts,IntUnaryOperator limit,Runnable changed,Predicate<Player> access){
        if(size<1||size>64)throw new IllegalArgumentException("Invalid native schematic inventory size");items=new ArrayList<>(Collections.nCopies(size,ItemStack.EMPTY));this.accepts=accepts;this.limit=limit;this.changed=changed;this.access=Objects.requireNonNull(access);
        codec=Codec.either(ItemStack.OPTIONAL_CODEC.listOf(),LegacyInventory.CODEC).flatXmap(value->decode(value,size),value->DataResult.success(Either.left(value)));
    }
    private static DataResult<List<ItemStack>> decode(Either<List<ItemStack>,LegacyInventory> input,int size){return input.map(items->items.size()==size?DataResult.success(items):DataResult.error(()->"Incomplete native schematic inventory"),legacy->{
        if(legacy.size()!=size)return DataResult.error(()->"Wrong original schematic inventory size");var items=new ArrayList<ItemStack>(Collections.nCopies(size,ItemStack.EMPTY));var seen=new HashSet<Integer>();
        for(var entry:legacy.items())if(entry.slot()<0||entry.slot()>=size||!seen.add(entry.slot()))return DataResult.error(()->"Invalid or duplicate original schematic inventory slot");else items.set(entry.slot(),entry.item());return DataResult.success(items);
    });}
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public boolean canPlaceItem(int slot,ItemStack item){return accepts.test(slot,item);}
    @Override public int getMaxStackSize(){return 64;}
    public int slotLimit(int slot){return Math.max(0,limit.applyAsInt(slot));}
    @Override public ItemStack removeItem(int slot,int count){if(count<=0||items.get(slot).isEmpty())return ItemStack.EMPTY;var result=items.get(slot).split(count);if(items.get(slot).isEmpty())items.set(slot,ItemStack.EMPTY);setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return items.set(slot,ItemStack.EMPTY);}
    @Override public void setItem(int slot,ItemStack item){items.set(slot,item.copy());setChanged();}
    @Override public void setChanged(){version++;changed.run();}
    @Override public boolean stillValid(Player player){return access.test(player);}
    @Override public void clearContent(){Collections.fill(items,ItemStack.EMPTY);setChanged();}
    public List<ItemStack> copies(){return items.stream().map(ItemStack::copy).toList();}
    @Override protected List<ItemStack> createSnapshot(){return copies();}
    @Override protected void readSnapshot(List<ItemStack> saved){for(int i=0;i<items.size();i++)items.set(i,saved.get(i));}
    @Override protected void onFinalCommit(){setChanged();}
    public long insert(ItemVariant resource,long amount,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;var sample=resource.toStack();
        for(int pass=0;pass<2&&moved<amount;pass++)for(int i=0;i<items.size()&&moved<amount;i++){
            var present=items.get(i);if(!canPlaceItem(i,sample)||pass==0&&present.isEmpty()||pass==1&&!present.isEmpty()||!present.isEmpty()&&!resource.equals(ItemVariant.of(present)))continue;
            int capacity=Math.min(slotLimit(i),sample.getMaxStackSize())-present.getCount();int count=(int)Math.min(amount-moved,Math.max(0,capacity));if(count==0)continue;updateSnapshots(tx);if(present.isEmpty())items.set(i,resource.toStack(count));else present.grow(count);moved+=count;
        }return moved;
    }
    public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;for(int i=0;i<items.size()&&moved<amount;i++)moved+=extractSlot(i,resource,amount-moved,tx);return moved;}
    public long extractSlot(int slot,ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);var present=items.get(slot);if(amount==0||present.isEmpty()||!resource.equals(ItemVariant.of(present)))return 0;int moved=(int)Math.min(amount,present.getCount());updateSnapshots(tx);present.shrink(moved);if(present.isEmpty())items.set(slot,ItemStack.EMPTY);return moved;}
    private Iterator<StorageView<ItemVariant>> views(){var result=new ArrayList<StorageView<ItemVariant>>();for(int i=0;i<items.size();i++){final int slot=i;result.add(new StorageView<>(){
        @Override public boolean isResourceBlank(){return items.get(slot).isEmpty();}
        @Override public ItemVariant getResource(){return ItemVariant.of(items.get(slot));}
        @Override public long getAmount(){return items.get(slot).getCount();}
        @Override public long getCapacity(){return Math.max(slotLimit(slot),getAmount());}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){return extractSlot(slot,resource,amount,tx);}
    });}return result.iterator();}
    public void write(ValueOutput output){output.store("Inventory",ItemStack.OPTIONAL_CODEC.listOf(),copies());}
    public void read(ValueInput input){var raw=input.read("Inventory",Codec.PASSTHROUGH);var decoded=input.read("Inventory",codec);if(raw.isPresent()&&decoded.isEmpty())throw new IllegalArgumentException("Invalid owned schematic inventory cannot be silently discarded");var loaded=decoded.orElse(Collections.nCopies(items.size(),ItemStack.EMPTY));for(int i=0;i<items.size();i++)items.set(i,loaded.get(i).copy());version++;}
}
