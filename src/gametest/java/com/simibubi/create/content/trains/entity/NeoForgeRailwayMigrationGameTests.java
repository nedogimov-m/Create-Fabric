package com.simibubi.create.content.trains.entity;

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

/** Actual source serialization shapes, genuine native codecs/DFU/storage; every authored case UNRUN. */
public final class NeoForgeRailwayMigrationGameTests {
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
        state.getValues().forEach(entry -> putProperty(properties, entry.property(), entry.value()));
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
    /** Reuse the complete genuine source body/graph owner before testing direct migrated cargo transfers. */
    public static Train actualMigratedCargoOwner(GameTestHelper h,CompoundTag originalCargo) throws IOException {
        var fixture=fixture();
        for(int index=0;index<2;index++){
            var actualFrame=frame(fixture,index);
            for(var key:List.of("items","fluids","interactable_positions","TicksSinceLastExchange"))actualFrame.remove(key);
            for(var key:originalCargo.keySet())actualFrame.put(key,originalCargo.get(key).copy());
        }
        var actualSourceItem=originalCargo.getListOrEmpty("items").getCompoundOrEmpty(0).getCompoundOrEmpty("storage").getCompoundOrEmpty("value").getCompoundOrEmpty("items").getCompoundOrEmpty("0").copy();
        actualSourceItem.putByte("Slot",(byte)0);
        var originalChest=body(fixture).getCompoundOrEmpty("Blocks").getListOrEmpty("BlockList").getCompoundOrEmpty(1).getCompoundOrEmpty("Data");
        originalChest.put("Items",values(actualSourceItem));
        try(var directory=Directory.create();var storage=directory.storage(h)){
            source(directory,fixture.source);
            return decoded(h,load(h,directory,storage));
        }
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

    @GameTest public void originalNonemptyScopeMigratesOneActualCargoOwnerAcrossBothNativeSavedDataFiles(GameTestHelper h) throws Exception {
        var fixture = fixture(); var input = fixture.source.copy();
        try (var directory = Directory.create()) {
            source(directory, fixture.source); byte[] old = Files.readAllBytes(directory.source());
            try (var storage = directory.storage(h)) {
                var pair = load(h, directory, storage); var train = decoded(h, pair); stock(h, train, 11);
                h.assertTrue(train.id.equals(fixture.train) && train.carriages.getFirst().custodyIdentities().bodies().contains(fixture.body)
                        && train.graph == pair.first().getTrackNetworks().get(fixture.graph) && train.graph.manager() == null && train.trafficManager() == null
                        && train.name.getString().equals("Original rail") && train.speed == .125 && train.throttle == .75 && train.fuelTicks == 123 && train.currentlyBackwards,
                    "Original persistent graph/Train/body IDs, native component DFU, motion/fuel and exact detached model references survive");
                h.assertTrue(storage.get(RailwaySavedData.TYPE) == pair.first() && storage.get(TrainTrafficSavedData.TYPE) == pair.second(), "Both actual native SavedData cache owners are installed together");
            }
            try (var storage = directory.storage(h)) { stock(h, decoded(h, load(h, directory, storage)), 11); }
            h.assertTrue(Arrays.equals(old, Files.readAllBytes(directory.source())) && input.equals(fixture.source), "Original compressed file and source model stay byte/value identical through native reload");
        }
        h.succeed();
    }

    @GameTest public void dimensionalCopiesBecomeOneCargoAndPreserveActualPivotDimensionsAndAnchors(GameTestHelper h) {
        var fixture = fixture(); var dimensions = DimensionPalette.read(fixture.source); var result = NeoForgeCarriageCargoImport.convert(carriage(fixture), dimensions, 3955, h.getLevel().registryAccess());
        h.assertTrue(result.cargo().getInt("TicksSinceLastExchange").orElseThrow() == 37 && result.cargo().getList("items").orElseThrow().size() == 1, "Two equal source copies establish one native cargo manager, never twice the stock");
        for (Tag value : result.positioning()) {
            var frame = (CompoundTag) value; var dimension = dimensions.decode(frame.getInt("Dim").orElseThrow());
            h.assertTrue(!frame.contains("items") && !frame.contains("fluids") && !frame.contains("TicksSinceLastExchange")
                    && frame.getCompound("Pivot").orElseThrow().getString("Dimension").orElseThrow().equals(dimension.identifier().toString())
                    && frame.getList("RotationAnchors").orElseThrow().getCompoundOrEmpty(1).getDouble("X").orElseThrow() == 1.5,
                "Actual row dimension supplies the dimensionless original pivot; original V vectors become genuine native XYZ anchors");
        }
        h.succeed();
    }

    @GameTest public void disagreeingOriginalCargoCopiesFailBeforeEitherOwner(GameTestHelper h) throws Exception { refuses(h, fixture(), value -> stock(value, 1).putInt("count", 12)); h.succeed(); }
    @GameTest public void absentOriginalCargoOwnerCannotBecomeInventedEmptyManager(GameTestHelper h) throws Exception { refuses(h, fixture(), value -> carriage(value).put("EntityPositioning", new ListTag())); h.succeed(); }
    @GameTest public void duplicateOriginalDimensionAndOutsideBodyCargoFailBeforePublication(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> frame(value, 1).putInt("Dim", 1));
        refuses(h, fixture(), value -> { for (int i = 0; i < 2; i++) frame(value, i).getList("items").orElseThrow().getCompoundOrEmpty(0).put("pos", xyz(new BlockPos(99, 0, 0))); }); h.succeed();
    }
    @GameTest public void actualBodyUuidTrainOwnerAndBogeyGeometryMustMatch(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> carriage(value).getCompound("Entity").orElseThrow().store("TrainId", UUIDUtil.CODEC, UUID.randomUUID()));
        refuses(h, fixture(), value -> carriage(value).getCompound("FirstBogey").orElseThrow().putString("Type", "create:large_bogey")); h.succeed();
    }
    @GameTest public void unknownBodySourceFieldsAndUnknownNativeItemComponentsRefuse(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> body(value).putInt("FutureBodyVersion", 9));
        refuses(h, fixture(), value -> { for (int i = 0; i < 2; i++) stock(value, i).getCompound("components").orElseThrow().putString("other:unreviewed", "payload"); }); h.succeed();
    }
    @GameTest public void malformedMotionFormationAndMissingGraphCannotDefault(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> train(value).putDouble("Throttle", Double.NaN));
        refuses(h, fixture(), value -> train(value).putIntArray("CarriageSpacing", new int[] {7}));
        refuses(h, fixture(), value -> train(value).store("Graph", UUIDUtil.CODEC, UUID.randomUUID())); h.succeed();
    }
    @GameTest public void duplicateGlobalBodyAndTrainIdsCannotPublishPartialScopes(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> value.source.getList("Trains").orElseThrow().add(train(value).copy()));
        refuses(h, fixture(), value -> {
            var second = train(value).copy(); var id = UUID.randomUUID(); second.store("Id", UUIDUtil.CODEC, id);
            second.getList("Carriages").orElseThrow().getCompoundOrEmpty(0).getCompound("Entity").orElseThrow().store("TrainId", UUIDUtil.CODEC, id); value.source.getList("Trains").orElseThrow().add(second);
        }); h.succeed();
    }
    @GameTest public void absentFallbackOrOrphanSignalGroupCannotCreateDefaultOwnership(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> value.source.put("SignalBlocks", new ListTag()));
        refuses(h, fixture(), value -> { var group = value.source.getList("SignalBlocks").orElseThrow().getCompoundOrEmpty(0).copy(); group.store("Id", UUIDUtil.CODEC, UUID.randomUUID()); group.putBoolean("Fallback", false); value.source.getList("SignalBlocks").orElseThrow().add(group); }); h.succeed();
    }
    private static CompoundTag npc(String id, UUID uuid) { var result = new CompoundTag(); result.putString("id", id); result.store("UUID", UUIDUtil.CODEC, uuid); result.put("Pos", vector(0, 65, 0)); result.put("Motion", vector(0, 0, 0)); result.put("Rotation", values(FloatTag.valueOf(0), FloatTag.valueOf(0)));
        result.putFloat("FallDistance", 0); result.putShort("Fire", (short) 0); result.putShort("Air", (short) 300); result.putBoolean("OnGround", false); result.putBoolean("Invulnerable", false); result.putInt("PortalCooldown", 0);
        result.put("ArmorItems", values(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag())); result.put("HandItems", values(new CompoundTag(), new CompoundTag()));
        if (id.equals("minecraft:allay")) result.put("Inventory", new ListTag()); return result; }
    @GameTest public void originalNestedNpcTreeRetainsExactUuidParentOrderWithoutEntityFactories(GameTestHelper h) throws Exception {
        var fixture = fixture(); UUID root = UUID.randomUUID(), child = UUID.randomUUID(); var passenger = npc("minecraft:pig", root); passenger.put("Passengers", values(npc("minecraft:parrot", child))); carriage(fixture).getCompound("Passengers").orElseThrow().put("Seat0", passenger);
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, fixture.source); var actual = decoded(h, load(h, directory, storage)); var custody = actual.carriages.getFirst().custodyIdentities();
            h.assertTrue(custody.passengers().containsAll(List.of(root, child)) && custody.passengers().size() == 2 && custody.bodies().contains(fixture.body), "Vanilla ENTITY_TREE DFU/native custody preserves every original NPC UUID without spawning or replacing riders"); stock(h, actual, 11);
        }
        h.succeed();
    }
    @GameTest public void actualDirectPlayerReferenceRemainsOnlyItsOriginalUuid(GameTestHelper h) throws Exception {
        var fixture = fixture(); UUID player = UUID.randomUUID(); var reference = new CompoundTag(); reference.store("PlayerPassenger", UUIDUtil.CODEC, player); carriage(fixture).getCompound("Passengers").orElseThrow().put("Seat0", reference);
        try (var directory = Directory.create(); var storage = directory.storage(h)) {
            source(directory, fixture.source); var pair = load(h, directory, storage); var actual = decoded(h, pair);
            var row = pair.second().retained().getList("Trains").orElseThrow().getCompoundOrEmpty(0).getList("Carriages").orElseThrow().getCompoundOrEmpty(0).getCompound("Passengers").orElseThrow().getCompound("Seat0").orElseThrow();
            var graph = CarriagePassengerGraph.fromLegacy(row, fixture.body);
            h.assertTrue(graph.nodes().size() == 1 && graph.node(player) instanceof CarriagePassengerGraph.PlayerReference && graph.players().equals(java.util.Set.of(player))
                    && actual.carriages.getFirst().custodyIdentities().passengers().contains(player), "Source direct player reference gains no invented entity payload, relationship epoch or paid receipt");
        }
        h.succeed();
    }
    @GameTest public void bodyPassengerCollisionAndOriginalPlayerEntityPayloadAreRejected(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> carriage(value).getCompound("Passengers").orElseThrow().put("Seat0", npc("minecraft:pig", value.body)));
        refuses(h, fixture(), value -> carriage(value).getCompound("Passengers").orElseThrow().put("Seat0", npc("minecraft:player", UUID.randomUUID()))); h.succeed();
    }
    @GameTest public void malformedNpcEquipmentComponentsAndInventoriesRefuseBeforeEitherSavedOwner(GameTestHelper h) throws Exception {
        refuses(h, fixture(), value -> {
            var passenger = npc("minecraft:pig", UUID.randomUUID()); var stack = stack(11);
            stack.getCompound("components").orElseThrow().putString("other:unreviewed", "cannot disappear into EMPTY");
            passenger.put("HandItems", values(stack, new CompoundTag()));
            carriage(value).getCompound("Passengers").orElseThrow().put("Seat0", passenger);
        });
        refuses(h, fixture(), value -> {
            var passenger = npc("minecraft:allay", UUID.randomUUID()); passenger.put("Inventory", values(stack(11), stack(1)));
            carriage(value).getCompound("Passengers").orElseThrow().put("Seat0", passenger);
        });
        refuses(h, fixture(), value -> {
            var passenger = npc("minecraft:pig", UUID.randomUUID()); passenger.remove("HandItems");
            carriage(value).getCompound("Passengers").orElseThrow().put("Seat0", passenger);
        }); h.succeed();
    }

    @GameTest public void unknownScheduleInstructionsConditionsAndEnumsNeverUseNativeFallbacks(GameTestHelper h) throws Exception {
        var schedule = new CompoundTag(); schedule.putBoolean("Cyclic", true); var entry = new CompoundTag(); var instruction = new CompoundTag(); instruction.putString("Id", "other:unreviewed"); instruction.put("Data", new CompoundTag()); entry.put("Instruction", instruction); entry.put("Conditions", new ListTag()); schedule.put("Entries", values(entry));
        refuses(h, fixture(), value -> { var runtime = train(value).getCompound("Runtime").orElseThrow(); runtime.put("Schedule", schedule); runtime.putIntArray("TransitTimes", new int[] {-1}); });
        var validInstruction = new CompoundTag(); validInstruction.putString("Id", "create:destination"); var destination = new CompoundTag(); destination.putString("Text", "Original*"); validInstruction.put("Data", destination);
        entry.put("Instruction", validInstruction); var condition = new CompoundTag(); condition.putString("Id", "other:unreviewed"); condition.put("Data", new CompoundTag()); entry.put("Conditions", values(values(condition)));
        refuses(h, fixture(), value -> { var runtime = train(value).getCompound("Runtime").orElseThrow(); runtime.put("Schedule", schedule.copy()); runtime.putIntArray("TransitTimes", new int[] {-1}); });
        condition.putString("Id", "create:delay"); var delay = new CompoundTag(); delay.putInt("Value", 5); delay.putInt("TimeUnit", -1); condition.put("Data", delay);
        refuses(h, fixture(), value -> { var runtime = train(value).getCompound("Runtime").orElseThrow(); runtime.put("Schedule", schedule.copy()); runtime.putIntArray("TransitTimes", new int[] {-1}); });
        refuses(h, fixture(), value -> train(value).getCompound("Runtime").orElseThrow().putString("State", "FUTURE_STATE")); h.succeed();
    }
    @GameTest public void sourceScheduleItemFiltersKeepComponentsAndRuntimeContext(GameTestHelper h) {
        var instruction = new CompoundTag(); instruction.putString("Id", "create:destination"); var destination = new CompoundTag(); destination.putString("Text", "Original*"); instruction.put("Data", destination);
        var condition = new CompoundTag(); condition.putString("Id", "create:item_threshold"); var data = new CompoundTag(); data.putInt("Operator", 0); data.putString("Threshold", "11"); data.putInt("Measure", 0); var filter = stack(1); data.put("Item", filter); condition.put("Data", data);
        var entry = new CompoundTag(); entry.put("Instruction", instruction); entry.put("Conditions", values(values(condition))); var schedule = new CompoundTag(); schedule.putBoolean("Cyclic", true); schedule.put("Entries", values(entry));
        var runtime = runtime(); runtime.put("Schedule", schedule); runtime.putString("State", "POST_TRANSIT"); runtime.putIntArray("TransitTimes", new int[] {123}); runtime.putIntArray("ConditionProgress", new int[] {0}); var context = new CompoundTag(); context.putInt("Time", 13); context.putInt("CurrentDisplay", 7); context.putInt("StatusVersion", 4); runtime.put("ConditionContext", values(context));
        var before = runtime.copy(); var converted = NeoForgeTrainScheduleImport.runtime(runtime, 3955, h.getLevel().registryAccess());
        h.assertTrue(converted.getList("ConditionContext").orElseThrow().getCompoundOrEmpty(0).equals(context) && converted.getIntArray("TransitTimes").orElseThrow()[0] == 123 && before.equals(runtime), "Original runtime progress/context/transit prediction and complete input remain intact");
        var item = converted.getCompound("Schedule").orElseThrow().getList("Entries").orElseThrow().getCompoundOrEmpty(0).getList("Conditions").orElseThrow().getListOrEmpty(0).getCompoundOrEmpty(0).getCompound("Data").orElseThrow().getCompound("Item").orElseThrow();
        h.assertTrue(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), item).getOrThrow().get(DataComponents.CUSTOM_DATA).copyTag().getString("source-cargo").orElseThrow().equals("same original eleven"), "Genuine item DFU/native schedule codec retains the original filter component payload"); h.succeed();
    }
}
