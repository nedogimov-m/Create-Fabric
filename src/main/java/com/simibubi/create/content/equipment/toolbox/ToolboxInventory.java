package com.simibubi.create.content.equipment.toolbox;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Eight retained filters and thirty-two real slots. Stock and filters share one transaction. */
public final class ToolboxInventory extends SnapshotParticipant<ToolboxInventory.State> implements SlottedStorage<ItemVariant> {
    public static final int COMPARTMENTS=8, STACKS_PER_COMPARTMENT=4, SIZE=32;
    private static final Codec<Integer> INDEX=Codec.STRING.comapFlatMap(value->{try{int index=Integer.parseInt(value);return index>=0&&index<SIZE?DataResult.success(index):DataResult.error(()->"Invalid toolbox slot");}catch(NumberFormatException ex){return DataResult.error(()->"Invalid toolbox slot: "+value);}},Object::toString);
    private record Slots(Map<Integer,ItemStack> items,int size){}
    private static final Codec<Slots> SLOTS=RecordCodecBuilder.create(i->i.group(Codec.unboundedMap(INDEX,ItemStack.CODEC).fieldOf("items").forGetter(Slots::items),Codec.intRange(0,SIZE).fieldOf("size").forGetter(Slots::size)).apply(i,Slots::new));
    private record Data(Slots slots,List<ItemStack> filters){}
    private static final Codec<Data> DATA=RecordCodecBuilder.create(i->i.group(SLOTS.fieldOf("items").forGetter(Data::slots),ItemStack.OPTIONAL_CODEC.listOf().validate(values->values.size()==COMPARTMENTS?DataResult.success(values):DataResult.error(()->"Toolbox requires eight filters")).fieldOf("filters").forGetter(Data::filters)).apply(i,Data::new));
    public static final Codec<ToolboxInventory> CODEC=DATA.comapFlatMap(ToolboxInventory::decode,ToolboxInventory::data);
    public static final Codec<ToolboxInventory> BACKWARDS_COMPAT_CODEC=Codec.withAlternative(CODEC,ItemContainerContents.CODEC.xmap(ToolboxInventory::fromContainerContents,ToolboxInventory::containerContents));
    private record LegacyItem(ItemStack item,int slot){}
    private static final Codec<LegacyItem> LEGACY_ITEM=RecordCodecBuilder.create(i->i.group(ItemStack.MAP_CODEC.forGetter(LegacyItem::item),Codec.intRange(0,SIZE-1).fieldOf("Slot").forGetter(LegacyItem::slot)).apply(i,LegacyItem::new));
    private record LegacyData(int size,List<LegacyItem> items,List<ItemStack> filters){}
    private static final Codec<LegacyData> LEGACY_DATA=RecordCodecBuilder.create(i->i.group(Codec.intRange(SIZE,SIZE).fieldOf("Size").forGetter(LegacyData::size),LEGACY_ITEM.listOf().fieldOf("Items").forGetter(LegacyData::items),ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("Compartments",List.of()).forGetter(LegacyData::filters)).apply(i,LegacyData::new));
    public static final Codec<ToolboxInventory> LEGACY_CODEC=LEGACY_DATA.comapFlatMap(data->{var sparse=new LinkedHashMap<Integer,ItemStack>();for(var entry:data.items){if(sparse.put(entry.slot,entry.item)!=null)return DataResult.error(()->"Duplicate legacy toolbox slot");}return decode(new Data(new Slots(sparse,SIZE),data.filters.size()==COMPARTMENTS?data.filters:Collections.nCopies(COMPARTMENTS,ItemStack.EMPTY)));},inventory->{var list=new ArrayList<LegacyItem>();for(int slot=0;slot<SIZE;slot++)if(!inventory.items.get(slot).isEmpty())list.add(new LegacyItem(inventory.items.get(slot).copy(),slot));return new LegacyData(SIZE,list,copies(inventory.filters));});
    public static final Codec<ToolboxInventory> SAVED_INVENTORY_CODEC=Codec.withAlternative(BACKWARDS_COMPAT_CODEC,LEGACY_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf,ToolboxInventory> STREAM_CODEC=ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public record State(List<ItemStack> items,List<ItemStack> filters){}
    private final ArrayList<ItemStack> items=new ArrayList<>(Collections.nCopies(SIZE,ItemStack.EMPTY));
    private final ArrayList<ItemStack> filters=new ArrayList<>(Collections.nCopies(COMPARTMENTS,ItemStack.EMPTY));
    private final List<SlotStorage> slots;
    private Runnable changed=()->{};
    private long version;
    private int limitedDepth;
    private boolean frozen,clientReplica;

    private final Container container=new Container(){
        @Override public int getContainerSize(){return SIZE;}
        @Override public boolean isEmpty(){return ToolboxInventory.this.isEmpty();}
        @Override public ItemStack getItem(int slot){return ToolboxInventory.this.getItem(slot);}
        @Override public ItemStack removeItem(int slot,int amount){return ToolboxInventory.this.removeItem(slot,amount);}
        @Override public ItemStack removeItemNoUpdate(int slot){return ToolboxInventory.this.removeItemNoUpdate(slot);}
        @Override public void setItem(int slot,ItemStack stack){ToolboxInventory.this.setItem(slot,stack);}
        @Override public void setChanged(){ToolboxInventory.this.setChanged();}
        @Override public boolean stillValid(Player player){return true;}
        @Override public boolean canPlaceItem(int slot,ItemStack stack){return isItemValid(slot,stack);}
        @Override public void clearContent(){ToolboxInventory.this.clearContent();}
    };
    public Container asContainer(){return container;}
    public ToolboxInventory(){var values=new ArrayList<SlotStorage>(SIZE);for(int i=0;i<SIZE;i++)values.add(new SlotStorage(i));slots=List.copyOf(values);}
    public void setClientReplica(boolean client){requireDirect();clientReplica=client;}
    public void onChange(Runnable callback){requireMutable();changed=Objects.requireNonNull(callback);}
    private static List<ItemStack> copies(List<ItemStack> stacks){return stacks.stream().map(ItemStack::copy).toList();}
    @Override protected State createSnapshot(){return new State(copies(items),copies(filters));}
    @Override protected void readSnapshot(State state){for(int i=0;i<SIZE;i++)items.set(i,state.items.get(i).copy());for(int i=0;i<COMPARTMENTS;i++)filters.set(i,state.filters.get(i).copy());}
    @Override protected void onFinalCommit(){if(!clientReplica)for(int c=0;c<COMPARTMENTS;c++)settleRaw(c);version++;changed.run();}
    private void requireMutable(){if(frozen)throw new IllegalStateException("Toolbox item component is immutable; copy before use");}
    private void requireDirect(){requireMutable();if(Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)throw new IllegalStateException("Use transactional toolbox storage during transfers");}
    private void snapshot(TransactionContext tx){requireMutable();updateSnapshots(tx);}
    private static void checkCompartment(int compartment){Objects.checkIndex(compartment,COMPARTMENTS);}
    public ToolboxInventory copy(){var copy=new ToolboxInventory();copy.readSnapshot(createSnapshot());return copy;}
    /** Component values own immutable deep snapshots, independent of placed/mounted stock. */
    public ToolboxInventory frozenCopy(){var copy=copy();copy.frozen=true;return copy;}
    public void copyFrom(ToolboxInventory source){requireDirect();readSnapshot(source.createSnapshot());onFinalCommit();}
    public ItemStack getFilter(int compartment){checkCompartment(compartment);return filters.get(compartment).copy();}
    public List<ItemStack> getFilters(){return copies(filters);}
    public int totalCount(int compartment){checkCompartment(compartment);int count=0;for(int i=0;i<STACKS_PER_COMPARTMENT;i++)count+=items.get(compartment*STACKS_PER_COMPARTMENT+i).getCount();return count;}
    public void clearFilter(int compartment){requireDirect();checkCompartment(compartment);if(totalCount(compartment)!=0)return;filters.set(compartment,ItemStack.EMPTY);onFinalCommit();}
    public void inLimitedMode(Consumer<ToolboxInventory> operation){limitedDepth++;try{operation.accept(this);}finally{limitedDepth--;}}
    public <T>T inLimitedMode(java.util.function.Supplier<T> operation){limitedDepth++;try{return operation.get();}finally{limitedDepth--;}}
    public int getContainerSize(){return SIZE;}
    @Override public int getSlotCount(){return SIZE;}
    @Override public SlotStorage getSlot(int slot){return slots.get(slot);}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){return new ArrayList<StorageView<ItemVariant>>(slots).iterator();}
    @Override public long getVersion(){return version;}
    public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    /** Vanilla menus mutate live stacks before setChanged; frozen component reads remain defensive. */
    public ItemStack getItem(int slot){ItemStack stack=items.get(slot);return frozen?stack.copy():stack;}
    public ItemStack getStackInSlot(int slot){return items.get(slot).copy();}
    public boolean canPlaceItem(int slot,ItemStack stack){return isItemValid(slot,stack);}
    public boolean isItemValid(int slot,ItemStack stack){if(slot<0||slot>=SIZE||stack.isEmpty()||!stack.getItem().canFitInsideContainerItems())return false;ItemStack filter=filters.get(slot/STACKS_PER_COMPARTMENT);return (!filter.isEmpty()||limitedDepth==0)&&(filter.isEmpty()||canItemsShareCompartment(filter,stack));}
    public void setItem(int slot,ItemStack stack){requireDirect();Objects.checkIndex(slot,SIZE);if(!stack.isEmpty()&&(stack.getCount()>stack.getMaxStackSize()||!clientReplica&&!isItemValid(slot,stack)))throw new IllegalArgumentException("Invalid toolbox stack");items.set(slot,stack.copy());claim(slot,stack);if(!clientReplica)settleRaw(slot/STACKS_PER_COMPARTMENT);onFinalCommit();}
    public void setStackInSlot(int slot,ItemStack stack){setItem(slot,stack);}
    public ItemStack removeItem(int slot,int amount){requireDirect();if(amount<=0)return ItemStack.EMPTY;ItemStack taken=items.get(slot).split(amount);if(!taken.isEmpty()){if(!clientReplica)settleRaw(slot/STACKS_PER_COMPARTMENT);onFinalCommit();}return taken;}
    public ItemStack removeItemNoUpdate(int slot){requireDirect();ItemStack value=items.set(slot,ItemStack.EMPTY);return value;}
    public void clearContent(){requireDirect();Collections.fill(items,ItemStack.EMPTY);onFinalCommit();}
    public void setChanged(){requireDirect();for(int i=0;i<SIZE;i++)claim(i,items.get(i));if(!clientReplica)for(int c=0;c<COMPARTMENTS;c++)settleRaw(c);onFinalCommit();}
    private void claim(int slot,ItemStack stack){int compartment=slot/STACKS_PER_COMPARTMENT;if(!stack.isEmpty()&&filters.get(compartment).isEmpty())filters.set(compartment,stack.copyWithCount(1));}
    public void settle(int compartment){requireDirect();checkCompartment(compartment);if(settleRaw(compartment))onFinalCommit();}
    /** Compact exact variants; distinct damaged tools/enchants remain separate real stacks. */
    private boolean settleRaw(int compartment){int base=compartment*STACKS_PER_COMPARTMENT;var compact=new ArrayList<ItemStack>();for(int i=0;i<STACKS_PER_COMPARTMENT;i++){ItemStack value=items.get(base+i).copy();if(value.isEmpty())continue;for(ItemStack existing:compact){if(!ItemStack.isSameItemSameComponents(existing,value))continue;int moved=Math.min(value.getCount(),existing.getMaxStackSize()-existing.getCount());existing.grow(moved);value.shrink(moved);if(value.isEmpty())break;}if(!value.isEmpty())compact.add(value);}boolean modified=false;for(int i=0;i<STACKS_PER_COMPARTMENT;i++){ItemStack next=i<compact.size()?compact.get(i):ItemStack.EMPTY;if(!ItemStack.matches(items.get(base+i),next)){items.set(base+i,next);modified=true;}}return modified;}
    @Override public long insert(ItemVariant variant,long maxAmount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,maxAmount);long inserted=0;for(int c=0;c<COMPARTMENTS&&inserted<maxAmount;c++)inserted+=insertCompartment(variant,maxAmount-inserted,c,tx,false);return inserted;}
    public long insertCompartment(ItemVariant variant,long amount,int compartment,TransactionContext tx,boolean requireFilter){checkCompartment(compartment);if(requireFilter&&filters.get(compartment).isEmpty())return 0;long inserted=0;for(int i=STACKS_PER_COMPARTMENT-1;i>=0&&inserted<amount;i--)inserted+=getSlot(compartment*STACKS_PER_COMPARTMENT+i).insert(variant,amount-inserted,tx);return inserted;}
    @Override public long extract(ItemVariant variant,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,amount);long extracted=0;for(int c=0;c<COMPARTMENTS&&extracted<amount;c++)extracted+=extractCompartment(variant,amount-extracted,c,tx);return extracted;}
    public long extractCompartment(ItemVariant variant,long amount,int compartment,TransactionContext tx){checkCompartment(compartment);long extracted=0;for(int i=STACKS_PER_COMPARTMENT-1;i>=0&&extracted<amount;i--)extracted+=getSlot(compartment*STACKS_PER_COMPARTMENT+i).extract(variant,amount-extracted,tx);return extracted;}
    public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){if(stack.isEmpty())return ItemStack.EMPTY;try(var tx=Transaction.openOuter()){long inserted=getSlot(slot).insert(ItemVariant.of(stack),stack.getCount(),tx);if(!simulate)tx.commit();return stack.copyWithCount(stack.getCount()-(int)inserted);}}
    public ItemStack extractItem(int slot,int amount,boolean simulate){ItemStack source=getStackInSlot(slot);if(source.isEmpty()||amount<=0)return ItemStack.EMPTY;try(var tx=Transaction.openOuter()){long extracted=getSlot(slot).extract(ItemVariant.of(source),amount,tx);if(!simulate)tx.commit();return source.copyWithCount((int)extracted);}}
    public ItemStack distributeToCompartment(ItemStack stack,int compartment,boolean simulate){if(stack.isEmpty())return stack;try(var tx=Transaction.openOuter()){long inserted=insertCompartment(ItemVariant.of(stack),stack.getCount(),compartment,tx,true);if(!simulate)tx.commit();return stack.copyWithCount(stack.getCount()-(int)inserted);}}
    public ItemStack takeFromCompartment(int amount,int compartment,boolean simulate){checkCompartment(compartment);if(amount<=0)return ItemStack.EMPTY;ItemStack sample=ItemStack.EMPTY;for(int i=STACKS_PER_COMPARTMENT-1;i>=0;i--){sample=getStackInSlot(compartment*STACKS_PER_COMPARTMENT+i);if(!sample.isEmpty())break;}if(sample.isEmpty())return ItemStack.EMPTY;try(var tx=Transaction.openOuter()){long taken=extractCompartment(ItemVariant.of(sample),Math.min(amount,sample.getMaxStackSize()),compartment,tx);if(!simulate)tx.commit();return sample.copyWithCount((int)taken);}}
    public static ItemStack cleanItemNBT(ItemStack stack){if(stack.is(AllItems.BELT_CONNECTOR))stack.remove(AllDataComponents.BELT_FIRST_SHAFT);return stack;}
    public static boolean canItemsShareCompartment(ItemStack first,ItemStack second){if(!first.isStackable()&&!second.isStackable()&&first.isDamageableItem()&&second.isDamageableItem())return first.getItem()==second.getItem();if(first.is(AllItems.BELT_CONNECTOR)&&second.is(AllItems.BELT_CONNECTOR))return true;return ItemStack.isSameItemSameComponents(first,second);}
    public final class SlotStorage implements SingleSlotStorage<ItemVariant>{
        private final int index;private SlotStorage(int index){this.index=index;}
        @Override public boolean isResourceBlank(){return items.get(index).isEmpty();}
        @Override public ItemVariant getResource(){return ItemVariant.of(items.get(index));}
        @Override public long getAmount(){return items.get(index).getCount();}
        @Override public long getCapacity(){ItemStack stack=items.get(index);return stack.isEmpty()?64:stack.getMaxStackSize();}
        @Override public long getVersion(){return version;}
        @Override public long insert(ItemVariant variant,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,amount);ItemStack source=variant.toStack();if(amount==0||!isItemValid(index,source))return 0;ItemStack present=items.get(index);if(!present.isEmpty()&&!variant.matches(present))return 0;int moved=(int)Math.min(amount,source.getMaxStackSize()-present.getCount());if(moved<=0)return 0;snapshot(tx);if(present.isEmpty())items.set(index,variant.toStack(moved));else present.grow(moved);claim(index,source);return moved;}
        @Override public long extract(ItemVariant variant,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,amount);ItemStack present=items.get(index);if(amount==0||present.isEmpty()||!variant.matches(present))return 0;int moved=(int)Math.min(amount,present.getCount());snapshot(tx);present.shrink(moved);if(present.isEmpty())items.set(index,ItemStack.EMPTY);return moved;}
    }
    private Data data(){var sparse=new LinkedHashMap<Integer,ItemStack>();for(int i=0;i<SIZE;i++)if(!items.get(i).isEmpty())sparse.put(i,items.get(i).copy());return new Data(new Slots(sparse,SIZE),copies(filters));}
    private static DataResult<ToolboxInventory> decode(Data data){if(data.slots.items.keySet().stream().anyMatch(slot->slot>=data.slots.size))return DataResult.error(()->"Toolbox item lies outside declared sparse size");var inv=new ToolboxInventory();for(int c=0;c<COMPARTMENTS;c++)inv.filters.set(c,data.filters.get(c).copyWithCount(data.filters.get(c).isEmpty()?0:1));for(var entry:data.slots.items.entrySet()){ItemStack stack=entry.getValue();if(stack.getCount()>stack.getMaxStackSize()||!inv.isItemValid(entry.getKey(),stack))return DataResult.error(()->"Invalid toolbox compartment contents");inv.items.set(entry.getKey(),stack.copy());inv.claim(entry.getKey(),stack);}for(int c=0;c<COMPARTMENTS;c++)inv.settleRaw(c);return DataResult.success(inv);}
    private ItemContainerContents containerContents(){return ItemContainerContents.fromItems(copies(items));}
    private static ToolboxInventory fromContainerContents(ItemContainerContents contents){var inv=new ToolboxInventory();var stacks=contents.itemCopies().toList();if(stacks.size()>SIZE)throw new IllegalArgumentException("Oversized legacy toolbox");for(int i=0;i<stacks.size();i++)inv.setItem(i,stacks.get(i));return inv;}
    /** Real legacy Inventory/Items/Compartments decoding with registry-aware item codecs. */
    public static ToolboxInventory fromLegacy(HolderLookup.Provider registries,CompoundTag tag){return LEGACY_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();}
    @Override public boolean equals(Object object){if(!(object instanceof ToolboxInventory other))return false;for(int i=0;i<SIZE;i++)if(!ItemStack.matches(items.get(i),other.items.get(i)))return false;for(int i=0;i<COMPARTMENTS;i++)if(!ItemStack.matches(filters.get(i),other.filters.get(i)))return false;return true;}
    @Override public int hashCode(){int hash=1;for(ItemStack stack:items)hash=31*hash+ItemStack.hashItemAndComponents(stack)+stack.getCount();for(ItemStack stack:filters)hash=31*hash+ItemStack.hashItemAndComponents(stack);return hash;}
}
