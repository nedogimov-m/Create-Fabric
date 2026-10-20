package com.simibubi.create.test;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.world.entity.Entity;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.graph.NeoForgeTrackGraphImport;
import com.simibubi.create.foundation.utility.NeoForgePlayerStorageImport;
import com.simibubi.create.foundation.mixin.accessor.PlayerDataStorageAccessor;
import com.mojang.authlib.GameProfile;
import java.util.concurrent.atomic.AtomicReference;
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

/** Actual source core custom NPC tree, paired SavedData and one real canonical Carriage. Authored3 UNRUN. */
public final class NeoForgeRailwayCustomPassengerGameTests {
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
        @Override public void close(){for(var row:train.carriages){var present=new java.util.ArrayList<CarriageContraptionEntity>();row.forEachPresentEntity(present::add);for(var front:present){if(!front.isRemoved()&&!front.prepareNativePassengerUnload())throw new IllegalStateException("Real canonical passenger retirement refused");front.retireToCarriage(row);}}manager.remove(train.id);graphs.removeGraph(graph);}
    }
    private static void shiftPos(CompoundTag original,BlockPos delta,int factor){for(String axis:new String[]{"X","Y","Z"}){int value=axis.equals("X")?delta.getX():axis.equals("Y")?delta.getY():delta.getZ();original.putInt(axis,Math.addExact(original.getInt(axis).orElseThrow(),Math.multiplyExact(factor,value)));}}
    private static ListTag shiftVector(ListTag source,BlockPos delta){return vector(source.getDouble(0).orElseThrow()+delta.getX(),source.getDouble(1).orElseThrow()+delta.getY(),source.getDouble(2).orElseThrow()+delta.getZ());}
    private static Fixture located(GameTestHelper h){
        var fixture=fixture();var train=train(fixture);train.putDouble("Speed",0);train.putDouble("Throttle",0);train.putDouble("TargetSpeed",0);train.putBoolean("Backwards",false);
        var delta=h.absolutePos(TARGET).subtract(new BlockPos(0,64,0));var graph=fixture.source.getListOrEmpty("RailGraphs").getCompoundOrEmpty(0);
        for(var value:graph.getListOrEmpty("Nodes")){var node=(CompoundTag)value;shiftPos(node.getCompoundOrEmpty("Location").getCompoundOrEmpty("Pos"),delta,2);node.getCompoundOrEmpty("Location").putInt("D",0);}
        var carriage=carriage(fixture);for(var value:carriage.getCompoundOrEmpty("FirstBogey").getListOrEmpty("Points"))for(var node:((CompoundTag)value).getListOrEmpty("Nodes")){shiftPos(((CompoundTag)node).getCompoundOrEmpty("Pos"),delta,2);((CompoundTag)node).putInt("D",0);}
        var frame=frame(fixture,1).copy();frame.remove("Pivot");frame.putInt("DiscardTicks",0);frame.putFloat("Cutoff",0);frame.put("PositionAnchor",shiftVector(frame.getListOrEmpty("PositionAnchor"),delta));for(var value:frame.getListOrEmpty("RotationAnchors")){var row=(CompoundTag)value;row.put("V",shiftVector(row.getListOrEmpty("V"),delta));}carriage.put("EntityPositioning",values(frame));
        var entity=carriage.getCompoundOrEmpty("Entity");entity.put("Pos",shiftVector(entity.getListOrEmpty("Pos"),delta));shiftPos(entity.getCompoundOrEmpty("Contraption").getCompoundOrEmpty("Anchor"),delta,1);

        return fixture;
    }
    private static Canonical canonical(GameTestHelper h,Fixture fixture){
        var dimensions=DimensionPalette.read(fixture.source);var graph=NeoForgeTrackGraphImport.convert(fixture.source.getListOrEmpty("RailGraphs").getCompoundOrEmpty(0),dimensions,3955,h.getLevel().registryAccess());var graphs=RailwayGraphManager.get(h.getLevel().getServer());graphs.putGraph(graph);var manager=TrainManager.get(h.getLevel());try{var train=NeoForgeTrainImport.convert(train(fixture),graphs.getTrackNetworks(),dimensions,3955,h.getLevel().registryAccess()).train();manager.add(train);return new Canonical(manager,graphs,graph,train);}catch(RuntimeException failure){graphs.removeGraph(graph);throw failure;}
    }
    private static CompoundTag parcel(GameTestHelper h, UUID id) {
        var result = new CompoundTag(); result.putString("id", "create:package"); result.store("UUID", UUIDUtil.CODEC, id); var pos = h.absolutePos(TARGET);
        result.put("Pos", vector(pos.getX() + .5, pos.getY(), pos.getZ() + .5)); result.put("Motion", vector(0, 0, 0)); result.put("Rotation", values(FloatTag.valueOf(0), FloatTag.valueOf(0)));
        result.putFloat("FallDistance", 0); result.putShort("Fire", (short) -1); result.putShort("Air", (short) 300); result.putBoolean("OnGround", false); result.putBoolean("Invulnerable", false); result.putInt("PortalCooldown", 0); result.putBoolean("NoGravity", true);
        result.putFloat("Health", 4); result.putFloat("AbsorptionAmount", 0); result.putShort("HurtTime", (short) 0); result.putInt("HurtByTimestamp", 17); result.putShort("DeathTime", (short) 0); result.putBoolean("FallFlying", false);
        var health = new CompoundTag(); health.putString("id", "minecraft:generic.max_health"); health.putDouble("base", 5); result.put("attributes", values(health)); var brain = new CompoundTag(); brain.put("memories", new CompoundTag()); result.put("Brain", brain);
        var box = new CompoundTag(); box.putString("id", "create:cardboard_package_12x12"); box.putInt("count", 1); var item = stack(11); var row = new CompoundTag(); row.putInt("slot", 0); row.put("item", item); var components = new CompoundTag(); components.put("create:package_contents", values(row)); box.put("components", components); result.put("Box", box); return result;
    }
    private static CompoundTag pig(GameTestHelper h, UUID id) {
        var result = parcel(h, id); result.putString("id", "minecraft:pig"); result.remove("Box"); result.putFloat("Health", 10); result.put("attributes", new ListTag());
        result.put("ArmorItems", values(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag())); result.put("HandItems", values(new CompoundTag(), new CompoundTag()));
        result.putBoolean("NoAI", true); result.putBoolean("CanPickUpLoot", false); result.putBoolean("PersistenceRequired", true); result.putBoolean("LeftHanded", false); result.putInt("Age", 0); result.putInt("ForcedAge", 0); result.putInt("InLove", 0); return result;
    }
    private static CompoundTag rider(Fixture fixture) { return carriage(fixture).getCompoundOrEmpty("Passengers").getCompoundOrEmpty("Seat0"); }
    private static void addTree(GameTestHelper h, Fixture fixture, UUID root, UUID child) {
        var original = parcel(h, root); original.put("Passengers", values(pig(h, child))); carriage(fixture).getCompoundOrEmpty("Passengers").put("Seat0", original);
    }
    @GameTest public void completeCorePackageTreeReachesPairedSavedOwnersWithOneBoxAndExactOriginalEdges(GameTestHelper h) throws Exception {
        var fixture = fixture(); UUID root = UUID.randomUUID(), child = UUID.randomUUID(); addTree(h, fixture, root, child); var before = fixture.source.copy();
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, fixture.source); var pair = load(h, directory, storage); var actual = decoded(h, pair); var row = actual.carriages.getFirst();
            var snapshot = pair.second().retained().getListOrEmpty("Trains").getCompoundOrEmpty(0).getListOrEmpty("Carriages").getCompoundOrEmpty(0).getCompoundOrEmpty("Passengers").getCompoundOrEmpty("Seat0");
            var graph = CarriagePassengerGraph.fromLegacy(snapshot, fixture.body); var originalGraph = row.custodyIdentities();
            h.assertTrue(fixture.source.equals(before) && originalGraph.passengers().equals(java.util.Set.of(root, child)) && graph.node(root) instanceof CarriagePassengerGraph.Npc
                && graph.node(child) instanceof CarriagePassengerGraph.Npc && h.getLevel().getEntity(root) == null && h.getLevel().getEntity(child) == null,
                "Complete genuine package/pig UUID graph is admitted only as the canonical Train proposal; migration never spawns an independent body or NPC clone");
            stock(h, actual, 11); var node = (CarriagePassengerGraph.Npc) graph.node(root); var encoded = node.payload().orElseThrow();
            var box = net.minecraft.world.item.ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), encoded.get("Box")).getOrThrow();
            h.assertTrue(box.getCount() == 1 && PackageItem.getContents(box).getFirst().getCount() == 11 && graph.node(child).parent().orElseThrow().equals(root) && graph.node(child).order() == 0,
                "The original package Box and nested native passenger order survive apart from the one original canonical cargo manager's eleven diamonds");
        } h.succeed();
    }

    @GameTest(structure="create_test:reservoir", maxTicks=240) public void ordinaryCanonicalCarriageLoadsAndRetiresCorePackageTreeWithoutCargoOrNpcCopies(GameTestHelper h) {
        for (int x=2;x<=16;x++) for(int z=2;z<=6;z++) h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        var fixture = located(h); UUID root = UUID.randomUUID(), child = UUID.randomUUID(); addTree(h, fixture, root, child); var canonical = canonical(h, fixture); var row = canonical.train.carriages.getFirst(); var cargo = row.storage;
        var previous = new AtomicReference<CarriageContraptionEntity>(); var previousPackage = new AtomicReference<Entity>(); var previousChild = new AtomicReference<Entity>();
        h.startSequence().thenWaitUntil(() -> {
            var front = row.anyAvailableEntity(); var parcel = h.getLevel().getEntity(root); var npc = h.getLevel().getEntity(child);
            h.assertTrue(front != null && front.isGloballyManaged() && front.getCarriage() == row && front.getUUID().equals(fixture.body) && parcel instanceof PackageEntity && parcel.getVehicle() == front
                && npc != null && npc.getVehicle() == parcel && front.getPassengers().size() == 1 && parcel.getPassengers().size() == 1,
                "Ordinary canonical Carriage ticks create one actual package and its exact original child under the one real frontend");
            h.assertTrue(row.storage == cargo && PackageItem.getContents(((PackageEntity)parcel).getBox()).getFirst().getCount() == 11, "Original Box11 is separate from the same canonical cargo11 manager"); stock(h, canonical.train, 11); canonical.manager.validateCustody(); previous.set(front); previousPackage.set(parcel); previousChild.set(npc);
        }).thenExecute(() -> {
            var front = previous.get(); h.assertTrue(front.prepareNativePassengerUnload(), "Actual canonical lifecycle captures and retires its real NPC tree before native frontend unload"); front.retireToCarriage(row);
            h.assertTrue(front.isRemoved() && previousPackage.get().isRemoved() && previousChild.get().isRemoved() && row.storage == cargo, "Real unload retires every actual old NPC object without replacing the canonical cargo manager");
        }).thenWaitUntil(() -> {
            var front = row.anyAvailableEntity(); var parcel = h.getLevel().getEntity(root); var npc = h.getLevel().getEntity(child);
            h.assertTrue(front != null && front != previous.get() && front.getUUID().equals(fixture.body) && front.getCarriage() == row && parcel instanceof PackageEntity && parcel != previousPackage.get() && npc != null && npc != previousChild.get()
                && parcel.getVehicle() == front && npc.getVehicle() == parcel && row.storage == cargo && PackageItem.getContents(((PackageEntity)parcel).getBox()).getFirst().getCount() == 11,
                "The same actual Carriage reloads exactly the original UUID tree from native captured state with one frontend, one Box and the same cargo owner");
            stock(h, canonical.train, 11); canonical.manager.validateCustody();
        }).thenExecute(canonical::close).thenSucceed();
    }

    @GameTest public void customPassengerOwnerViolationsRefuseBeforeEitherNativeSavedOwner(GameTestHelper h) throws Exception {
        refuses(h, fixture(), f -> { addTree(h, f, UUID.randomUUID(), UUID.randomUUID()); rider(f).putInt("FuturePaidStock", 11); });
        refuses(h, fixture(), f -> { addTree(h, f, UUID.randomUUID(), UUID.randomUUID()); rider(f).putString("id", "other:unreviewed"); });
        refuses(h, fixture(), f -> { addTree(h, f, UUID.randomUUID(), UUID.randomUUID()); rider(f).getListOrEmpty("Passengers").getCompoundOrEmpty(0).putString("id", "create:carriage_contraption"); });
        refuses(h, fixture(), f -> { addTree(h, f, UUID.randomUUID(), UUID.randomUUID()); rider(f).getListOrEmpty("Passengers").getCompoundOrEmpty(0).putString("id", "create:contraption"); });
        refuses(h, fixture(), f -> { addTree(h, f, UUID.randomUUID(), UUID.randomUUID()); var attachment = new CompoundTag(); attachment.put("create:minecart_controller", new CompoundTag()); rider(f).getListOrEmpty("Passengers").getCompoundOrEmpty(0).put("neoforge:attachments", attachment); });
        refuses(h, fixture(), f -> addTree(h, f, f.body, UUID.randomUUID()));
        refuses(h, fixture(), f -> { UUID same = UUID.randomUUID(); addTree(h, f, same, same); }); h.succeed();
    }
}
