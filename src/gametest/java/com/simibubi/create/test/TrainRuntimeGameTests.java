package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.roller.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

/** Proposed actual station-created Train custody, native persistence, public payload and real mail regressions. */
public final class TrainRuntimeGameTests {
  private static final String STRUCTURE="create_test:reservoir",MARKER="TRAIN_PRIVATE_CARGO_91";
  private static final BlockPos R=new BlockPos(3,6,4),T=R.east(),S=T.south(2),B=T.east().above(),C=B.east(),I=C.east();
  private record Fixture(StationBlockEntity station,Player player,BarrelBlockEntity barrel,TrainManager traffic){}
  private static ItemStack cargo(){var item=new ItemStack(Items.DIAMOND,7);item.set(DataComponents.CUSTOM_NAME,Component.literal(MARKER));return item;}
  private static void glue(GameTestHelper h,BlockPos from,BlockPos to){h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(from),h.absolutePos(to)))),"Actual source glue is accepted by the native world");}
  private static Fixture fixture(GameTestHelper h,boolean second,boolean joined,boolean forward){
    var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
    for(int i=0;i<25;i++){h.setBlock(R.east(i).below(),Blocks.STONE);h.setBlock(R.east(i),rail);}
    for(int i=0;i<25;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(R.east(i)),rail,false).complete(),"Actual physical source rail discovery completes");
    h.setBlock(S,AllBlocks.TRACK_STATION);var station=h.getBlockEntity(S,StationBlockEntity.class);
    h.assertTrue(station.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(T)),true,new Vec3(1,0,0),Optional.empty())),"Actual native station owns its true source graph point");
    var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(station.getBlockPos()).add(0,0,1));
    h.assertTrue(station.configure(player,"Actual Train Station",true,DoorControl.ALL),"Actual source assembly mode is authorized");
    h.setBlock(B,AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X));
    h.setBlock(C,AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING,forward?Direction.EAST:Direction.WEST));
    h.setBlock(I,Blocks.BARREL);var barrel=h.getBlockEntity(I,BarrelBlockEntity.class);barrel.setItem(0,cargo());glue(h,B,I);
    if(second){
      h.setBlock(B.east(6),AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X));
      h.setBlock(B.east(7),Blocks.BARREL);
      if(joined){
        // Glue connects touching source faces; a spanning AABB cannot bridge three air cells.
        for(int bridge=3;bridge<=5;bridge++)h.setBlock(B.east(bridge),Blocks.OAK_PLANKS);
        glue(h,B,B.east(7));
      }
    }
    return new Fixture(station,player,barrel,TrainManager.get(h.getLevel()));
  }
  private static long cargoCount(Train train,ItemVariant variant){long count=0;for(var carriage:train.carriages)for(var slot:carriage.storage.getAllItems())if(slot.getResource().equals(variant))count+=slot.getAmount();return count;}
  private static void assembled(GameTestHelper h,Fixture fixture,BiConsumer<Fixture,Train> assertion){assembled(h,fixture,0,assertion);}
  private static void assembled(GameTestHelper h,Fixture fixture,int delayTicks,BiConsumer<Fixture,Train> assertion){assembled(h,fixture,delayTicks,(actual,train)->{},assertion);}
  private static void assembled(GameTestHelper h,Fixture fixture,int delayTicks,BiConsumer<Fixture,Train> beforeWait,BiConsumer<Fixture,Train> assertion){
    var created=new AtomicReference<Train>();var checked=new AtomicBoolean();var createdAt=new AtomicLong();
    h.succeedWhen(()->{
      var train=created.get();
      if(train==null){
        var preflight=StationAssemblyPreflight.check(fixture.station,fixture.player);
        h.assertTrue(preflight.readyForCapture(),"Genuine native entity/source wheel readiness: "+preflight.status());
        h.assertTrue(fixture.station.tryAssemble(fixture.player),"Actual station must complete full native capture/adoption: "+fixture.station.trainAssemblyStatus());
        train=fixture.station.getStation().getPresentTrain();
        h.assertTrue(train!=null&&fixture.traffic.get(train.id)==train,"One actual Train enters its genuine server registry after full transfer");created.set(train);createdAt.set(h.getLevel().getGameTime());beforeWait.accept(fixture,train);
      }
      if(!checked.get())h.assertTrue(h.getLevel().getGameTime()-createdAt.get()>=delayTicks,"Actual native server traffic ticks must complete before their state is asserted");
      if(!checked.get()){assertion.accept(fixture,train);checked.set(true);}
      if(fixture.traffic.get(train.id)==null)return;
      if(!train.isRestoring()){
        h.assertTrue(train.canDisassemble(),"Actual accepted native carriage fronts are flat and ready for original-world restoration");
        train.disassemble(Direction.EAST,h.absolutePos(T.above()));
      }
      h.assertTrue(fixture.traffic.get(train.id)==null,"Actual source restoration completes before the test releases global Train custody");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualStationCreatesOneTrainWithTheOriginalNativeCargoOwner(GameTestHelper h){
    assembled(h,fixture(h,false,false,true),(f,train)->{
      h.assertTrue(!f.station.isAssembling()&&train.carriages.size()==1&&train.carriageSpacing.isEmpty(),"Native user assembly leaves source mode with the actual source formation");
      var carriage=train.carriages.getFirst();var front=carriage.anyAvailableEntity();
      h.assertTrue(front!=null&&h.getLevel().getEntity(front.getUUID())==front&&front.getCarriage()==carriage&&front.getContraption().getStorage()==carriage.storage&&carriage.storage.ownsContents(),"Native accepted frontend and globally owned row share exactly one actual mounted manager");
      h.assertTrue(carriage.custodyIdentities().bodies().equals(Set.of(front.getUUID()))&&cargoCount(train,ItemVariant.of(cargo()))==7,"Original real body UUID and named cargo are retained exactly once");
      h.assertTrue(LoadedLevelAccess.blockEntity(h.getLevel(),h.absolutePos(I))==null&&f.barrel.isRemoved(),"The actual source barrel has retired after genuine quiet world transfer");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualIndependentCarriagesKeepTheirSourceCouplingSpacing(GameTestHelper h){
    assembled(h,fixture(h,true,false,true),(f,train)->{h.assertTrue(train.carriages.size()==2&&train.carriageSpacing.equals(List.of(6))&&train.carriages.stream().allMatch(carriage->!carriage.isOnTwoBogeys()),"Two actual separate source roots produce two real single-bogey rows with original spacing6");f.traffic.validateCustody();});
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualJoinedBogeysCreateOneRealTwoBogeyCarriage(GameTestHelper h){
    assembled(h,fixture(h,true,true,true),(f,train)->{var carriage=train.carriages.getFirst();h.assertTrue(train.carriages.size()==1&&train.carriageSpacing.isEmpty()&&carriage.isOnTwoBogeys()&&carriage.bogeySpacing==6,"The actual glued two-bogey body keeps its source order and real spacing");h.assertTrue(carriage.getLeadingPoint().resolve(train.graph)&&carriage.getTrailingPoint().resolve(train.graph),"Both real native endpoint wheels resolve on their authoritative graph");});
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void missingForwardControlsRefuseBeforeAnyOriginalSourceOwnershipChanges(GameTestHelper h){
    var f=fixture(h,false,false,false);var original=f.barrel;var before=new HashSet<>(f.traffic.trains().keySet());
    h.succeedWhen(()->{
      h.assertTrue(StationAssemblyPreflight.check(f.station,f.player).readyForCapture(),"Actual source wheel metadata must be ready before checking real carriage controls");
      h.assertFalse(f.station.tryAssemble(f.player),"Actual backward-only source control cannot authorize a forward train");
      h.assertTrue(f.station.trainAssemblyFailure()!=null&&f.station.trainAssemblyFailure().component.equals(Component.translatable("create.train_assembly.no_controls"))&&f.traffic.trains().keySet().equals(before),"Full capture refuses the real source formation without publishing an incomplete Train");
      h.assertTrue(h.getBlockEntity(I,BarrelBlockEntity.class)==original&&!original.isRemoved()&&ItemStack.matches(original.getItem(0),cargo())&&h.getBlockState(B).is(AllBlocks.SMALL_BOGEY),"The exact original native barrel, bogey and named cargo remain world-owned");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void publicTrainPayloadContainsRealBogeysAndNoPrivateBodyCargoOrPassengers(GameTestHelper h){
    assembled(h,fixture(h,false,false,true),(f,train)->{
      var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
      try{
        Train.STREAM_CODEC.encode(buffer,train);var raw=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),raw);
        h.assertFalse(new String(raw,java.nio.charset.StandardCharsets.ISO_8859_1).contains(MARKER),"Real public native bytes omit private cargo components");
        var publicTrain=Train.STREAM_CODEC.decode(buffer);h.assertTrue(buffer.readableBytes()==0&&publicTrain.id.equals(train.id)&&publicTrain.carriages.size()==train.carriages.size(),"Actual native source description keeps exact public Train/bogey identity");
        var description=publicTrain.carriages.getFirst();h.assertTrue(!description.storage.ownsContents()&&description.custodyIdentities().bodies().isEmpty()&&description.custodyIdentities().passengers().isEmpty()&&cargoCount(publicTrain,ItemVariant.of(cargo()))==0,"Decoded public row has no server cargo, original body or passengers");
        publicTrain.id=UUID.randomUUID();boolean refused=false;try{f.traffic.add(publicTrain);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused&&publicTrain.trafficManager()==null,"A different UUID cannot promote actual public metadata into authoritative native Train custody");
      }finally{buffer.release();}
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void duplicatePersistedBodyCannotEnterAnotherActualNativeTrain(GameTestHelper h){
    assembled(h,fixture(h,false,false,true),(f,train)->{
      var dimensions=new DimensionPalette();var tag=train.write(dimensions,h.getLevel().registryAccess());tag.store("Id",UUIDUtil.CODEC,UUID.randomUUID());
      var other=Train.read(tag,h.getLevel().registryAccess(),RailwayGraphManager.get(h.getLevel()).getTrackNetworks(),dimensions);
      boolean refused=false;try{f.traffic.add(other);}catch(IllegalArgumentException expected){refused=true;}
      h.assertTrue(refused&&other.trafficManager()==null&&f.traffic.get(train.id)==train&&cargoCount(train,ItemVariant.of(cargo()))==7,"Another actual decoded row with the original body UUID is rejected atomically before publication");
      boolean stolen=false;try{new Train(UUID.randomUUID(),null,train.graph,train.carriages,train.carriageSpacing,false,0);}catch(IllegalArgumentException expected){stolen=true;}h.assertTrue(stolen&&train.carriages.getFirst().train==train,"Source constructor preflight cannot steal any already owned real Carriage");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void genuineNativeSavedDataCodecRetainsOneCargoRecordAndThinPublicFrontend(GameTestHelper h){
    assembled(h,fixture(h,false,false,true),(f,train)->{
      var dimensions=new DimensionPalette();var saved=train.write(dimensions,h.getLevel().registryAccess());var row=(CompoundTag)saved.getListOrEmpty("Carriages").getFirst();
      h.assertTrue(row.contains("Cargo")&&row.getCompoundOrEmpty("Cargo").toString().contains(MARKER),"Actual global carriage row persists the sole original named cargo");
      var body=row.getCompoundOrEmpty("Entity").getCompoundOrEmpty("Contraption");h.assertTrue(!body.contains("items")&&!body.contains("fluids")&&!body.contains("NativeStandaloneCargo"),"True managed frontend persistence never duplicates the native independent fallback or global cargo");
      var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var encoded=TrainTrafficSavedData.CODEC.encodeStart(ops,TrainTrafficSavedData.load(h.getLevel().getServer())).getOrThrow();var reopened=TrainTrafficSavedData.CODEC.parse(ops,encoded).getOrThrow();
      h.assertTrue(TrainTrafficSavedData.CODEC.encodeStart(ops,reopened).getOrThrow().equals(encoded),"Actual unopened native SavedData codec preserves every full source row without fake consumer hydration");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualPostboxAndTrainTransferTheSameOriginalParcelTransactionally(GameTestHelper h){
    assembled(h,fixture(h,false,false,true),(f,train)->{
      var pos=S.south().below();h.setBlock(pos,AllBlocks.POSTBOXES.get(DyeColor.WHITE));var box=h.getBlockEntity(pos,PostboxBlockEntity.class);box.addressFilter="Home";
      h.assertTrue(box.setTarget(new PackagePortTarget.TrainStationFrogportTarget(f.station.getBlockPos().subtract(box.getBlockPos())))&&box.isBoundTo(f.station.getStation()),"A real native mailbox is bound to the actual Train station");
      var parcel=PackageItem.containing(List.of(cargo()));PackageItem.addAddress(parcel,"Away");box.inventory.setItem(0,parcel);
      f.station.getStation().runMailTransfer();
      h.assertTrue(box.inventory.isEmpty()&&cargoCount(train,ItemVariant.of(parcel))==1,"Original addressed parcel moves from the real mailbox into actual Train cargo exactly once");
      box.addressFilter="Away";box.filterChanged();f.station.getStation().runMailTransfer();
      h.assertTrue(cargoCount(train,ItemVariant.of(parcel))==0&&box.items().stream().filter(item->ItemStack.matches(item,parcel)).count()==1,"Changing the actual destination transfers that same real parcel back through the native mailbox owner");
      var retained=f.station.getStation().ports().stream().filter(port->port.pos().equals(box.getBlockPos())).findFirst().orElseThrow().data();h.assertTrue(retained.items().stream().filter(item->ItemStack.matches(item,parcel)).count()==1&&!retained.primed(),"Loaded original mailbox and its genuine retained mirror agree without an offline replay");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void nativePostboxKeepsItsStationAddressWhenConveyorAcceptanceIsDisabled(GameTestHelper h){
    assembled(h,fixture(h,false,false,true),(f,train)->{
      var pos=S.south().below();h.setBlock(pos,AllBlocks.POSTBOXES.get(DyeColor.WHITE));
      var box=h.getBlockEntity(pos,PostboxBlockEntity.class);box.addressFilter="Home";box.acceptsPackages=false;
      h.assertTrue(box.getFilterString()==null&&box.setTarget(new PackagePortTarget.TrainStationFrogportTarget(f.station.getBlockPos().subtract(box.getBlockPos()))),"Actual conveyor acceptance is disabled while the native Postbox still owns its station target");
      var port=f.station.getStation().ports().stream().filter(entry->entry.pos().equals(box.getBlockPos())).findFirst().orElseThrow().data();
      h.assertTrue(port.address().equals("Home"),"Source station registration keeps the original addressFilter independently of the conveyor routing filter");
      var retained=PackageItem.containing(List.of(cargo()));PackageItem.addAddress(retained,"Home");
      var outgoing=PackageItem.containing(List.of(cargo()));PackageItem.addAddress(outgoing,"Away");
      box.inventory.setItem(0,retained);box.inventory.setItem(1,outgoing);f.station.getStation().runMailTransfer();
      h.assertTrue(ItemStack.matches(box.inventory.getItem(0),retained)&&cargoCount(train,ItemVariant.of(retained))==0&&cargoCount(train,ItemVariant.of(outgoing))==1,"The actual home parcel stays in its original Postbox while the foreign parcel moves into real Train custody once");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualSignalTrafficSeesTheRealTrainAndTheOppositePhysicalPartition(GameTestHelper h){
    var f=fixture(h,false,false,true);var first=S.north(5);var second=first.east();
    h.setBlock(first,AllBlocks.TRACK_SIGNAL);h.setBlock(second,AllBlocks.TRACK_SIGNAL);
    var a=h.getBlockEntity(first,com.simibubi.create.content.trains.signal.SignalBlockEntity.class);
    var b=h.getBlockEntity(second,com.simibubi.create.content.trains.signal.SignalBlockEntity.class);
    var target=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(T.east(6)));
    h.assertTrue(a.setTarget(new TrackTargetingSelection(target,true,new Vec3(1,0,0),Optional.empty()))&&b.setTarget(new TrackTargetingSelection(target,false,new Vec3(1,0,0),Optional.empty())),"Two actual native signal owners share opposite sides of one physical boundary");
    assembled(h,f,3,(actual,train)->{
      var boundary=a.getSignal();h.assertTrue(boundary!=null&&b.getSignal()==boundary,"Actual shared boundary identity remains stable through Train creation");
      boolean trainSide=train.occupiedSignalBlocks.containsKey(boundary.group(true));
      h.assertTrue(train.occupiedSignalBlocks.containsKey(boundary.group(trainSide))&&!train.occupiedSignalBlocks.containsKey(boundary.group(!trainSide)),"The actual native Train spans exactly one of these genuine physical partitions");
      var group=actual.traffic.signalEdgeGroups().get(boundary.group(trainSide));
      h.assertTrue(group!=null&&group.trains.contains(train)&&group.hasResolvedTopology(),"Current real server frame published original Train occupancy into its actual signal group");
      var occupiedOwner=boundary.owners(trainSide).keySet().iterator().next();var freeOwner=boundary.owners(!trainSide).keySet().iterator().next();
      h.assertTrue(boundary.getStateFor(occupiedOwner)==com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState.RED&&boundary.getStateFor(freeOwner)==com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState.GREEN,"Actual current-frame traffic makes only the occupied physical side red while the genuine empty opposite side is green");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualTrackObserverFiltersOriginalTrainCargoWithoutACommittedExchange(GameTestHelper h){
    var f=fixture(h,false,false,true);var at=S.north(5);h.setBlock(at,AllBlocks.TRACK_OBSERVER);
    var owner=h.getBlockEntity(at,com.simibubi.create.content.trains.observer.TrackObserverBlockEntity.class);
    owner.filtering.setFilter(new ItemStack(Items.DIAMOND));
    h.assertTrue(owner.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(T.east())),true,new Vec3(1,0,0),Optional.empty())),"Actual detector targets the physical interval spanned by the source bogey wheels");
    var originalVersion=new AtomicInteger();
    assembled(h,f,3,(actual,train)->{
      var cargo=train.carriages.getFirst().storage;
      for(int tick=0;tick<4;tick++)cargo.tickIdleCargoTracker();
      originalVersion.set(cargo.getVersion());
    },(actual,train)->{
      var detector=owner.getObserver();h.assertTrue(detector!=null&&detector.isActivated()&&train.id.equals(detector.getCurrentTrain())&&owner.isBlockPowered(),"Native source detector is activated by this exact real Train and powers its actual block");
      h.assertTrue(cargoCount(train,ItemVariant.of(cargo()))==7&&train.carriages.getFirst().storage.getTicksSinceLastExchange()==4&&train.carriages.getFirst().storage.getVersion()==originalVersion.get(),"The genuine filter scans original mounted cargo through aborted transactions without exchange or idle-reset side effects");
    });
  }

  private static void relocationTrack(GameTestHelper h,BlockPos start,int length){
    var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
    for(int i=0;i<length;i++){h.setBlock(start.east(i).below(),Blocks.STONE);h.setBlock(start.east(i),rail);}
    for(int i=0;i<length;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(start.east(i)),rail,false).complete(),"Actual relocation destination uses loaded physical source rails");
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualRelocationMovesTheSameNativeBodyCargoAndSeatedCowWithoutPreviewMutation(GameTestHelper h){
    var f=fixture(h,false,false,true);var destination=R.south(4);relocationTrack(h,destination,25);
    h.setBlock(I.above(),AllBlocks.SEATS.get(DyeColor.BLUE));glue(h,B,I.above());
    var originalFront=new AtomicReference<CarriageContraptionEntity>();var originalCargo=new AtomicReference<Object>();
    var originalPosition=new AtomicReference<Vec3>();var passenger=new AtomicReference<net.minecraft.world.entity.animal.cow.Cow>();
    assembled(h,f,3,(actual,train)->{
      var carriage=train.carriages.getFirst();var front=carriage.anyAvailableEntity();
      h.assertTrue(front!=null&&front.getContraption().getSeats().size()==1,"Actual source seat was captured in the owned native carriage");
      var cow=h.spawn(net.minecraft.world.entity.EntityTypes.COW,I.above(2));
      h.assertTrue(front.addSittingPassenger(cow,0)&&cow.getVehicle()==front,"Actual accepted world cow occupies the actual captured seat");
      passenger.set(cow);originalFront.set(front);originalCargo.set(carriage.storage);originalPosition.set(front.position());
      var oldGraph=train.graph;var oldStation=train.currentStation;
      var oldPoints=new ArrayList<Double>();train.forEachTravellingPoint(point->oldPoints.add(point.position));
      var preview=TrainRelocator.preview(train,h.getLevel(),h.absolutePos(destination.east(12)),null,false,new Vec3(1,0,0));
      var afterPoints=new ArrayList<Double>();train.forEachTravellingPoint(point->afterPoints.add(point.position));
      h.assertTrue(preview.valid()&&train.graph==oldGraph&&Objects.equals(oldStation,train.currentStation)&&oldPoints.equals(afterPoints)&&cargoCount(train,ItemVariant.of(cargo()))==7&&cow.getVehicle()==front,"True relocation preview leaves original wheels/station/cargo/passenger owners unchanged");
      h.assertTrue(TrainRelocator.relocate(train,h.getLevel(),h.absolutePos(destination.east(12)),null,false,new Vec3(1,0,0),false),"Actual source wheel plan commits on the real target graph");
      h.assertTrue(train.graph!=oldGraph&&train.currentStation==null&&train.speed==0&&!train.derailed,"Actual relocation changes graph and releases the original station");
    },(actual,train)->{
      var carriage=train.carriages.getFirst();var front=carriage.anyAvailableEntity();var cow=passenger.get();
      h.assertTrue(front==originalFront.get()&&h.getLevel().getEntity(front.getUUID())==front&&carriage.storage==originalCargo.get()&&cargoCount(train,ItemVariant.of(cargo()))==7,"Natural server ticks retain the exact original body UUID and paid cargo storage");
      h.assertTrue(front.position().z-originalPosition.get().z>3.5&&cow.isAlive()&&cow.getVehicle()==front&&h.getLevel().getEntity(cow.getUUID())==cow,"Actual native body and original seated world cow reached the other physical rail");
      actual.traffic.validateCustody();
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualRelocationDeadEndAndNonTrackRefuseWithoutLosingOriginalPaidOwners(GameTestHelper h){
    var f=fixture(h,false,false,true);var shortTrack=R.south(6);relocationTrack(h,shortTrack,1);
    assembled(h,f,(actual,train)->{
      var carriage=train.carriages.getFirst();var front=carriage.anyAvailableEntity();var storage=carriage.storage;
      var graph=train.graph;var station=train.currentStation;var before=new ArrayList<Double>();train.forEachTravellingPoint(point->before.add(point.position));
      h.assertTrue(!TrainRelocator.preview(train,h.getLevel(),h.absolutePos(shortTrack),null,false,new Vec3(1,0,0)).valid(),"Physical dead end cannot hold both actual bogey wheel points");
      h.assertTrue(!TrainRelocator.relocate(train,h.getLevel(),h.absolutePos(shortTrack),null,false,new Vec3(1,0,0),false),"Actual dead-end relocation refuses before source ownership changes");
      h.assertTrue(!TrainRelocator.relocate(train,h.getLevel(),h.absolutePos(shortTrack.south()),null,false,new Vec3(1,0,0),false),"Actual non-track target is rejected");
      var after=new ArrayList<Double>();train.forEachTravellingPoint(point->after.add(point.position));
      h.assertTrue(train.graph==graph&&Objects.equals(station,train.currentStation)&&before.equals(after)&&carriage.anyAvailableEntity()==front&&carriage.storage==storage&&cargoCount(train,ItemVariant.of(cargo()))==7,"Every failed target retains the exact original station, wheel values, body, storage and seven named diamonds");
      actual.traffic.validateCustody();
    });
  }

  private static final BlockPos TRAIN_ROLLER=B.north(2), FLAT_PAVER_SENTINEL=R.east(22).south(22).below(3);
  private static final String PAVING_NAME="Original native train roller slabs";
  private static ItemStack pavingStock(){var stack=new ItemStack(Items.OAK_SLAB,32);stack.set(DataComponents.CUSTOM_NAME,Component.literal(PAVING_NAME));return stack;}
  private static Fixture rollerFixture(GameTestHelper h){
    var f=fixture(h,false,false,true);
    h.setBlock(B.north(),Blocks.OAK_PLANKS);
    h.setBlock(TRAIN_ROLLER,AllBlocks.MECHANICAL_ROLLER.defaultBlockState().setValue(RollerBlock.FACING,Direction.EAST));
    var roller=h.getBlockEntity(TRAIN_ROLLER,RollerBlockEntity.class);
    h.assertTrue(roller.filtering.setFilter(new ItemStack(Items.OAK_SLAB)),"Actual captured roller accepts the source slab material");
    roller.setMode(RollerBlockEntity.RollingMode.TUNNEL_PAVE);f.barrel.setItem(1,pavingStock());glue(h,B,TRAIN_ROLLER);
    h.setBlock(FLAT_PAVER_SENTINEL,Blocks.AIR);return f;
  }
  private static MovementContext trainRoller(GameTestHelper h,Train train){
    var carriage=train.carriages.getFirst();var owner=carriage.anyAvailableEntity();
    h.assertTrue(owner!=null&&owner.hasOwnedStructure()&&h.getLevel().getEntity(owner.getUUID())==owner,"Actual accepted current train frontend owns the real captured roller");
    var actor=owner.getContraption().getActorAt(TRAIN_ROLLER.subtract(B));
    h.assertTrue(actor!=null&&actor.right!=null&&actor.right.contraption==owner.getContraption(),"Genuine native actor initialization supplies the real movement context");
    return actor.right;
  }
  private static RollerMovementBehaviour actualTrainRoller(){return (RollerMovementBehaviour)MovementBehaviour.REGISTRY.get(AllBlocks.MECHANICAL_ROLLER);}
  private static final class TrainRollerProfileProbe extends RollerMovementBehaviour{
    PaveTask profile(MovementContext context){return super.createHeightProfileForTracks(context);}
  }
  private record OriginalWheel(TravellingPoint point,TrackNode first,TrackNode second,TrackEdge edge,double position,boolean blocked,boolean inverted){}
  private static List<OriginalWheel> originalWheels(Train train){
    var result=new ArrayList<OriginalWheel>();train.forEachTravellingPoint(point->result.add(new OriginalWheel(point,point.node1,point.node2,point.edge,point.position,point.blocked,point.upsideDown)));return result;
  }
  private record RollerCurve(BlockPos first,BlockPos second,BezierConnection curve){}
  private static RollerCurve rollerCurve(GameTestHelper h,Fixture f,int rise){
    var first=R.east().south(12);var second=first.offset(8,rise,8);
    for(var pos:BlockPos.betweenClosed(first.offset(-3,-2,-3),second.offset(3,3,3)))h.setBlock(pos,Blocks.AIR);
    var xRail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
    var zRail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.ZO);
    h.setBlock(first.below(),Blocks.STONE);h.setBlock(first,xRail);h.setBlock(second.below(),Blocks.STONE);h.setBlock(second,zRail);
    f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.TRACK,64));
    for(int i=0;i<2;i++){
      var pos=h.absolutePos(i==0?first:second);f.player.setYRot(i==0?-90:180);f.player.setXRot(0);
      h.assertTrue(f.player.getMainHandItem().getItem().useOn(new UseOnContext(f.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0,.125,0),Direction.UP,pos,false))).consumesAction(),"Real native track-item use constructs the actual train roller curve");
    }
    var curve=h.getBlockEntity(first,TrackBlockEntity.class).getConnections().get(h.absolutePos(second));
    h.assertTrue(curve!=null&&curve.primary,"Actual world endpoints own reciprocal source Bezier geometry");
    for(var pos:List.of(first,second))h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(pos),h.getBlockState(pos),false).complete(),"Actual source curve graph discovery completes");
    return new RollerCurve(first,second,curve);
  }
  private static Set<BlockPos> physicalPavement(GameTestHelper h,BlockPos first,BlockPos second){
    var result=new HashSet<BlockPos>();
    for(var pos:BlockPos.betweenClosed(first,second))if(h.getBlockState(pos).is(Blocks.OAK_SLAB))result.add(pos.immutable());
    return result;
  }
  private record CurveSurface(Vec3 position,Vec3 direction,double horizontalDistance){}
  private static CurveSurface nearestCurveSurface(BezierConnection curve,Vec3 center){
    // An independent geometric band/surface oracle, never the production rasterizer or its profile.
    double best=Double.MAX_VALUE,bestT=0;
    for(int i=0;i<=512;i++){double t=i/512d;var point=curve.getPosition(t);double dx=center.x-point.x,dz=center.z-point.z,distance=dx*dx+dz*dz;if(distance<best){best=distance;bestT=t;}}
    var point=curve.getPosition(bestT);var direction=curve.getPosition(Math.min(1,bestT+.001)).subtract(curve.getPosition(Math.max(0,bestT-.001))).multiply(1,0,1).normalize();
    return new CurveSurface(point,direction,Math.sqrt(best));
  }
  private static void restoreRollerToStraightRail(GameTestHelper h,Train train){
    h.assertTrue(TrainRelocator.relocate(train,h.getLevel(),h.absolutePos(R.east(12)),null,false,new Vec3(1,0,0),false),"Same actual train returns to its physical straight rail for native restoration");
  }
  private static void checkCurvePaving(GameTestHelper h,Fixture f,Train train,RollerCurve geometry,boolean reverse,boolean halfSurface){
    var target=new BezierTrackPointLocation(h.absolutePos(geometry.second),Math.max(5,(int)geometry.curve.getLength()));
    h.assertTrue(TrainRelocator.relocate(train,h.getLevel(),h.absolutePos(geometry.first),target,reverse,new Vec3(1,0,0),false),"Real native relocation binds both actual train wheels to the selected directed curve");
    try{
      var context=trainRoller(h,train);var wheels=originalWheels(train);var graph=train.graph;long revision=graph.revision();var storage=train.carriages.getFirst().storage;
      var before=physicalPavement(h,geometry.first.offset(-3,-2,-3),geometry.second.offset(3,3,3));long stock=cargoCount(train,ItemVariant.of(pavingStock()));
      actualTrainRoller().visitNewPosition(context,h.absolutePos(FLAT_PAVER_SENTINEL));
      var after=physicalPavement(h,geometry.first.offset(-3,-2,-3),geometry.second.offset(3,3,3));var added=new HashSet<>(after);added.removeAll(before);
      h.assertTrue(added.size()>=2,"Actual train roller paves multiple physical cells along its curved source work span; flat fallback cannot supply them");
      h.assertBlockPresent(Blocks.AIR,FLAT_PAVER_SENTINEL);
      h.assertTrue(stock-cargoCount(train,ItemVariant.of(pavingStock()))==added.size()&&cargoCount(train,ItemVariant.of(cargo()))==7,"Every actual new slab cell spends exactly one original component-bearing item; seven unrelated diamonds remain owned");
      boolean bottom=false;
      for(var pos:added){
        var state=h.getBlockState(pos);bottom|=state.getValue(SlabBlock.TYPE)==SlabType.BOTTOM;
        var center=Vec3.atCenterOf(h.absolutePos(pos));var surface=nearestCurveSurface(geometry.curve,center);
        double side=center.subtract(surface.position).dot(surface.direction.cross(new Vec3(0,1,0)));
        h.assertTrue(Math.abs(side-(reverse?-2:2))<.95&&surface.horizontalDistance>1,"Actual curve paving follows the correct signed two-block lateral strip after source edge inversion: side="+side+", reverse="+reverse);
        if(!after.contains(pos.above())){
          double top=h.absolutePos(pos).getY()+(state.getValue(SlabBlock.TYPE)==SlabType.BOTTOM?.5:1);
          h.assertTrue(Math.abs(top-surface.position.y)<.85,"Actual slab surface follows the native curve elevation, including half-block slope steps");
        }
      }
      if(halfSurface)h.assertTrue(bottom,"The actual sloped source curve creates at least one bottom-slab half step");
      long replayStock=cargoCount(train,ItemVariant.of(pavingStock()));actualTrainRoller().visitNewPosition(context,h.absolutePos(FLAT_PAVER_SENTINEL));
      h.assertTrue(cargoCount(train,ItemVariant.of(pavingStock()))==replayStock&&physicalPavement(h,geometry.first.offset(-3,-2,-3),geometry.second.offset(3,3,3)).equals(after),"Revisiting the actual paved curve spends no item and adds no terrain");
      h.assertTrue(originalWheels(train).equals(wheels)&&train.graph==graph&&graph.revision()==revision&&train.carriages.getFirst().storage==storage,"Independent real scout never moves actual wheels, replaces graph/cargo owners or changes graph topology");f.traffic.validateCustody();
    }finally{restoreRollerToStraightRail(h,train);}
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualTrainRollerPavesBothDirectedSidesOfPhysicalCurveWithoutMovingItsWheels(GameTestHelper h){
    var f=rollerFixture(h);var geometry=rollerCurve(h,f,0);
    assembled(h,f,3,(actual,train)->{checkCurvePaving(h,actual,train,geometry,false,false);checkCurvePaving(h,actual,train,geometry,true,false);});
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualTrainRollerPavesNativeSlopedCurveWithPaidHalfSlabs(GameTestHelper h){
    // Source 90-degree turns need turnSize >= 7 + max(0, rise - 3) * 2;
    // these physical 8x8 endpoints provide 7.4, so a genuine three-block rise fits.
    var f=rollerFixture(h);var geometry=rollerCurve(h,f,3);
    h.assertTrue(geometry.curve.starts.getSecond().y-geometry.curve.starts.getFirst().y==3,
        "Actual native item construction retains the source three-block sloped curve");
    assembled(h,f,3,(actual,train)->checkCurvePaving(h,actual,train,geometry,false,true));
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void actualTrainRollerFollowsPhysicalStraightSlopeAcrossDistinctElevations(GameTestHelper h){
    var f=rollerFixture(h);var start=R.south(12);var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.AE);
    for(int i=0;i<11;i++){var pos=start.east(i).above(i);h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,rail);}
    for(int i=0;i<11;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(start.east(i).above(i)),rail,false).complete(),"Physical ascending rails discover a real straight sloped train graph");
    assembled(h,f,3,(actual,train)->{
      try{
        long initial=cargoCount(train,ItemVariant.of(pavingStock()));var placed=new HashSet<BlockPos>();
        for(int step:List.of(4,7)){
          var destination=start.east(step).above(step);
          h.assertTrue(TrainRelocator.relocate(train,h.getLevel(),h.absolutePos(destination),null,false,new Vec3(1,1,0),false),"Actual complete train relocates onto the genuine rising rail");
          var wheels=originalWheels(train);actualTrainRoller().visitNewPosition(trainRoller(h,train),h.absolutePos(FLAT_PAVER_SENTINEL));
          var now=physicalPavement(h,start.offset(-2,-2,-3),start.offset(13,13,3));var added=new HashSet<>(now);added.removeAll(placed);
          h.assertTrue(!added.isEmpty(),"Each actual elevation has real newly paved source slope terrain");
          for(var pos:added)h.assertTrue(Math.abs(pos.getZ()-start.getZ())==2&&pos.getY()-pos.getX()==start.getY()-start.getX()-1,"Physical source support stays two blocks lateral and follows the exact 1:1 ascending rail grade");
          placed.addAll(added);h.assertTrue(originalWheels(train).equals(wheels),"Source slope scout leaves all real wheel coordinates unchanged");
        }
        int minimum=placed.stream().mapToInt(BlockPos::getY).min().orElseThrow(),maximum=placed.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        h.assertTrue(maximum-minimum>=2&&initial-cargoCount(train,ItemVariant.of(pavingStock()))==placed.size(),"Actual terrain spans multiple native elevations and consumes exactly the physical support count");h.assertBlockPresent(Blocks.AIR,FLAT_PAVER_SENTINEL);actual.traffic.validateCustody();
      }finally{restoreRollerToStraightRail(h,train);}
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=200) public void retiredActualTrainRollerContextCannotPaveOrDebitRestoredWorldCargo(GameTestHelper h){
    var f=rollerFixture(h);var created=new AtomicReference<Train>();var oldContext=new AtomicReference<MovementContext>();var oldOwner=new AtomicReference<CarriageContraptionEntity>();
    h.succeedWhen(()->{
      var train=created.get();
      if(train==null){
        h.assertTrue(StationAssemblyPreflight.check(f.station,f.player).readyForCapture(),"Actual source train and native entities become ready");h.assertTrue(f.station.tryAssemble(f.player),"Source station captures the original roller and named stock");train=f.station.getStation().getPresentTrain();h.assertTrue(train!=null,"Actual accepted train owns the captured source body");created.set(train);
      }
      if(oldContext.get()==null){var context=trainRoller(h,train);h.assertTrue(!new TrainRollerProfileProbe().profile(context).keys().isEmpty(),"The current real train has a genuine profile before retirement");oldContext.set(context);oldOwner.set(train.carriages.getFirst().anyAvailableEntity());}
      if(f.traffic.get(train.id)!=null&&!train.isRestoring()){h.assertTrue(train.canDisassemble(),"Actual original frontend is flat for full world restoration");train.disassemble(Direction.EAST,h.absolutePos(T.above()));}
      h.assertTrue(f.traffic.get(train.id)==null&&oldOwner.get().isRemoved(),"Full actual native restoration retires the original train frontend");
      var stale=oldContext.get();var empty=new TrainRollerProfileProbe().profile(stale);h.assertTrue(empty!=null&&empty.keys().isEmpty(),"Retired train profile stays explicitly empty and cannot become the ordinary non-train fallback");
      actualTrainRoller().visitNewPosition(stale,h.absolutePos(FLAT_PAVER_SENTINEL));h.assertBlockPresent(Blocks.AIR,FLAT_PAVER_SENTINEL);
      long slabs=0,diamonds=0;
      for(var pos:BlockPos.betweenClosed(new BlockPos(1,5,1),new BlockPos(30,10,7)))if(LoadedLevelAccess.blockEntity(h.getLevel(),h.absolutePos(pos)) instanceof BarrelBlockEntity barrel)for(int slot=0;slot<barrel.getContainerSize();slot++){var stack=barrel.getItem(slot);if(ItemStack.isSameItemSameComponents(stack,pavingStock()))slabs+=stack.getCount();if(ItemStack.isSameItemSameComponents(stack,cargo()))diamonds+=stack.getCount();}
      h.assertTrue(slabs==32&&diamonds==7,"All original named slabs and diamonds now belong only to actual restored world barrels after a stale actor invocation");
    });
  }

}
