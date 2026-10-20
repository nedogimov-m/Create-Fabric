package com.simibubi.create.content.trains.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Actual world-owned station graph point. No reservation exists until real Train custody exists.
 */
public final class GlobalStation extends SingleBlockEntityEdgePoint {
  public record Port(BlockPos pos, GlobalPackagePort data) {
    public static final Codec<Port> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        BlockPos.CODEC.fieldOf("Pos").forGetter(Port::pos),
                        GlobalPackagePort.CODEC.fieldOf("Data").forGetter(Port::data))
                    .apply(i, Port::new));

    public Port {
      pos = pos.immutable();
      data = data.copy();
    }
  }

  public static final Codec<GlobalStation> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      TrackEdgePoint.baseCodec(GlobalStation::new)
                          .fieldOf("Point")
                          .forGetter(p -> p),
                      GlobalPos.CODEC.fieldOf("Owner").forGetter(GlobalStation::owner),
                      Codec.string(0, 256).fieldOf("Name").forGetter(p -> p.name),
                      Codec.BOOL.fieldOf("Assembling").forGetter(p -> p.assembling),
                      Port.CODEC.listOf().fieldOf("Ports").forGetter(GlobalStation::ports))
                  .apply(
                      i,
                      (point, owner, name, assembling, ports) -> {
                        point.restoreOwner(owner);
                        point.name = name;
                        point.assembling = assembling;
                        point.restorePorts(ports);
                        return point;
                      }));
  private java.lang.ref.WeakReference<com.simibubi.create.content.trains.entity.Train> nearestTrain=new java.lang.ref.WeakReference<>(null);
  private String name = "Track Station";
  private boolean assembling;
  private final Map<BlockPos, GlobalPackagePort> connectedPorts = new LinkedHashMap<>();

  public String name() {
    return name;
  }

  public boolean assembling() {
    return assembling;
  }

  public List<Port> ports() {
    return connectedPorts.entrySet().stream().map(e -> new Port(e.getKey(), e.getValue())).toList();
  }

  private void restorePorts(List<Port> ports) {
    var fresh = new LinkedHashMap<BlockPos, GlobalPackagePort>();
    for (var port : ports)
      if (fresh.putIfAbsent(port.pos, port.data.copy()) != null)
        throw new IllegalArgumentException("Duplicate actual station port position");
    connectedPorts.clear();
    connectedPorts.putAll(fresh);
    connectedPorts.values().forEach(port->port.bind(this));
  }

  public boolean ownedBy(StationBlockEntity station) {
    return station.getLevel() != null
        && owner().equals(GlobalPos.of(station.getLevel().dimension(), station.getBlockPos()))
        && id.equals(station.targeting().id());
  }

  public void configure(StationBlockEntity station, String name, boolean assembling) {
    if (!ownedBy(station)) throw new IllegalArgumentException("Foreign native station point owner");
    if (name.length() > 256) throw new IllegalArgumentException("Station name too long");
    if (this.name.equals(name) && this.assembling == assembling) return;
    this.name = name;
    this.assembling = assembling;
    if (graph() != null) graph().markDirty();
  }

  @Override
  public void blockEntityAdded(BlockEntity entity, boolean front) {
    if (!(entity instanceof StationBlockEntity station) || !id.equals(station.targeting().id()))
      throw new IllegalArgumentException("Actual station BE/point ID required");
    super.blockEntityAdded(entity, front);
    assembling = station.isAssembling();
  }

  public boolean canApproachFrom(TrackNode node) {
    return isPrimary(node) && !assembling;
  }

  @Override
  public boolean canNavigateVia(TrackNode node) {
    return !assembling;
  }

  public void reserveFor(com.simibubi.create.content.trains.entity.Train train){
    requireActualTrain(train);
    if(train.isRestoring())return;
    var nearest=getNearestTrain();
    if(nearest==null||nearest.navigation.distanceToDestination>train.navigation.distanceToDestination)
      nearestTrain=new java.lang.ref.WeakReference<>(train);
  }

  private void requireActualTrain(com.simibubi.create.content.trains.entity.Train train){
    if(graph()==null||graph().manager()==null||train.graph!=graph()||train.trafficManager()==null
        ||train.trafficManager().get(train.id)!=train||train.trafficManager().server()!=graph().manager().server())
      throw new IllegalArgumentException("Native station reservation requires its exact real server Train");
  }

  public void cancelReservation(com.simibubi.create.content.trains.entity.Train train){
    if(nearestTrain.get()==train)nearestTrain=new java.lang.ref.WeakReference<>(null);
  }
  public void trainDeparted(com.simibubi.create.content.trains.entity.Train train){cancelReservation(train);}
  public com.simibubi.create.content.trains.entity.Train getNearestTrain(){
    var train=nearestTrain.get();
    if(train==null)return null;
    if(train.isRestoring()||train.trafficManager()==null||train.trafficManager().get(train.id)!=train||train.graph!=graph()){
      nearestTrain=new java.lang.ref.WeakReference<>(null);
      return null;
    }
    return train;
  }
  public com.simibubi.create.content.trains.entity.Train getPresentTrain(){
    var train=getNearestTrain();
    return train!=null&&train.getCurrentStation()==this?train:null;
  }
  public com.simibubi.create.content.trains.entity.Train getImminentTrain(){
    var train=getNearestTrain();
    if(train==null||train.getCurrentStation()==this)return train;
    return train.navigation.isActive()&&train.navigation.distanceToDestination<=30?train:null;
  }

  public void attachPackagePort(com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity box){
    if(box.getLevel()==null||box.getLevel().isClientSide()||!box.getLevel().dimension().equals(getBlockEntityDimension())
        ||!box.targets(this))throw new IllegalArgumentException("Actual native postbox/station target required");
    var port=connectedPorts.get(box.getBlockPos());
    if(port==null){port=new GlobalPackagePort(box.addressFilter,box.items(),false);connectedPorts.put(box.getBlockPos(),port);if(graph()!=null)graph().markDirty();}
    port.bind(this);
    box.bind(this);
    port.restoreOfflineBuffer(box);
    if(!port.address().equals(box.addressFilter))port.configureAddress(box.addressFilter);
  }

  public void removePackagePort(com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity box){
    if(!box.targets(this))return;
    var removed=connectedPorts.remove(box.getBlockPos());
    if(removed!=null&&graph()!=null)graph().markDirty();
    box.unbind(this);
  }
  public void savePostboxBuffer(com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity box){
    var port=connectedPorts.get(box.getBlockPos());
    if(port==null||!box.targets(this)||box.getLevel()==null||!box.getLevel().dimension().equals(getBlockEntityDimension()))return;
    port.saveOfflineBuffer(box.items());
  }

  public void runMailTransfer(){
    var train=getPresentTrain();
    if(train==null||connectedPorts.isEmpty())return;
    requireActualTrain(train);
    var world=train.manager().server().getLevel(getBlockEntityDimension());
    for(var carriage:train.carriages){
      var cargo=carriage.storage.getAllItems();
      if(cargo==null)continue;
      for(var entry:connectedPorts.entrySet()){
        var port=entry.getValue();
        var found=world==null?null:com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(world,entry.getKey());
        var box=found instanceof com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity actual&&actual.isBoundTo(this)?actual:null;
        var inventory=box==null?port.storage():box.inventory.storage;
        boolean sent=transferPackages(inventory,cargo,item->!com.simibubi.create.content.logistics.box.PackageItem.matchAddress(item,port.address()));
        boolean received=transferPackages(cargo,inventory,item->com.simibubi.create.content.logistics.box.PackageItem.matchAddress(item,port.address()));
        if(!sent&&!received)continue;
        if(box==null)port.prime();else box.spawnParticles();
        graph().markDirty();
      }
    }
  }

  private static boolean transferPackages(net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> source,
      net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> destination,
      java.util.function.Predicate<net.minecraft.world.item.ItemStack> predicate){
    boolean moved=false;
    for(var slot:source){
      if(slot.isResourceBlank()||slot.getAmount()<1)continue;
      var item=slot.getResource();
      if(!com.simibubi.create.content.logistics.box.PackageItem.isPackage(item.toStack(1))||!predicate.test(item.toStack(1)))continue;
      try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
        if(slot.extract(item,1,transaction)!=1||destination.insert(item,1,transaction)!=1)continue;
        transaction.commit();moved=true;
      }
    }
    return moved;
  }

  @Override public void tick(TrackGraph graph,boolean preTrains){}


  @Override
  public void read(
      CompoundTag tag,
      HolderLookup.Provider registries,
      boolean migration,
      DimensionPalette dimensions) {
    super.read(tag, registries, migration, dimensions);
    name = tag.getStringOr("Name", "Track Station");
    assembling = tag.getBooleanOr("Assembling", false);
    restorePorts(
        tag.contains("Ports")
            ? tag.read(
                    "Ports",
                    Port.CODEC.listOf(),
                    registries.createSerializationContext(NbtOps.INSTANCE))
                .orElseThrow()
            : List.of());
  }

  @Override
  public void write(
      CompoundTag tag, HolderLookup.Provider registries, DimensionPalette dimensions) {
    super.write(tag, registries, dimensions);
    tag.putString("Name", name);
    tag.putBoolean("Assembling", assembling);
    tag.store(
        "Ports",
        Port.CODEC.listOf(),
        registries.createSerializationContext(NbtOps.INSTANCE),
        ports());
  }
}
