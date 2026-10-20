package com.simibubi.create.content.trains.observer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Genuine world-owned source detector point, filter and passing original Train identity. */
public final class TrackObserver extends SingleBlockEntityEdgePoint {
  public static final Codec<TrackObserver> CODEC=RecordCodecBuilder.create(i->i.group(
      com.simibubi.create.content.trains.signal.TrackEdgePoint.baseCodec(TrackObserver::new).fieldOf("Point").forGetter(point->point),
      GlobalPos.CODEC.fieldOf("Owner").forGetter(TrackObserver::owner),
      Codec.intRange(0,8).fieldOf("Activated").forGetter(point->point.activated),
      ItemStack.OPTIONAL_CODEC.fieldOf("Filter").forGetter(point->point.filter.item()),
      UUIDUtil.CODEC.optionalFieldOf("TrainId").forGetter(point->Optional.ofNullable(point.currentTrain)))
      .apply(i,(point,owner,activated,filter,currentTrain)->{point.restoreOwner(owner);point.activated=activated;point.filter=FilterItemStack.of(filter);point.currentTrain=currentTrain.orElse(null);return point;}));
  private int activated;
  private FilterItemStack filter=FilterItemStack.empty();
  private UUID currentTrain;
  @Override public void blockEntityAdded(BlockEntity entity,boolean front){
    if(!(entity instanceof TrackObserverBlockEntity observer)||!id.equals(observer.targeting().id()))throw new IllegalArgumentException("Actual detector block/point identity required");
    super.blockEntityAdded(entity,front);setFilterAndNotify(entity.getLevel(),observer.getFilter());
  }
  @Override public void tick(TrackGraph graph,boolean preTrains){if(activated>0)activated--;if(activated==0)currentTrain=null;}
  public void setFilterAndNotify(Level level,ItemStack filter){
    if(ItemStack.isSameItemSameComponents(this.filter.item(),filter))return;
    this.filter=FilterItemStack.of(filter.copy());
    if(graph()!=null)graph().markDirty();
  }
  public FilterItemStack getFilter(){return filter;}
  public UUID getCurrentTrain(){return currentTrain;}
  public boolean isActivated(){return activated>0;}
  public void keepAlive(UUID actualTrain){activated=8;currentTrain=Objects.requireNonNull(actualTrain);}
  @Override public void read(CompoundTag tag,HolderLookup.Provider registries,boolean migration,DimensionPalette dimensions){
    super.read(tag,registries,migration,dimensions);
    activated=tag.getIntOr("Activated",0);if(activated<0||activated>8)throw new IllegalArgumentException("Invalid persisted native track observer pulse");
    filter=FilterItemStack.of(tag.read("Filter",ItemStack.OPTIONAL_CODEC,registries.createSerializationContext(NbtOps.INSTANCE)).orElse(ItemStack.EMPTY));
    currentTrain=tag.read("TrainId",UUIDUtil.CODEC).orElse(null);
  }
  @Override public void write(CompoundTag tag,HolderLookup.Provider registries,DimensionPalette dimensions){
    super.write(tag,registries,dimensions);tag.putInt("Activated",activated);tag.store("Filter",ItemStack.OPTIONAL_CODEC,registries.createSerializationContext(NbtOps.INSTANCE),filter.item());if(currentTrain!=null)tag.store("TrainId",UUIDUtil.CODEC,currentTrain);
  }
  @Override public void write(FriendlyByteBuf buffer,DimensionPalette dimensions){super.write(buffer,dimensions);buffer.writeNbt((CompoundTag)CODEC.encodeStart(buffer instanceof net.minecraft.network.RegistryFriendlyByteBuf nativeBuffer?nativeBuffer.registryAccess().createSerializationContext(NbtOps.INSTANCE):NbtOps.INSTANCE,this).getOrThrow());}
  @Override public void read(FriendlyByteBuf buffer,DimensionPalette dimensions){
    super.read(buffer,dimensions);var encoded=buffer.readNbt(NbtAccounter.create(1048576));if(!(encoded instanceof CompoundTag compound))throw new IllegalArgumentException("Actual observer payload requires a compound");
    var decoded=CODEC.parse(buffer instanceof net.minecraft.network.RegistryFriendlyByteBuf nativeBuffer?nativeBuffer.registryAccess().createSerializationContext(NbtOps.INSTANCE):NbtOps.INSTANCE,compound).getOrThrow();
    if(!id.equals(decoded.id)||position!=decoded.position||(!edgeLocation.getFirst().equals(decoded.edgeLocation.getFirst())||!edgeLocation.getSecond().equals(decoded.edgeLocation.getSecond())))throw new IllegalArgumentException("Native observer payload geometry changed");
    restoreOwner(decoded.owner());activated=decoded.activated;filter=decoded.filter;currentTrain=decoded.currentTrain;
  }
}
