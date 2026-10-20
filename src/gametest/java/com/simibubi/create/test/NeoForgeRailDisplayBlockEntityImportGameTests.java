package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Original source schemas through the real dispatcher/factory/save/load/world owners. Runtime UNRUN. */
public final class NeoForgeRailDisplayBlockEntityImportGameTests {
    private static final int VERSION=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static final String STRUCTURE="create_test:reservoir";
    private static Identifier id(String path){return Identifier.parse("create:"+path);}
    private static CompoundTag pos(BlockPos at){var tag=new CompoundTag();tag.putInt("X",at.getX());tag.putInt("Y",at.getY());tag.putInt("Z",at.getZ());return tag;}
    private static ListTag list(Tag... rows){var list=new ListTag();for(Tag row:rows)list.add(row);return list;}
    private static ListTag vector(double x,double y,double z){return list(DoubleTag.valueOf(x),DoubleTag.valueOf(y),DoubleTag.valueOf(z));}
    private static CompoundTag item(String name,int count){var tag=new CompoundTag();tag.putString("id",name);tag.putInt("count",count);return tag;}
    private static CompoundTag handler(int size,int slot,CompoundTag stack){var tag=new CompoundTag();tag.putInt("Size",size);var values=new ListTag();if(stack!=null){var entry=stack.copy();entry.putInt("Slot",slot);values.add(entry);}tag.put("Items",values);return tag;}
    private static CompoundTag transported(CompoundTag stack){var tag=new CompoundTag();tag.put("Item",stack);tag.putFloat("Pos",.5f);tag.putFloat("PrevPos",.4f);tag.putFloat("Offset",.1f);tag.putFloat("PrevOffset",0);tag.putInt("Angle",19);tag.putInt("InSegment",0);tag.putInt("InDirection",Direction.NORTH.get3DDataValue());return tag;}
    private static CompoundTag target(UUID identity,BlockPos relative){var tag=new CompoundTag();tag.store("Id",UUIDUtil.CODEC,identity);tag.put("TargetTrack",pos(relative));tag.putBoolean("Ortho",true);tag.putBoolean("TargetDirection",true);return tag;}
    private static CompoundTag station(UUID identity,BlockPos relative){var tag=target(identity,relative);tag.putString("DoorControl","WEST");tag.putInt("FailedCarriageIndex",-1);tag.putInt("PrevTrainColor",7);tag.put("OutputBuffer",handler(8,0,null));return tag;}
    private static CompoundTag display(String source){var tag=new CompoundTag();tag.put("TargetOffset",pos(new BlockPos(1,0,3)));tag.putInt("TargetLine",2);var conf=new CompoundTag();conf.putString("Id",source);tag.put("Source",conf);return tag;}
    private static CompoundTag section(){var tag=new CompoundTag();tag.putFloat("Width",70);tag.putString("Cycle","alphabet");tag.putString("Text","{\"text\":\"Original board\",\"color\":\"gold\",\"extra\":[{\"text\":\"26.3\"}]}");tag.putBoolean("RightAligned",true);tag.putBoolean("Transition",true);return tag;}
    private static CompoundTag flap(){var tag=new CompoundTag();tag.putBoolean("Controller",true);tag.putInt("XSize",3);tag.putInt("YSize",1);tag.putBoolean("CustomLine0",true);tag.putBoolean("GlowingLine1",true);tag.putString("Dye0","BLUE");for(int i=0;i<2;i++){var layout=new CompoundTag();layout.putString("Key","Original"+i);layout.put("Sections",list(section()));tag.put("Display"+i,layout);}return tag;}
    private static CompoundTag curve(){
        var tag=new CompoundTag();var first=new CompoundTag();first.put("Pos",pos(BlockPos.ZERO));var second=new CompoundTag();second.put("Pos",pos(new BlockPos(8,0,3)));tag.put("Positions",list(first,second));
        for(String key:List.of("Starts","Axes","Normals")){var a=new CompoundTag();var b=new CompoundTag();a.put("V",key.equals("Starts")?vector(.5,.25,.5):key.equals("Axes")?vector(1,0,0):vector(0,1,0));b.put("V",key.equals("Starts")?vector(8.5,.25,3.5):key.equals("Axes")?vector(-1,0,0):vector(0,1,0));tag.put(key,list(a,b));}
        tag.putBoolean("Primary",true);tag.putBoolean("Girder",true);tag.putString("Material","create:andesite");var a=new CompoundTag();a.putInt("V",3);var b=new CompoundTag();b.putInt("V",-2);tag.put("Smoothing",list(a,b));return tag;
    }
    private static CompoundTag convert(GameTestHelper h,String type,CompoundTag source,BlockPos absolute){
        var input=source.copy();input.putString("id",id(type).toString());input.putInt("x",absolute.getX());input.putInt("y",absolute.getY());input.putInt("z",absolute.getZ());var before=input.copy();
        var result=NeoForgeBlockEntityImport.convert(id(type),input,VERSION,h.getLevel().registryAccess(),NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),absolute));
        h.assertTrue(before.equals(input),"Original caller bytes remain unchanged before native admission");return result;
    }
    private static BlockEntity twice(GameTestHelper h,String type,Block block,CompoundTag source,BlockPos relative){
        var at=h.absolutePos(relative);var result=convert(h,type,source,at);var owner=BlockEntity.loadStatic(at,block.defaultBlockState(),result,h.getLevel().registryAccess());h.assertTrue(owner!=null,"Real registered native factory accepts "+type);if(owner instanceof StationBlockEntity)owner.setLevel(h.getLevel());
        var next=BlockEntity.loadStatic(at,block.defaultBlockState(),owner.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(next!=null&&next!=owner,"Fresh native owner reloads actual serialized "+type);if(next instanceof StationBlockEntity)next.setLevel(h.getLevel());return next;
    }
    private static boolean refused(Runnable task){try{task.run();return false;}catch(RuntimeException expected){return true;}}

    @GameTest public void allSixActualFactoriesRetainOriginalDomainsThroughNativeSaveAndFreshReload(GameTestHelper h){
        var tracks=new CompoundTag();tracks.put("Connections",list(curve()));tracks.putDouble("Smoothing",.125);tracks.put("BoundLocation",pos(new BlockPos(-9,72,18)));tracks.putString("BoundDimension","minecraft:the_nether");
        var track=(TrackBlockEntity)twice(h,"track",AllBlocks.TRACK,tracks,new BlockPos(1,2,1));h.assertTrue(track.getConnections().size()==1&&track.tilt.smoothingAngle.orElseThrow()==.125&&track.boundLocation.getSecond().equals(new BlockPos(-9,72,18))&&track.getPortalBinding()==null,"Curves/smoothing/actual dimensional reference survive without fabricated paired portal authority");
        var station=(StationBlockEntity)twice(h,"track_station",AllBlocks.TRACK_STATION,station(UUID.randomUUID(),new BlockPos(0,-1,2)),new BlockPos(2,2,1));h.assertTrue(station.doorControl()==DoorControl.WEST&&station.targeting().selection()==null,"Original station waits for actual loaded dimension/axis and preserves real door enum");
        var signal=target(UUID.randomUUID(),new BlockPos(2,-1,0));signal.putString("State","YELLOW");signal.putString("Overlay","DUAL");signal.putBoolean("Power",true);var signalOwner=(SignalBlockEntity)twice(h,"track_signal",AllBlocks.TRACK_SIGNAL,signal,new BlockPos(3,2,1));h.assertTrue(signalOwner.getState()==SignalBlockEntity.SignalState.YELLOW&&signalOwner.getOverlay()==SignalBlockEntity.OverlayState.DUAL&&signalOwner.getReportedPower(),"Actual signal state/overlay/power survive");
        twice(h,"track_observer",AllBlocks.TRACK_OBSERVER,target(UUID.randomUUID(),new BlockPos(-2,-1,0)),new BlockPos(4,2,1));
        var board=(FlapDisplayBlockEntity)twice(h,"flap_display",AllBlocks.DISPLAY_BOARD,flap(),new BlockPos(5,2,1));h.assertTrue(board.xSize==3&&board.manualLines[0]&&board.glowingLines[1]&&board.colour[0]==DyeColor.BLUE&&board.getLines().get(0).getSections().getFirst().getText().getString().equals("Original board26.3"),"Genuine source rich text, layout, width, dye and line flags survive modern text conversion and actual native read");
        var link=(DisplayLinkBlockEntity)twice(h,"display_link",AllBlocks.DISPLAY_LINK,display("create:cuckoo_clock_source_time_of_day"),new BlockPos(6,2,1));h.assertTrue(link.activeSource==AllDisplaySources.TIME_OF_DAY&&link.getTargetOffset().equals(new BlockPos(1,0,3))&&link.targetLine==2,"Source config compound survives root common dispatch and native source alias canonicalization");h.succeed();
    }

    @GameTest public void stationHeldScheduleAndTailOutputStayDistinctMaterialOwnersWithScopedTransportIds(GameTestHelper h){
        var original=station(UUID.randomUUID(),new BlockPos(0,-1,2));var schedule=item("create:schedule",1);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original reusable timetable\"}");schedule.put("components",components);original.put("HeldItem",transported(schedule));original.put("OutputBuffer",handler(8,7,item("minecraft:diamond",11)));
        var first=(StationBlockEntity)twice(h,"track_station",AllBlocks.TRACK_STATION,original,new BlockPos(1,2,1));var second=(StationBlockEntity)twice(h,"track_station",AllBlocks.TRACK_STATION,original,new BlockPos(2,2,1));
        h.assertTrue(first.getAutoSchedule().is(AllItems.SCHEDULE)&&first.getAutoSchedule().getCount()==1&&first.getAutoSchedule().get(DataComponents.CUSTOM_NAME).getString().equals("Original reusable timetable")&&first.depotBehaviour.inventory.outputs.get(7).getAmount()==11,"Actual station depot retains one held timetable plus eleven separate tail-slot diamonds");
        h.assertTrue(first.depotBehaviour.inventory.inputs.getFirst().getAmount()==1&&first.depotBehaviour.inventory.outputs.getFirst().isResourceBlank()&&!first.depotBehaviour.getHeldItem().id.equals(second.depotBehaviour.getHeldItem().id),"Held metadata mirrors one canonical input; distinct real original station scopes receive distinct new transport identities");h.succeed();
    }

    @GameTest public void originalStationDiagnosticsPreserveModernRichTextAndPackedPositionWithoutAssemblyAuthority(GameTestHelper h){
        var original=station(UUID.randomUUID(),new BlockPos(0,-1,1));original.putInt("FailedCarriageIndex",3);original.putString("PrevTrainName","{\"text\":\"Old commuter\"}");var failure=new CompoundTag();failure.putString("Component","{\"text\":\"Original coupling error\"}");long position=new BlockPos(-4,80,29).asLong();failure.putLong("Position",position);original.put("LastException",failure);
        var owner=(StationBlockEntity)twice(h,"track_station",AllBlocks.TRACK_STATION,original,new BlockPos(1,2,1));var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.failedCarriageIndex()==3&&owner.lastDisassembledTrainName().getString().equals("Old commuter")&&owner.trainAssemblyFailure()!=null&&saved.getCompoundOrEmpty("TrainAssemblyFailure").getCompoundOrEmpty("LastException").getLongOr("Position",0)==position,"Actual native diagnostic owner retains old component, exact failure index and packed source position");h.assertTrue(!saved.contains("DisassembledTrainId")&&!saved.contains("AssemblyEntryIntent"),"A diagnostic or remembered name cannot invent a Train/adoption/authorized operation receipt");h.succeed();
    }

    @GameTest public void observerFilterIsATypedSampleWhileOriginalTargetIdentityStaysExact(GameTestHelper h){
        UUID identity=UUID.randomUUID();var original=target(identity,new BlockPos(1,-1,2));var filter=item("minecraft:emerald",1);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Cargo sample\"}");filter.put("components",components);original.put("Filter",filter);original.putInt("FilterAmount",27);original.putBoolean("UpTo",true);
        var owner=(TrackObserverBlockEntity)twice(h,"track_observer",AllBlocks.TRACK_OBSERVER,original,new BlockPos(1,2,1));h.assertTrue(owner.targeting().id().equals(identity)&&owner.getFilter().getCount()==1&&owner.getFilter().get(DataComponents.CUSTOM_NAME).getString().equals("Cargo sample"),"Native filtering sample and original railway point UUID survive separately");h.succeed();
    }

    @GameTest public void displayConfigUsesOriginalScalarSchemaAndRetainsRealNegativeCounterWithoutItemGuessing(GameTestHelper h){
        var source=display("create:track_station_source_station_summary");var conf=source.getCompoundOrEmpty("Source");conf.putString("Filter","Central *");conf.putInt("NameColumn",52);conf.putInt("PlatformColumn",4);source.put("Source",conf);
        var owner=(DisplayLinkBlockEntity)twice(h,"display_link",AllBlocks.DISPLAY_LINK,source,new BlockPos(1,2,1));h.assertTrue(owner.activeSource==AllDisplaySources.STATION_SUMMARY&&owner.getSourceConfig().getStringOr("Filter","").equals("Central *"),"Display Filter is an actual station-pattern string, never a guessed ItemStack");
        source=display("create:accumulate_items");conf=source.getCompoundOrEmpty("Source");conf.putInt("Collected",Integer.MIN_VALUE);conf.putString("Label","Long-running original counter");source.put("Source",conf);owner=(DisplayLinkBlockEntity)twice(h,"display_link",AllBlocks.DISPLAY_LINK,source,new BlockPos(2,2,1));h.assertTrue(owner.getSourceConfig().getIntOr("Collected",0)==Integer.MIN_VALUE,"Reachable original signed integer counter overflow is not silently clamped or reset");h.succeed();
    }

    @GameTest public void allTwentyFiveRegisteredCoreSourceSchemasAndFactoryPanelCodecRemainLoadable(GameTestHelper h){
        String[] sources={"time_of_day","stopwatch","observed_train_name","station_summary","train_status","death_count","scoreboard","enchant_power","redstone_power","nixie_tube","item_names","boiler","current_floor","gauge_status","entity_name","kinetic_speed","kinetic_stress","accumulate_items","item_throughput","count_items","list_items","count_fluids","list_fluids","read_package_address","fill_level"};
        for(String source:sources){var data=display("create:"+source);if(source.equals("gauge_status"))data.getCompoundOrEmpty("Source").putInt("Format",1);var link=(DisplayLinkBlockEntity)twice(h,"display_link",AllBlocks.DISPLAY_LINK,data,new BlockPos(1,2,1));h.assertTrue(link.activeSource!=null&&link.activeSource.getId().equals(id(source)),"Real registered source retained: "+source);}
        var data=display("create:gauge_status");var panel=new FactoryPanelPosition(new BlockPos(10,12,-8),FactoryPanelBlock.PanelSlot.BOTTOM_RIGHT);data.put("LinkedGauges",list(FactoryPanelPosition.CODEC.encodeStart(NbtOps.INSTANCE,panel).getOrThrow()));var link=(DisplayLinkBlockEntity)twice(h,"display_link",AllBlocks.DISPLAY_LINK,data,new BlockPos(1,2,1));h.assertTrue(link.factoryPanelSupport.getLinkedPanels().contains(panel),"Genuine old and new FactoryPanelPosition codecs share pos/slot; linked gauge is preserved");h.succeed();
    }

    @GameTest public void genuineSameTickThroughputInfinityAndAllTenOriginalSamplesStayExact(GameTestHelper h){
        var source=display("create:brass_tunnel_source_item_throughput");var conf=source.getCompoundOrEmpty("Source");conf.putInt("Interval",1);conf.putFloat("Rate",Float.POSITIVE_INFINITY);conf.putLong("LastReceived",12345);conf.putInt("LastReceivedAmount",16);conf.putInt("Index",7);var rates=new ListTag();for(int i=0;i<10;i++)rates.add(FloatTag.valueOf(i==6?Float.POSITIVE_INFINITY:i-1));conf.put("PrevRates",rates);source.put("Source",conf);
        var link=(DisplayLinkBlockEntity)twice(h,"display_link",AllBlocks.DISPLAY_LINK,source,new BlockPos(1,2,1));var saved=link.getSourceConfig();h.assertTrue(saved.getFloatOr("Rate",0)==Float.POSITIVE_INFINITY&&saved.getListOrEmpty("PrevRates").size()==10&&saved.getListOrEmpty("PrevRates").getFloatOr(6,0)==Float.POSITIVE_INFINITY&&saved.getLongOr("LastReceived",0)==12345,"Genuine source zero-time division can persist infinity; exact scalar NBT remains with its real native source owner");h.succeed();
    }

    @GameTest public void unknownDisplaySourceAndOutOfCapacityPayloadsRefuseBeforeNativeSilentClamps(GameTestHelper h){
        var board=flap();board.putInt("XSize",33);var lines=flap();lines.putBoolean("CustomLine2",true);var section=flap();section.getCompoundOrEmpty("Display0").getListOrEmpty("Sections").getCompoundOrEmpty(0).putFloat("Width",8193);
        var unknown=display("addon:unknown_source");var out=display("create:time_of_day");out.putInt("TargetLine",256);
        for(var pair:List.of(Map.entry("flap_display",board),Map.entry("flap_display",lines),Map.entry("flap_display",section),Map.entry("display_link",unknown),Map.entry("display_link",out))){var data=pair.getValue();var before=data.copy();h.assertTrue(refused(()->convert(h,pair.getKey(),data,h.absolutePos(new BlockPos(1,2,1))))&&before.equals(data),"Unsupported original capacity/schema is refused before native clamping can lose source state");}h.succeed();
    }

    @GameTest public void originalTargetingSafePayloadRetainsItsActualReferenceAndNeverInventsStationStock(GameTestHelper h){
        for(String type:List.of("track_station","track_signal","track_observer")){
            var source=target(UUID.randomUUID(),new BlockPos(3,-2,8));source.put("PrevAxis",vector(1,0,0));var curve=new CompoundTag();curve.put("Key",pos(new BlockPos(8,-2,11)));curve.putInt("Segment",7);source.put("Bezier",curve);
            var converted=NeoForgeBlockEntityImport.convertSafe(id(type),source,VERSION,h.getLevel().registryAccess(),null);var data=converted.getCompoundOrEmpty(type.equals("track_station")?"StationData":"Targeting");var original=data.read("OriginalTargeting",NeoForgeTrackTargetingReference.CODEC).orElseThrow();
            h.assertTrue(original.offset().equals(new BlockPos(3,-2,8))&&original.curve().orElseThrow().segment()==7&&!data.contains("TargetingSelection")&&!data.contains("Dimension")&&!converted.contains("Inventory"),"Original safe targeting saves its relative geometry without fabricated dimension/axis or station Depot material");
        }h.succeed();
    }

    @GameTest public void originalPointMigrationKeepsLastSlotMailAndNativeTextWithoutConstructingAWorldGraph(GameTestHelper h){
        UUID identity=UUID.randomUUID();var source=station(identity,new BlockPos(2,-1,0));var snapshot=new CompoundTag();snapshot.store("Id",UUIDUtil.CODEC,identity);snapshot.putDouble("Position",2);
        var first=new CompoundTag();first.put("Pos",pos(new BlockPos(0,128,0)));first.putInt("D",0);var second=new CompoundTag();second.put("Pos",pos(new BlockPos(10,128,0)));second.putInt("D",0);snapshot.put("Edge",list(first,second));snapshot.put("BlockEntityPos",pos(new BlockPos(7,65,-3)));snapshot.putInt("BlockEntityDimension",0);snapshot.putString("Name","Original mail interchange");snapshot.putBoolean("Assembling",false);
        var port=new CompoundTag();port.put("Pos",pos(new BlockPos(8,65,-3)));port.putString("Address","Original mail");port.putBoolean("Primed",true);var cargo=item("minecraft:diamond",11);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original retained mail\"}");cargo.put("components",components);port.put("OfflineBuffer",handler(18,17,cargo));snapshot.put("Ports",list(port));var dimension=new CompoundTag();dimension.putString("Id",h.getLevel().dimension().identifier().toString());snapshot.put("DimensionPalette",list(dimension));source.put("Migrate",snapshot);
        var owner=(StationBlockEntity)twice(h,"track_station",AllBlocks.TRACK_STATION,source,new BlockPos(1,2,1));var migration=owner.saveWithoutMetadata(h.getLevel().registryAccess()).getCompoundOrEmpty("StationData").getCompoundOrEmpty("Migrate");var prepared=com.simibubi.create.content.trains.station.StationEdgePoints.STATION.create();prepared.read(migration,h.getLevel().registryAccess(),true,DimensionPalette.read(migration));var items=prepared.ports().getFirst().data().items();
        h.assertTrue(prepared.graph()==null&&prepared.name().equals("Original mail interchange")&&items.get(17).getCount()==11&&items.get(17).get(DataComponents.CUSTOM_NAME).getString().equals("Original retained mail")&&items.subList(0,17).stream().allMatch(ItemStack::isEmpty),"Actual native migration decoder retains all offline mail slots/components without a dummy graph, actual admission or fabricated point ownership");h.succeed();
    }

    @GameTest public void pendingOriginalTargetTransformsRelativeTrackCurveAndDirectionHintsBeforeAnyWorldResolution(GameTestHelper h){
        var original=target(UUID.randomUUID(),new BlockPos(2,-1,3));original.put("PrevAxis",vector(1,0,0));var bezier=new CompoundTag();bezier.put("Key",pos(new BlockPos(8,-1,7)));bezier.putInt("Segment",9);original.put("Bezier",bezier);
        var owner=(TrackObserverBlockEntity)twice(h,"track_observer",AllBlocks.TRACK_OBSERVER,original,new BlockPos(1,2,1));var transform=new com.simibubi.create.content.contraptions.StructureTransform(BlockPos.ZERO,Direction.Axis.Y,Rotation.CLOCKWISE_90,Mirror.NONE);var previous=owner.targeting().id();owner.transform(owner,transform);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess()).getCompoundOrEmpty("Targeting");var reference=saved.read("OriginalTargeting",NeoForgeTrackTargetingReference.CODEC).orElseThrow();
        h.assertTrue(!previous.equals(owner.targeting().id())&&reference.offset().equals(transform.applyWithoutOffset(new BlockPos(2,-1,3)))&&reference.curve().orElseThrow().curveTarget().equals(transform.applyWithoutOffset(new BlockPos(8,-1,7)))&&reference.curve().orElseThrow().segment()==9&&reference.rotatedAxis().orElseThrow().distanceToSqr(transform.applyWithoutOffsetUncentered(new Vec3(1,0,0)))<1e-12&&!saved.contains("TargetingSelection"),"Real original movement transforms pending relative geometry and source previous-axis hint before any loaded world dimension/axis exists");h.succeed();
    }

    @GameTest public void duplicateCurveEndpointAndForeignPointMigrationAreRefusedWithoutChangingSource(GameTestHelper h){
        var tracks=new CompoundTag();tracks.put("Connections",list(curve(),curve()));var before=tracks.copy();h.assertTrue(refused(()->convert(h,"track",tracks,h.absolutePos(new BlockPos(1,2,1))))&&tracks.equals(before),"Two original curves cannot silently overwrite one native endpoint-map owner");
        var station=station(UUID.randomUUID(),new BlockPos(0,-1,2));var migrate=new CompoundTag();migrate.store("Id",UUIDUtil.CODEC,UUID.randomUUID());migrate.put("DimensionPalette",new ListTag());station.put("Migrate",migrate);h.assertTrue(refused(()->convert(h,"track_station",station,h.absolutePos(new BlockPos(1,2,1)))),"Malformed/foreign source point migration cannot supply graph or material authority");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void unreadyRealTargetRemainsPendingAcrossActualNativeSaveWithoutChunkGeneration(GameTestHelper h){
        var at=new BlockPos(5,6,6);h.setBlock(at.below(),Blocks.STONE);h.setBlock(at,AllBlocks.TRACK_SIGNAL);var absolute=h.absolutePos(at);var source=target(UUID.randomUUID(),new BlockPos(1_000_000,0,0));source.putString("State","INVALID");source.putString("Overlay","SKIP");source.putBoolean("Power",false);
        var owner=h.getBlockEntity(at,SignalBlockEntity.class);owner.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),convert(h,"track_signal",source,absolute)));
        var target=absolute.offset(new BlockPos(1_000_000,0,0));h.assertTrue(!LoadedLevelAccess.isLoaded(h.getLevel(),target),"Actual distant source target is not loaded before resolution");owner.targeting().resolveTick();var saved=owner.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.targeting().status()==TrackTargetingBehaviour.Status.PENDING_LOADED_FRONTIER&&saved.getCompoundOrEmpty("Targeting").contains("OriginalTargeting")&&!saved.getCompoundOrEmpty("Targeting").contains("TargetingSelection")&&!LoadedLevelAccess.isLoaded(h.getLevel(),target),"Real owner retains original target and defers without synthetic axis/dimension or forced target chunk");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualGraphStationNameWinsBeforeImportedBlockEntityBeginsConfiguringItsOwner(GameTestHelper h){
        var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);var start=new BlockPos(3,6,4);for(int i=0;i<15;i++){h.setBlock(start.east(i).below(),Blocks.STONE);h.setBlock(start.east(i),rail);}for(int i=0;i<15;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(start.east(i)),rail,false).complete(),"Actual line creates real native graph");
        var at=start.east(2).south(2);var target=start.east(2);h.setBlock(at.below(),Blocks.STONE);h.setBlock(at,AllBlocks.TRACK_STATION);var original=h.getBlockEntity(at,StationBlockEntity.class);h.assertTrue(original.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(target)),true,new Vec3(1,0,0),Optional.empty())),"Actual station owns physical graph point");var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(original.getBlockPos()).add(0,0,1));h.assertTrue(original.configure(player,"Original Interchange",false,DoorControl.ALL),"Actual source point has its authoritative original station name");
        var point=original.getStation();var imported=station(original.targeting().id(),target.subtract(at));original.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),convert(h,"track_station",imported,original.getBlockPos())));
        StationBlockEntity.tick(h.getLevel(),original.getBlockPos(),original.getBlockState(),original);h.assertTrue(original.getStation()==point&&original.stationName().equals("Original Interchange")&&point.name().equals("Original Interchange")&&original.doorControl()==DoorControl.WEST,"Actual imported point UUID/name survives first native normal tick; BE default cannot overwrite global authority");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualStationFabricStorageRejectsOrdinaryItemsAndReturnsEachPhysicalTimetableOnce(GameTestHelper h){
        var at=new BlockPos(5,6,6);h.setBlock(at.below(),Blocks.STONE);h.setBlock(at,AllBlocks.TRACK_STATION);var station=h.getBlockEntity(at,StationBlockEntity.class);var storage=ItemStorage.SIDED.find(h.getLevel(),station.getBlockPos(),Direction.UP);h.assertTrue(storage!=null,"Actual station has registered native sided storage");
        try(var tx=Transaction.openOuter()){h.assertTrue(storage.insert(ItemVariant.of(Items.DIAMOND),5,tx)==0&&storage.insert(ItemVariant.of(AllItems.SCHEDULE),1,tx)==1,"Source station only accepts its real schedule item");}
        h.assertTrue(station.getAutoSchedule().isEmpty(),"Aborted actual insertion creates no canonical or metadata stock");
        try(var tx=Transaction.openOuter()){h.assertTrue(storage.insert(ItemVariant.of(AllItems.SCHEDULE),1,tx)==1,"Committed native insertion accepts actual timetable");tx.commit();}
        h.assertTrue(station.getAutoSchedule().getCount()==1&&!station.applyAutoSchedule(),"One real held template exists; no bound Train is invented by insertion");
        try(var tx=Transaction.openOuter()){h.assertTrue(storage.extract(ItemVariant.of(AllItems.SCHEDULE),64,tx)==1,"Actual station storage debits exactly one held schedule");tx.commit();}
        try(var tx=Transaction.openOuter()){h.assertTrue(storage.extract(ItemVariant.of(AllItems.SCHEDULE),64,tx)==0&&station.getAutoSchedule().isEmpty(),"The metadata view never becomes another extractable timetable");}h.succeed();
    }
}
