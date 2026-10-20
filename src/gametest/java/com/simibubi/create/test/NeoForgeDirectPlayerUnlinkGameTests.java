package com.simibubi.create.test;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.graph.NeoForgeTrackGraphImport;
import com.simibubi.create.foundation.utility.NeoForgePlayerStorageImport;
import com.simibubi.create.foundation.mixin.accessor.PlayerDataStorageAccessor;
import com.mojang.authlib.GameProfile;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.phys.Vec3;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.trains.NeoForgeRailwayImport;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.TrainTrafficSavedData;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.schedule.NeoForgeTrainScheduleImport;
import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataPairImport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;

/** Genuine original rail player file, real direct unlink/logout/native frontend retirement. Authored3 UNRUN. */
public final class NeoForgeDirectPlayerUnlinkGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 0, 0), SEAT = new BlockPos(0, 1, 0);
    private record Fixture(CompoundTag source, UUID graph, UUID train, UUID body) {}
    private record Directory(Path root) implements AutoCloseable {
        static Directory create() throws IOException { return new Directory(Files.createTempDirectory("create-original-railway-")); }
        Path source() { return root.resolve("create_tracks.dat"); }
        SavedDataStorage storage(GameTestHelper h) { return new SavedDataStorage(root, DataFixers.getDataFixer(), h.getLevel().registryAccess()); }
        @Override public void close() throws IOException {
            try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static CompoundTag xyz(BlockPos position) { var result = new CompoundTag(); result.putInt("X", position.getX()); result.putInt("Y", position.getY()); result.putInt("Z", position.getZ()); return result; }
    private static ListTag vector(double x, double y, double z) { var result = new ListTag(); result.add(DoubleTag.valueOf(x)); result.add(DoubleTag.valueOf(y)); result.add(DoubleTag.valueOf(z)); return result; }
    private static ListTag values(Tag... tags) { var result = new ListTag(); for (var tag : tags) result.add(tag); return result; }
    private static CompoundTag location(int x) { var result = new CompoundTag(); result.put("Pos", xyz(new BlockPos(x, 128, 0))); result.putInt("D", 1); return result; }
    private static CompoundTag point(double distance) { var result = new CompoundTag(); result.put("Nodes", values(location(0), location(20))); result.putDouble("Position", distance); result.putBoolean("UpsideDown", false); return result; }
    private static CompoundTag bogeyData() { var result = new CompoundTag(); result.putString("BogeyStyle", "create:standard"); result.putBoolean("UpsideDown", false); return result; }
    private static CompoundTag bogey() { var result = new CompoundTag(); result.putString("Type", "create:small_bogey"); result.put("Points", values(point(3.5), point(2.5))); result.putBoolean("UpsideDown", false); result.put("BogeyData", bogeyData()); return result; }
    private static CompoundTag edge(int to) {
        var edge = new CompoundTag(); edge.putString("Material", "create:andesite"); var signals = new CompoundTag(); signals.putBoolean("PassiveGroup", true); edge.put("Signals", signals);
        var result = new CompoundTag(); result.putInt("To", to); result.put("EdgeData", edge); return result;
    }
    private static CompoundTag node(int x, int to) { var result = new CompoundTag(); result.put("Location", location(x)); result.put("Normal", vector(0, 1, 0)); result.put("Connections", values(edge(to))); return result; }
    private static CompoundTag stack(int count) {
        var result = new CompoundTag(); result.putString("id", "minecraft:diamond"); result.putInt("count", count);
        var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("source-cargo", "same original eleven"); components.put("minecraft:custom_data", custom); result.put("components", components); return result;
    }
    private static CompoundTag cargo() {
        var sparse = new CompoundTag(); sparse.put("2", stack(11)); var slots = new CompoundTag(); slots.put("items", sparse); slots.putInt("size", 27);
        var storage = new CompoundTag(); storage.putString("type", "create:chest"); storage.put("value", slots);
        var entry = new CompoundTag(); entry.put("pos", xyz(CHEST)); entry.put("storage", storage);
        var result = new CompoundTag(); result.put("items", values(entry)); result.putInt("TicksSinceLastExchange", 37); return result;
    }
    private static CompoundTag frame(int dimension) {
        var result = cargo(); result.putInt("Dim", dimension); result.putFloat("Cutoff", dimension == 1 ? 0 : 1); result.putInt("DiscardTicks", dimension == 1 ? 0 : 3);
        var pivot = new CompoundTag(); pivot.put("Pos", xyz(new BlockPos(0, 128, 0))); pivot.putInt("YO", 2); result.put("Pivot", pivot);
        result.put("PositionAnchor", vector(.5, 65, .5)); var first = new CompoundTag(); first.put("V", vector(.5, 65, .5)); var second = new CompoundTag(); second.put("V", vector(1.5, 65, .5)); result.put("RotationAnchors", values(first, second)); return result;
    }
    private static CompoundTag sourceState(BlockState state) {
        var result = new CompoundTag(); result.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        // Actual26.3 states expose Property.Value records as a stream. The
        // fixture deliberately writes the genuine original3955 Name/Properties
        // form, retaining each real property's original flat serialized spelling.
        var properties = new CompoundTag();
        state.getValues().forEach(entry -> properties.putString(entry.property().getName(),entry.valueName()));
        if (!properties.isEmpty()) result.put("Properties", properties);
        return result;
    }
    private static <T extends Comparable<T>> void putProperty(CompoundTag data, net.minecraft.world.level.block.state.properties.Property<T> property, Comparable<?> value) {
        @SuppressWarnings("unchecked") T typed = (T) value; data.putString(property.getName(), property.getName(typed));
    }
    private static CompoundTag body() {
        var states = new LinkedHashMap<BlockPos, BlockState>(); states.put(BlockPos.ZERO, AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
        states.put(CHEST, Blocks.CHEST.defaultBlockState()); states.put(SEAT, AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState());
        var palette = new ListTag(); var rows = new ListTag(); var actors = new ListTag(); var interactors = new ListTag();
        for (var entry : states.entrySet()) {
            int stateIndex = palette.size(); palette.add(sourceState(entry.getValue())); var row = new CompoundTag(); row.putLong("Pos", entry.getKey().asLong()); row.putInt("State", stateIndex);
            if (entry.getKey().equals(BlockPos.ZERO)) { var data = new CompoundTag(); data.putString("id", "create:bogey"); data.put("BogeyData", bogeyData()); row.put("Data", data); }
            if (entry.getKey().equals(CHEST)) { var data = new CompoundTag(); data.putString("id", "minecraft:chest"); var stock = stack(11); stock.putByte("Slot", (byte) 2); data.put("Items", values(stock)); row.put("Data", data); }
            rows.add(row);
            if (MovementBehaviour.REGISTRY.get(entry.getValue()) != null) {
                var actor = new CompoundTag(); actor.put("Pos", xyz(entry.getKey())); actor.put("Motion", vector(0, 0, 0)); actor.put("RelativeMotion", vector(0, 0, 0));
                actor.putBoolean("Stall", false); actor.putBoolean("FirstMovement", false); var data = new CompoundTag(); if (entry.getKey().equals(SEAT)) data.putInt("SeatIndex", 0); actor.put("Data", data); actors.add(actor);
            }
            if (MovingInteractionBehaviour.REGISTRY.get(entry.getValue()) != null) { var interaction = new CompoundTag(); interaction.put("Pos", xyz(entry.getKey())); interactors.add(interaction); }
        }
        var result = new CompoundTag(); result.putString("Type", "create:carriage"); var blocks = new CompoundTag(); blocks.put("Palette", palette); blocks.put("BlockList", rows); result.put("Blocks", blocks);
        result.put("Actors", actors); result.put("Interactors", interactors); result.put("CapturedMultiblocks", new ListTag()); result.put("DisabledActors", new ListTag());
        result.put("Superglue", new ListTag()); var seat = new CompoundTag(); seat.put("Pos", xyz(SEAT)); result.put("Seats", values(seat)); result.put("SubContraptions", new ListTag());
        result.put("Anchor", xyz(new BlockPos(0, 64, 0))); result.putBoolean("Stalled", false); result.putBoolean("BottomlessSupply", false); result.putString("AssemblyDirection", "EAST");
        for (var key : new String[] {"FrontControls", "BackControls", "FrontBlazeConductor", "BackBlazeConductor"}) result.putBoolean(key, false);
        result.put("ConductorSeats", new ListTag()); var sound = new CompoundTag(); sound.putInt("Offset", 0); sound.put("Sources", new ListTag()); result.put("SoundQueue", sound);
        return result;
    }
    private static CompoundTag entity(UUID train, UUID body) {
        var result = new CompoundTag(); result.putString("id", "create:carriage_contraption"); result.store("UUID", UUIDUtil.CODEC, body);
        result.put("Pos", vector(.5, 64, .5)); result.put("Motion", vector(0, 0, 0)); result.put("Rotation", values(FloatTag.valueOf(0), FloatTag.valueOf(0)));
        result.putFloat("FallDistance", 0); result.putShort("Fire", (short) -1); result.putShort("Air", (short) 300); result.putBoolean("OnGround", false); result.putBoolean("Invulnerable", false); result.putInt("PortalCooldown", 0);
        result.put("Contraption", body()); result.putBoolean("Stalled", false); result.putBoolean("Initialized", true); result.putBoolean("Placed", false); result.put("CachedMotion", vector(0, 0, 0));
        result.putString("InitialOrientation", "EAST"); result.putFloat("Yaw", 0); result.putFloat("Pitch", 0); result.store("TrainId", UUIDUtil.CODEC, train); result.putInt("CarriageIndex", 0); return result;
    }
    private static CompoundTag runtime() {
        var result = new CompoundTag(); result.putInt("CurrentEntry", 0); result.putBoolean("AutoSchedule", false); result.putBoolean("Paused", true); result.putBoolean("Completed", false); result.putString("State", "PRE_TRANSIT");
        result.putIntArray("ConditionProgress", new int[0]); result.put("ConditionContext", new ListTag()); result.putIntArray("TransitTimes", new int[0]); return result;
    }
    private static Fixture fixture() {
        UUID graphId = UUID.randomUUID(), trainId = UUID.randomUUID(), bodyId = UUID.randomUUID();
        var graph = new CompoundTag(); graph.store("Id", UUIDUtil.CODEC, graphId); graph.putInt("Color", 0x2255aa); graph.put("Nodes", values(node(0, 1), node(20, 0))); graph.put("Points", new CompoundTag());
        var group = new CompoundTag(); group.store("Id", UUIDUtil.CODEC, graphId); group.putString("Color", "BLUE"); group.put("Connected", new ListTag()); group.putBoolean("Fallback", true);
        var carriage = new CompoundTag(); carriage.put("FirstBogey", bogey()); carriage.putInt("Spacing", 0); carriage.putBoolean("FrontConductor", false); carriage.putBoolean("BackConductor", false); carriage.putBoolean("Stalled", false);
        carriage.put("Entity", entity(trainId, bodyId)); carriage.put("Passengers", new CompoundTag()); carriage.put("EntityPositioning", values(frame(1), frame(0)));
        var train = new CompoundTag(); train.store("Id", UUIDUtil.CODEC, trainId); train.store("Owner", UUIDUtil.CODEC, UUID.randomUUID()); train.store("Graph", UUIDUtil.CODEC, graphId); train.put("Carriages", values(carriage)); train.putIntArray("CarriageSpacing", new int[0]);
        train.putBoolean("DoubleEnded", false); train.putDouble("Speed", .125); train.putDouble("Throttle", .75); train.putInt("Fuel", 123); train.putDouble("TargetSpeed", .25); train.putString("IconType", "create:traditional"); train.putInt("MapColorIndex", 2);
        train.putString("Name", "{\"text\":\"Original rail\",\"color\":\"gold\"}"); train.putBoolean("Backwards", true); train.putBoolean("Derailed", false); train.putBoolean("UpdateSignals", true);
        for (var key : new String[] {"SignalBlocks", "ReservedSignalBlocks", "OccupiedObservers", "MigratingPoints"}) train.put(key, new ListTag()); train.put("Runtime", runtime()); train.put("Navigation", new CompoundTag());
        var source = new CompoundTag(); source.put("RailGraphs", values(graph)); source.put("SignalBlocks", values(group)); source.put("Trains", values(train)); new DimensionPalette(List.of(Level.OVERWORLD, Level.NETHER)).write(source);
        return new Fixture(source, graphId, trainId, bodyId);
    }
    private static CompoundTag train(Fixture fixture) { return fixture.source.getList("Trains").orElseThrow().getCompoundOrEmpty(0); }
    private static CompoundTag carriage(Fixture fixture) { return train(fixture).getList("Carriages").orElseThrow().getCompoundOrEmpty(0); }
    private static CompoundTag body(Fixture fixture) { return carriage(fixture).getCompound("Entity").orElseThrow().getCompound("Contraption").orElseThrow(); }
    private static CompoundTag frame(Fixture fixture, int index) { return carriage(fixture).getList("EntityPositioning").orElseThrow().getCompoundOrEmpty(index); }
    private static CompoundTag stock(Fixture fixture, int frame) { return frame(fixture, frame).getList("items").orElseThrow().getCompoundOrEmpty(0).getCompound("storage").orElseThrow().getCompound("value").orElseThrow().getCompound("items").orElseThrow().getCompound("2").orElseThrow(); }
    private static void source(Directory directory, CompoundTag data) throws IOException { var root = new CompoundTag(); root.putInt("DataVersion", 3955); root.put("data", data); NbtIo.writeCompressed(root, directory.source()); }
    private static NeoForgeSavedDataPairImport.Pair<RailwaySavedData, TrainTrafficSavedData> load(GameTestHelper h, Directory directory, SavedDataStorage storage) {
        return NeoForgeSavedDataPairImport.load(storage, directory.root, h.getLevel().registryAccess(), RailwaySavedData.TYPE, TrainTrafficSavedData.TYPE,
            "create_tracks.dat", "railway-v1", NeoForgeRailwayImport.VERSION, NeoForgeRailwayImport::convert);
    }
    private static Train decoded(GameTestHelper h, NeoForgeSavedDataPairImport.Pair<RailwaySavedData, TrainTrafficSavedData> pair) {
        var traffic = pair.second().retained(); return Train.read(traffic.getList("Trains").orElseThrow().getCompoundOrEmpty(0), h.getLevel().registryAccess(), pair.first().getTrackNetworks(), DimensionPalette.read(traffic));
    }
    private static void stock(GameTestHelper h, Train train, int count) {
        var storage = train.carriages.getFirst().storage.getAllItemStorages().get(CHEST); var stack = storage.getStackInSlot(2);
        h.assertTrue(storage.getSlotCount() == 27 && stack.is(Items.DIAMOND) && stack.getCount() == count
                && stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("source-cargo").orElseThrow().equals("same original eleven"),
            "Actual native mounted storage retains the real sparse slot, all source counts and components");
    }
    private static void refuses(GameTestHelper h, Fixture fixture, Consumer<Fixture> corruption) throws Exception {
        corruption.accept(fixture);
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, fixture.source); byte[] original = Files.readAllBytes(directory.source()); boolean rejected = false;
            try { load(h, directory, storage); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
            var cache = ((SavedDataStorageAccessor) storage).create$getCache();
            h.assertTrue(rejected && cache.getOrDefault(RailwaySavedData.TYPE, java.util.Optional.empty()).isEmpty()
                    && cache.getOrDefault(TrainTrafficSavedData.TYPE, java.util.Optional.empty()).isEmpty()
                    && !Files.exists(directory.root.resolve("create/tracks.dat")) && !Files.exists(directory.root.resolve("create/trains.dat"))
                    && Arrays.equals(original, Files.readAllBytes(directory.source())), "Invalid original full-scope custody cannot publish either new owner or alter source bytes");
        }
    }


    private static final BlockPos TARGET=new BlockPos(4,4,4);
    private record Canonical(TrainManager manager,RailwayGraphManager graphs,com.simibubi.create.content.trains.graph.TrackGraph graph,Train train) implements AutoCloseable{
        @Override public void close(){
            for(var row:train.carriages){row.write(new DimensionPalette(),manager.server().registryAccess());var present=new java.util.ArrayList<CarriageContraptionEntity>();row.forEachPresentEntity(present::add);for(var front:present){for(var passenger:java.util.List.copyOf(front.getPassengers()))if(!(passenger instanceof net.minecraft.world.entity.player.Player))passenger.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);front.retireToCarriage(row);}}
            manager.remove(train.id);graphs.removeGraph(graph);
        }
    }
    private static void shiftPos(CompoundTag original,BlockPos delta,int factor){for(String axis:new String[]{"X","Y","Z"}){int value=axis.equals("X")?delta.getX():axis.equals("Y")?delta.getY():delta.getZ();original.putInt(axis,Math.addExact(original.getInt(axis).orElseThrow(),Math.multiplyExact(factor,value)));}}
    private static ListTag shiftVector(ListTag source,BlockPos delta){return vector(source.getDouble(0).orElseThrow()+delta.getX(),source.getDouble(1).orElseThrow()+delta.getY(),source.getDouble(2).orElseThrow()+delta.getZ());}
    private static Fixture source(GameTestHelper h,GameProfile realProfile,boolean nested){
        var fixture=fixture();var train=train(fixture);train.store("Owner",UUIDUtil.CODEC,realProfile.id());train.putDouble("Speed",0);train.putDouble("Throttle",0);train.putDouble("TargetSpeed",0);train.putBoolean("Backwards",false);
        var delta=h.absolutePos(TARGET).subtract(new BlockPos(0,64,0));var graph=fixture.source.getListOrEmpty("RailGraphs").getCompoundOrEmpty(0);
        for(var value:graph.getListOrEmpty("Nodes")){var node=(CompoundTag)value;shiftPos(node.getCompoundOrEmpty("Location").getCompoundOrEmpty("Pos"),delta,2);node.getCompoundOrEmpty("Location").putInt("D",0);}
        var carriage=carriage(fixture);for(var value:carriage.getCompoundOrEmpty("FirstBogey").getListOrEmpty("Points"))for(var node:((CompoundTag)value).getListOrEmpty("Nodes")){shiftPos(((CompoundTag)node).getCompoundOrEmpty("Pos"),delta,2);((CompoundTag)node).putInt("D",0);}
        var frame=frame(fixture,1).copy();frame.remove("Pivot");frame.putInt("DiscardTicks",0);frame.putFloat("Cutoff",0);frame.put("PositionAnchor",shiftVector(frame.getListOrEmpty("PositionAnchor"),delta));for(var value:frame.getListOrEmpty("RotationAnchors")){var row=(CompoundTag)value;row.put("V",shiftVector(row.getListOrEmpty("V"),delta));}carriage.put("EntityPositioning",values(frame));
        var entity=carriage.getCompoundOrEmpty("Entity");entity.put("Pos",shiftVector(entity.getListOrEmpty("Pos"),delta));shiftPos(entity.getCompoundOrEmpty("Contraption").getCompoundOrEmpty("Anchor"),delta,1);
        if(nested){var npc=new CompoundTag();npc.putString("id","minecraft:pig");npc.store("UUID",UUIDUtil.CODEC,UUID.randomUUID());npc.put("Pos",entity.get("Pos").copy());npc.put("Motion",vector(0,0,0));npc.put("Rotation",values(FloatTag.valueOf(0),FloatTag.valueOf(0)));npc.putFloat("FallDistance",0);npc.putShort("Fire",(short)-1);npc.putShort("Air",(short)300);npc.putBoolean("OnGround",false);npc.putBoolean("Invulnerable",false);npc.putInt("PortalCooldown",0);npc.putBoolean("NoGravity",true);npc.putBoolean("NoAI",true);npc.putFloat("Health",10);npc.putFloat("AbsorptionAmount",0);npc.putShort("HurtTime",(short)0);npc.putShort("DeathTime",(short)0);npc.putInt("HurtByTimestamp",0);npc.putBoolean("FallFlying",false);npc.put("attributes",new ListTag());var brain=new CompoundTag();brain.put("memories",new CompoundTag());npc.put("Brain",brain);npc.putBoolean("CanPickUpLoot",false);npc.putBoolean("PersistenceRequired",false);npc.putBoolean("LeftHanded",false);npc.putInt("Age",0);npc.putInt("ForcedAge",0);npc.putInt("InLove",0);npc.put("ArmorItems",values(new CompoundTag(),new CompoundTag(),new CompoundTag(),new CompoundTag()));npc.put("HandItems",values(new CompoundTag(),new CompoundTag()));carriage.getCompoundOrEmpty("Passengers").put("Seat0",npc);}
        return fixture;
    }
    private static Canonical canonical(GameTestHelper h,Fixture fixture){
        var dimensions=DimensionPalette.read(fixture.source);var graph=NeoForgeTrackGraphImport.convert(fixture.source.getListOrEmpty("RailGraphs").getCompoundOrEmpty(0),dimensions,3955,h.getLevel().registryAccess());var graphs=RailwayGraphManager.get(h.getLevel().getServer());graphs.putGraph(graph);var manager=TrainManager.get(h.getLevel());try{var train=NeoForgeTrainImport.convert(train(fixture),graphs.getTrackNetworks(),dimensions,3955,h.getLevel().registryAccess()).train();manager.add(train);return new Canonical(manager,graphs,graph,train);}catch(RuntimeException failure){graphs.removeGraph(graph);throw failure;}
    }
    private static CompoundTag actualOriginalPlayer(GameTestHelper h,GameProfile profile,Fixture fixture,boolean nested){
        var source=NeoForgePlayerDataGameTests.original(h,profile.id());var root=carriage(fixture).getCompoundOrEmpty("Entity").copy();UUID seatRoot=profile.id(),attach=fixture.body;
        if(nested){var npc=carriage(fixture).getCompoundOrEmpty("Passengers").getCompoundOrEmpty("Seat0").copy();seatRoot=npc.read("UUID",UUIDUtil.CODEC).orElseThrow();attach=seatRoot;root.put("Passengers",values(npc));}
        var seat=new CompoundTag();seat.store("Id",UUIDUtil.CODEC,seatRoot);seat.putInt("Seat",0);root.getCompoundOrEmpty("Contraption").put("Passengers",values(seat));var vehicle=new CompoundTag();vehicle.store("Attach",UUIDUtil.CODEC,attach);vehicle.put("Entity",root);source.put("RootVehicle",vehicle);source.put("Pos",root.get("Pos").copy());return source;
    }
    private enum Action { VOLUNTARY_STOP, NATIVE_LOGOUT, NATIVE_FRONT_UNLOAD }
    private static boolean hasDirectReference(GameTestHelper h,Canonical canonical,UUID actualPlayer){
        var saved=canonical.train.carriages.getFirst().write(new DimensionPalette(),h.getLevel().registryAccess());
        for(var value:saved.getCompoundOrEmpty("Passengers").values())if(value instanceof CompoundTag tag){var graph=CarriagePassengerGraph.fromLegacy(tag,UUIDUtil.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,saved.getCompoundOrEmpty("Entity").get("UUID")).getOrThrow());if(graph.root().equals(actualPlayer)&&graph.node(actualPlayer) instanceof CarriagePassengerGraph.PlayerReference)return true;}
        return false;
    }
    private static void exercise(GameTestHelper h,Action action)throws Exception{
        var actualSpawn=h.absolutePos(TARGET);int chunkX=actualSpawn.getX()>>4,chunkZ=actualSpawn.getZ()>>4;
        for(int x=chunkX-3;x<=chunkX+3;x++)for(int z=chunkZ-3;z<=chunkZ+3;z++)h.getLevel().getChunk(x,z);
        for(int x=2;x<=16;x++)for(int z=2;z<=6;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        var profile=new GameProfile(UUID.randomUUID(),"direct_"+UUID.randomUUID().toString().substring(0,7));var fixture=source(h,profile,false);var canonical=canonical(h,fixture);var row=canonical.train.carriages.getFirst();var cargo=row.storage;
        var storage=NeoForgePlayerStorageImport.actualStorage(h.getLevel().getServer());var directory=((PlayerDataStorageAccessor)storage).create$getPlayerDirectory().toPath();var file=directory.resolve(profile.id()+".dat");
        h.assertTrue(!Files.exists(file)&&!Files.exists(directory.resolve(profile.id()+".dat_old")),"Actual source profile starts with fresh real PlayerDataStorage paths");var source=actualOriginalPlayer(h,profile,fixture,false);NbtIo.writeCompressed(source,file);
        var first=new AtomicReference<CarriagePlayerOwnerGameTests.Rejoin>();var second=new AtomicReference<CarriagePlayerOwnerGameTests.Rejoin>();var joined=new AtomicReference<CarriagePlayerOwnerGameTests.Joined>();var initialFront=new AtomicReference<CarriageContraptionEntity>();var cleaned=new AtomicBoolean();
        Runnable cleanup=()->{if(!cleaned.compareAndSet(false,true))return;try{if(second.get()!=null)second.get().close();if(first.get()!=null)first.get().close();canonical.close();try(var files=Files.list(directory)){for(var path:files.toList())if(path.getFileName().toString().startsWith(profile.id().toString()))Files.deleteIfExists(path);}}catch(Exception failure){throw new IllegalStateException(failure);}};
        ((GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new GameTestListener(){
            public void testStructureLoaded(GameTestInfo info){}
            public void testPassed(GameTestInfo info,GameTestRunner runner){cleanup.run();}
            public void testFailed(GameTestInfo info,GameTestRunner runner){cleanup.run();}
            public void testAddedForRerun(GameTestInfo old,GameTestInfo next,GameTestRunner runner){cleanup.run();}
        });
        h.startSequence().thenExecute(()->first.set(new CarriagePlayerOwnerGameTests.Rejoin(h,profile))).thenWaitUntil(()->first.get().requireReady()).thenExecute(()->joined.set(first.get().spawn())).thenWaitUntil(()->{
            var player=joined.get().player();var front=row.anyAvailableEntity();
            h.assertTrue(front!=null&&front.isGloballyManaged()&&front.getCarriage()==row&&h.getLevel().getEntity(fixture.body)==front&&player.getVehicle()==front&&front.getPassengers().stream().anyMatch(value->value==player),"Actual original rail file is consumed by real native login into one current canonical direct parent edge");
            h.assertTrue(((NeoForgeRailwayPlayerReference.Holder)player).create$getOriginalRailwayReference()==null&&CarriagePlayerReferences.peekCurrent(h.getLevel().getServer()).link(profile.id())==null,"Consumed original intent and a normal direct seat have no manufactured nested journal/token");
            h.assertTrue(hasDirectReference(h,canonical,profile.id()),"Real current Carriage.write captures the actual direct source reference before testing its later lifecycle");initialFront.set(front);stock(h,canonical.train,11);canonical.manager.validateCustody();
        }).thenExecute(()->{
            var player=joined.get().player();
            if(action==Action.VOLUNTARY_STOP){
                player.stopRiding();h.assertTrue(!player.isPassenger()&&!hasDirectReference(h,canonical,profile.id()),"Bare genuine voluntary native stopRiding immediately supersedes the previously captured plain direct reference");
            }else if(action==Action.NATIVE_LOGOUT){
                // Joined.close invokes the real PlayerList.remove directly. Rejoin.close
                // first performs a voluntary stopRiding, so it is deliberately not this trigger.
                joined.get().close();first.get().close();first.set(null);joined.set(null);
                h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(profile.id())==null&&hasDirectReference(h,canonical,profile.id()),"Actual native PlayerList logout preserves its existing captured direct reference without creating one from absent source data");
                second.set(new CarriagePlayerOwnerGameTests.Rejoin(h,profile));
            }else{
                var front=initialFront.get();h.assertTrue(front.prepareNativePassengerUnload(),"The actual Carriage lifecycle prepares native frontend retirement and its exact direct source edge");front.retireToCarriage(row);
                h.assertTrue(front.isRemoved()&&!player.isPassenger()&&hasDirectReference(h,canonical,profile.id()),"Native source retirement preserves its just-captured plain direct reference; it is not a voluntary detach");
            }
        }).thenWaitUntil(()->{
            if(action==Action.NATIVE_LOGOUT)second.get().requireReady();
        }).thenExecute(()->{if(action==Action.NATIVE_LOGOUT)joined.set(second.get().spawn());}).thenIdle(3).thenWaitUntil(()->{
            var player=joined.get().player();var front=row.anyAvailableEntity();
            if(action==Action.VOLUNTARY_STOP){
                h.assertTrue(!player.isPassenger()&&!hasDirectReference(h,canonical,profile.id())&&front==initialFront.get(),"Ordinary actual Carriage ticks cannot reboard a later voluntarily detached direct player");
            }else if(action==Action.NATIVE_LOGOUT){
                h.assertTrue(front==initialFront.get()&&player.getVehicle()==front,"Real native rejoin consumes the preserved ordinary direct reference on the same original canonical frontend");
            }else{
                h.assertTrue(front!=null&&front!=initialFront.get()&&front.getUUID().equals(fixture.body)&&player.getVehicle()==front,"Ordinary native Carriage ticks restore one new frontend with its exact original body UUID and preserved actual direct source player");
            }
            h.assertTrue(row.storage==cargo&&player.getInventory().getItem(0).is(Items.DIAMOND)&&player.getInventory().getItem(0).getCount()==11&&((NeoForgeRailwayPlayerReference.Holder)player).create$getOriginalRailwayReference()==null&&CarriagePlayerReferences.peekCurrent(h.getLevel().getServer()).link(profile.id())==null,"All three native transitions retain one real cargo manager, original player inventory11 and no replayed original marker or invented nested relationship");stock(h,canonical.train,11);canonical.manager.validateCustody();
        }).thenExecute(()->{
            if(action==Action.VOLUNTARY_STOP){
                first.get().close();first.set(null);second.set(new CarriagePlayerOwnerGameTests.Rejoin(h,profile));
            }
        }).thenWaitUntil(()->{if(action==Action.VOLUNTARY_STOP)second.get().requireReady();}).thenExecute(()->{if(action==Action.VOLUNTARY_STOP)joined.set(second.get().spawn());}).thenIdle(3).thenExecute(()->{
            if(action==Action.VOLUNTARY_STOP)h.assertTrue(!joined.get().player().isPassenger()&&row.anyAvailableEntity()==initialFront.get()&&!hasDirectReference(h,canonical,profile.id()),"A fresh actual PlayerData save/rejoin cannot replay a direct reference already superseded by bare voluntary native unlink");
        }).thenExecute(cleanup).thenSucceed();
    }
    @GameTest(structure="create_test:reservoir",maxTicks=240)public void actualBareDirectStopRidingSupersedesCapturedReferenceAcrossNativeTicksAndRealRejoin(GameTestHelper h)throws Exception{exercise(h,Action.VOLUNTARY_STOP);}
    @GameTest(structure="create_test:reservoir",maxTicks=240)public void actualPlayerListLogoutPreservesExistingCapturedDirectReferenceAcrossRealRejoin(GameTestHelper h)throws Exception{exercise(h,Action.NATIVE_LOGOUT);}
    @GameTest(structure="create_test:reservoir",maxTicks=240)public void actualCanonicalFrontendRetirementPreservesExactDirectSourceWithoutNestedToken(GameTestHelper h)throws Exception{exercise(h,Action.NATIVE_FRONT_UNLOAD);}
}
