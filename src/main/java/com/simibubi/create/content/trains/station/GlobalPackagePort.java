package com.simibubi.create.content.trains.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/** Real retained station mail owner; a loaded bound Postbox is the sole live inventory authority. */
public final class GlobalPackagePort extends SnapshotParticipant<List<ItemStack>> {
  public static final int SLOTS=18;
  public static final Codec<GlobalPackagePort> CODEC=RecordCodecBuilder.create(i->i.group(
      Codec.STRING.fieldOf("Address").forGetter(p->p.address),
      ItemStack.OPTIONAL_CODEC.listOf().fieldOf("OfflineBuffer").forGetter(GlobalPackagePort::items),
      Codec.BOOL.fieldOf("Primed").forGetter(p->p.primed)).apply(i,GlobalPackagePort::new));
  private String address;
  private final List<ItemStack> offlineBuffer=new ArrayList<>();
  private boolean primed,restoring;
  private GlobalStation station;
  private long version;
  private final Storage<ItemVariant> storage=new Storage<>(){
    @Override public long insert(ItemVariant resource,long amount,TransactionContext transaction){
      StoragePreconditions.notBlankNotNegative(resource,amount);
      if(amount==0||!PackageItem.isPackage(resource.toStack(1)))return 0;
      long inserted=0;
      for(int slot=0;slot<SLOTS&&inserted<amount;slot++)if(offlineBuffer.get(slot).isEmpty()){
        updateSnapshots(transaction);offlineBuffer.set(slot,resource.toStack(1));inserted++;
      }
      return inserted;
    }
    @Override public long extract(ItemVariant resource,long amount,TransactionContext transaction){
      StoragePreconditions.notBlankNotNegative(resource,amount);long extracted=0;
      for(int slot=0;slot<SLOTS&&extracted<amount;slot++)if(!offlineBuffer.get(slot).isEmpty()&&ItemVariant.of(offlineBuffer.get(slot)).equals(resource)){
        int count=(int)Math.min(amount-extracted,offlineBuffer.get(slot).getCount());
        updateSnapshots(transaction);offlineBuffer.get(slot).shrink(count);if(offlineBuffer.get(slot).isEmpty())offlineBuffer.set(slot,ItemStack.EMPTY);extracted+=count;
      }
      return extracted;
    }
    @Override public long getVersion(){return version;}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){
      var views=new ArrayList<StorageView<ItemVariant>>();
      for(int slot=0;slot<SLOTS;slot++){
        final int index=slot;
        views.add(new StorageView<>(){
          @Override public boolean isResourceBlank(){return offlineBuffer.get(index).isEmpty();}
          @Override public ItemVariant getResource(){return ItemVariant.of(offlineBuffer.get(index));}
          @Override public long getAmount(){return offlineBuffer.get(index).getCount();}
          @Override public long getCapacity(){return Math.max(1,getAmount());}
          @Override public long extract(ItemVariant resource,long amount,TransactionContext transaction){
            StoragePreconditions.notBlankNotNegative(resource,amount);
            if(amount==0||isResourceBlank()||!getResource().equals(resource))return 0;
            int count=(int)Math.min(amount,getAmount());updateSnapshots(transaction);offlineBuffer.get(index).shrink(count);if(offlineBuffer.get(index).isEmpty())offlineBuffer.set(index,ItemStack.EMPTY);return count;
          }
        });
      }
      return views.iterator();
    }
  };
  public GlobalPackagePort(){this("",Collections.nCopies(SLOTS,ItemStack.EMPTY),false);}
  public GlobalPackagePort(String address,List<ItemStack> items,boolean primed){
    this.address=Objects.requireNonNull(address);this.primed=primed;
    offlineBuffer.addAll(checked(items));
  }
  private static List<ItemStack> checked(List<ItemStack> items){
    if(items.size()!=SLOTS)throw new IllegalArgumentException("Station port must own18actual native slots");
    var checked=new ArrayList<ItemStack>();
    // Persisted source buffers predate parcel-only insertion and can own arbitrary original
    // item stacks. Preserve their full counts/components; only NEW insertion is parcel-only.
    for(var item:items){Objects.requireNonNull(item);checked.add(item.copy());}
    return checked;
  }
  void bind(GlobalStation station){if(this.station!=null&&this.station!=station)throw new IllegalArgumentException("Mail buffer already belongs to another actual station");this.station=Objects.requireNonNull(station);}
  public String address(){return address;}
  public boolean primed(){return primed;}
  public List<ItemStack> items(){return offlineBuffer.stream().map(ItemStack::copy).toList();}
  public Storage<ItemVariant> storage(){return storage;}
  public void prime(){primed=true;changed();}
  void configureAddress(String address){this.address=Objects.requireNonNull(address);changed();}
  public void saveOfflineBuffer(List<ItemStack> inventory){
    if(restoring)return;
    var actual=checked(inventory);boolean same=true;
    for(int slot=0;slot<SLOTS;slot++)if(!ItemStack.matches(actual.get(slot),offlineBuffer.get(slot))){same=false;break;}
    if(same)return;
    for(int slot=0;slot<SLOTS;slot++)offlineBuffer.set(slot,actual.get(slot));
    changed();
  }
  void restoreOfflineBuffer(PostboxBlockEntity box){
    if(!primed)return;
    if(station==null||!box.isBoundTo(station))throw new IllegalArgumentException("Actual bound postbox required for retained mail handoff");
    restoring=true;
    try{box.replaceMailContents(items());primed=false;changed();}finally{restoring=false;}
  }
  public GlobalPackagePort copy(){return new GlobalPackagePort(address,items(),primed);}
  @Override protected List<ItemStack> createSnapshot(){return items();}
  @Override protected void readSnapshot(List<ItemStack> snapshot){for(int slot=0;slot<SLOTS;slot++)offlineBuffer.set(slot,snapshot.get(slot));}
  @Override protected void onFinalCommit(){changed();}
  private void changed(){version++;if(station!=null&&station.graph()!=null)station.graph().markDirty();}
}
