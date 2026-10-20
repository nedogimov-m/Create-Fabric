package com.simibubi.create.test;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.NeoForgeTrackGraphImport;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.observer.ObserverEdgePoints;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalEdgePoints;
import com.simibubi.create.content.trains.station.StationEdgePoints;
import com.simibubi.create.content.trains.track.TrackMaterial;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Actual original graph shapes and native topology/point/item codecs; authored cases UNRUN. */
public final class NeoForgeTrackGraphMigrationGameTests {
    private static final DimensionPalette DIMENSIONS = new DimensionPalette(List.of(Level.OVERWORLD, Level.NETHER));
    private static CompoundTag coordinates(int x, int y, int z) { var result = new CompoundTag(); result.putInt("X", x); result.putInt("Y", y); result.putInt("Z", z); return result; }
    private static CompoundTag location(int x, int z, int dimension) { var result = new CompoundTag(); result.put("Pos", coordinates(x, 128, z)); result.putInt("D", dimension); return result; }
    private static ListTag vector(double x, double y, double z) { var result = new ListTag(); result.add(DoubleTag.valueOf(x)); result.add(DoubleTag.valueOf(y)); result.add(DoubleTag.valueOf(z)); return result; }
    private static ListTag pair(CompoundTag first, CompoundTag second) { var result = new ListTag(); result.add(first); result.add(second); return result; }
    private static CompoundTag edge() { var result = new CompoundTag(); var signals = new CompoundTag(); signals.putBoolean("PassiveGroup", true); result.put("Signals", signals); result.putString("Material", TrackMaterial.ANDESITE.id.toString()); return result; }
    private static CompoundTag connection(int to, CompoundTag edge) { var result = new CompoundTag(); result.putInt("To", to); result.put("EdgeData", edge); return result; }
    private static CompoundTag node(CompoundTag location, CompoundTag connection) { var result = new CompoundTag(); result.put("Location", location); result.put("Normal", vector(0, 1, 0)); var connections = new ListTag(); connections.add(connection); result.put("Connections", connections); return result; }
    private static CompoundTag graph(int dimension) {
        var result = new CompoundTag(); result.store("Id", UUIDUtil.CODEC, UUID.randomUUID()); result.putInt("Color", 0x71cafe);
        result.put("Nodes", pair(node(location(0, 0, dimension), connection(1, edge())), node(location(10, 0, dimension), connection(0, edge()))));
        result.put("Points", new CompoundTag()); return result;
    }
    private static CompoundTag basePoint(UUID id, int dimension, double position) { var result = new CompoundTag(); result.store("Id", UUIDUtil.CODEC, id); result.put("Edge", pair(location(0, 0, dimension), location(10, 0, dimension))); result.putDouble("Position", position); return result; }
    private static CompoundTag owningPoint(UUID id, int dimension) { var result = basePoint(id, dimension, 2); result.put("BlockEntityPos", coordinates(9, 65, -3)); result.putInt("BlockEntityDimension", dimension); return result; }
    private static CompoundTag at(CompoundTag source, int index) { return (CompoundTag) source.getListOrEmpty("Nodes").get(index); }
    private static CompoundTag edgeAt(CompoundTag source, int index) { return ((CompoundTag) at(source, index).getListOrEmpty("Connections").getFirst()).getCompoundOrEmpty("EdgeData"); }
    private static void addPoint(CompoundTag graph, String type, CompoundTag point, boolean boundary) {
        var values = graph.getCompoundOrEmpty("Points").getListOrEmpty(type); values.add(point); graph.getCompoundOrEmpty("Points").put(type, values);
        for (int index = 0; index < 2; index++) {
            var signals = edgeAt(graph, index).getCompoundOrEmpty("Signals"); if (boundary) signals.remove("PassiveGroup");
            var refs = signals.getListOrEmpty("Points"); var ref = new CompoundTag(); ref.put("Id", point.get("Id").copy()); ref.putString("Type", type); refs.add(ref); signals.put("Points", refs);
        }
    }
    private static TrackGraph convert(GameTestHelper h, CompoundTag source) { return NeoForgeTrackGraphImport.convert(source, DIMENSIONS, 3955, h.getLevel().registryAccess()); }
    private static CompoundTag item() { var result = new CompoundTag(); result.putString("id", "minecraft:diamond"); result.putInt("count", 11); var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("source", "actual original mail"); components.put("minecraft:custom_data", custom); result.put("components", components); return result; }
    private static CompoundTag station(UUID id, int dimension) {
        var result = owningPoint(id, dimension); result.putString("Name", "Original station"); result.putBoolean("Assembling", false);
        var port = new CompoundTag(); port.put("Pos", coordinates(8, 65, -3)); port.putString("Address", "Original mail address"); port.putBoolean("Primed", true);
        var inventory = new CompoundTag(); inventory.putInt("Size", 18); var items = new ListTag(); var mail = item(); mail.putInt("Slot", 17); items.add(mail); inventory.put("Items", items); port.put("OfflineBuffer", inventory);
        var ports = new ListTag(); ports.add(port); result.put("Ports", ports); return result;
    }
    private static CompoundTag signal(UUID id, int dimension) {
        var result = basePoint(id, dimension, 3);
        for (int side = 1; side <= 2; side++) {
            var tile = new CompoundTag(); tile.put("Pos", coordinates(side, 65, -4)); tile.putBoolean("Power", side == 2);
            var tiles = new ListTag(); tiles.add(tile); result.put("Tiles" + side, tiles); result.store("Group" + side, UUIDUtil.CODEC, UUID.randomUUID());
            result.putString("Type" + side, side == 1 ? "ENTRY_SIGNAL" : "CROSS_SIGNAL"); result.putString("State" + side, side == 1 ? "GREEN" : "RED");
        }
        result.putBoolean("Update1", true); return result;
    }

    @GameTest public void originalGraphNodesDimensionsColorsAndHalfBlockCoordinatesSurvive(GameTestHelper h) {
        var source = graph(1); at(source, 0).getCompoundOrEmpty("Location").putInt("YO", 3); var original = source.copy(); var actual = convert(h, source);
        h.assertTrue(actual.id.equals(source.read("Id", UUIDUtil.CODEC).orElseThrow()) && actual.color == 0x71cafe && actual.manager() == null
                && actual.getNodes().size() == 2 && actual.getNodes().stream().allMatch(location -> location.dimension.equals(Level.NETHER))
                && actual.getNodes().stream().anyMatch(location -> location.getX() == 0 && location.getY() == 128 && location.yOffsetPixels == 3),
            "Actual original UUID/color/packed half-block nodes/pixel smoothing/dimension survive without a fabricated graph manager");
        actual.validate(); h.assertTrue(original.equals(source), "Source graph input is unchanged"); h.succeed();
    }

    @GameTest public void originalStationOwnerAndSparseLastSlotMailKeepAllCountsAndComponents(GameTestHelper h) {
        var source = graph(1); UUID id = UUID.randomUUID(); addPoint(source, "create:station", station(id, 1), false); var actual = convert(h, source);
        var point = actual.getPoint(StationEdgePoints.STATION, id); var port = point.ports().getFirst(); var items = port.data().items();
        h.assertTrue(point.owner().equals(GlobalPos.of(Level.NETHER, new BlockPos(9, 65, -3))) && point.name().equals("Original station")
                && port.pos().equals(new BlockPos(8, 65, -3)) && port.data().address().equals("Original mail address") && port.data().primed()
                && items.size() == 18 && items.subList(0, 17).stream().allMatch(stack -> stack.isEmpty()) && items.get(17).is(Items.DIAMOND)
                && items.get(17).getCount() == 11 && items.get(17).get(DataComponents.CUSTOM_DATA).copyTag().getString("source").orElseThrow().equals("actual original mail"),
            "Source station ownership and actual NeoForge18-slot buffer retain the original last slot/count/components without parcel conversion or truncation");
        actual.validate(); h.succeed();
    }

    @GameTest public void originalSignalSideGroupsOwnersPowerStatesAndUpdateMarkersSurvive(GameTestHelper h) {
        var source = graph(1); UUID id = UUID.randomUUID(); var old = signal(id, 1); addPoint(source, "create:signal", old, true); var actual = convert(h, source);
        var point = actual.getPoint(SignalEdgePoints.SIGNAL, id);
        h.assertTrue(point.group(true).equals(old.read("Group1", UUIDUtil.CODEC).orElseThrow())
                && point.group(false).equals(old.read("Group2", UUIDUtil.CODEC).orElseThrow()) && point.type(true) == SignalType.ENTRY_SIGNAL
                && point.type(false) == SignalType.CROSS_SIGNAL && point.getStateFor(GlobalPos.of(Level.NETHER, new BlockPos(1, 65, -4))) == SignalState.GREEN
                && point.getStateFor(GlobalPos.of(Level.NETHER, new BlockPos(2, 65, -4))) == SignalState.RED
                && point.updateQueued(true) && !point.updateQueued(false)
                && point.owners(true).containsKey(GlobalPos.of(Level.NETHER, new BlockPos(1, 65, -4)))
                && point.owners(false).get(GlobalPos.of(Level.NETHER, new BlockPos(2, 65, -4))),
            "Actual original signal state retains independent side UUIDs/types/states/update markers and real dimension-aware powered tile owners");
        actual.validate(); h.succeed();
    }

    @GameTest public void originalObserverFilterPulseTrainAndActualWorldOwnerSurvive(GameTestHelper h) {
        var source = graph(0); UUID id = UUID.randomUUID(), train = UUID.randomUUID(); var old = owningPoint(id, 0);
        old.putInt("Activated", 5); old.put("Filter", item()); old.store("TrainId", UUIDUtil.CODEC, train); addPoint(source, "create:observer", old, false);
        var point = convert(h, source).getPoint(ObserverEdgePoints.OBSERVER, id);
        h.assertTrue(point.owner().equals(GlobalPos.of(Level.OVERWORLD, new BlockPos(9, 65, -3))) && point.isActivated()
                && train.equals(point.getCurrentTrain()) && point.getFilter().item().is(Items.DIAMOND) && point.getFilter().item().getCount() == 11,
            "Original detector world owner/filter item/count/pulse and actual source Train UUID survive native codecs"); h.succeed();
    }

    private static CompoundTag wrapped(String key, CompoundTag value) { var result = new CompoundTag(); result.put(key, value); return result; }
    private static CompoundTag wrappedVector(double x, double y, double z) { var result = new CompoundTag(); result.put("V", vector(x, y, z)); return result; }
    private static CompoundTag smoothing(int value) { var result = new CompoundTag(); result.putInt("V", value); return result; }
    private static void curved(CompoundTag edge, boolean forward) {
        edge.put("Positions", pair(wrapped("Pos", coordinates(forward ? 0 : 5, 64, 0)), wrapped("Pos", coordinates(forward ? 5 : 0, 64, 0))));
        edge.put("Starts", pair(wrappedVector(forward ? 0 : 5, 64, 0), wrappedVector(forward ? 5 : 0, 64, 0)));
        edge.put("Axes", pair(wrappedVector(forward ? 1 : -1, 0, 0), wrappedVector(forward ? -1 : 1, 0, 0)));
        edge.put("Normals", pair(wrappedVector(0, 1, 0), wrappedVector(0, 1, 0))); edge.putBoolean("Primary", forward); edge.putBoolean("Girder", true);
        edge.put("Smoothing", pair(smoothing(forward ? 3 : -2), smoothing(forward ? -2 : 3)));
    }
    @GameTest public void originalReciprocalCurveGeometryGirderAndSmoothingSurvive(GameTestHelper h) {
        var source = graph(0); curved(edgeAt(source, 0), true); curved(edgeAt(source, 1), false); var actual = convert(h, source);
        var first = actual.locateNode(actual.getNodes().stream().filter(location -> location.getX() == 0).findFirst().orElseThrow());
        var turn = actual.getConnectionsFrom(first).values().iterator().next().getTurn();
        h.assertTrue(turn != null && turn.primary && turn.hasGirder && turn.smoothing.getFirst() == 3 && turn.smoothing.getSecond() == -2
                && turn.bePositions.getFirst().equals(new BlockPos(0, 64, 0)) && turn.bePositions.getSecond().equals(new BlockPos(5, 64, 0))
                && turn.getMaterial() == TrackMaterial.ANDESITE,
            "Genuine source positions/starts/axes/normals/girder/material/smoothing form a validated reciprocal native curve"); actual.validate(); h.succeed();
    }

    @GameTest public void originalReciprocalCrossingKeepsIdentityTargetGeometryAndSavedGroup(GameTestHelper h) {
        var source = graph(0); var nodes = source.getListOrEmpty("Nodes");
        nodes.add(node(location(5, -5, 0), connection(3, edge()))); nodes.add(node(location(5, 5, 0), connection(2, edge())));
        UUID crossing = UUID.randomUUID(), group = source.read("Id", UUIDUtil.CODEC).orElseThrow();
        for (int index = 0; index < 4; index++) {
            var old = new CompoundTag(); old.store("Id", UUIDUtil.CODEC, crossing); old.store("GroupId", UUIDUtil.CODEC, group);
            old.putDouble("Location", 2.5); old.putDouble("TargetLocation", 2.5);
            old.put("TargetEdge", index < 2 ? pair(location(5, -5, 0), location(5, 5, 0)) : pair(location(0, 0, 0), location(10, 0, 0)));
            var intersections = new ListTag(); intersections.add(old); edgeAt(source, index).getCompoundOrEmpty("Signals").put("Intersections", intersections);
        }
        var actual = convert(h, source); int count = 0;
        for (var location : actual.getNodes()) for (var edge : actual.getConnectionsFrom(actual.locateNode(location)).values()) {
            var intersections = edge.getEdgeData().getIntersections(); h.assertTrue(intersections.size() == 1,
                "Every original directed crossing reference survived"); var value = intersections.getFirst(); count++;
            h.assertTrue(crossing.equals(value.id) && group.equals(value.groupId) && value.location == 2.5 && value.targetLocation == 2.5
                    && value.target.both(target -> target.dimension.equals(Level.OVERWORLD)),
                "Original crossing UUID/saved signal group, exact edge positions and target dimension geometry remain native data");
        }
        h.assertTrue(count == 4, "Both genuine reciprocal edges retain all four directed crossing records"); actual.validate(); h.succeed();
    }

    @GameTest public void malformedAndIncompleteOriginalTopologyFailsBeforeAnyGraphOwner(GameTestHelper h) {
        var missingReverse = graph(0); at(missingReverse, 1).put("Connections", new ListTag());
        var duplicate = graph(0); at(duplicate, 1).put("Location", at(duplicate, 0).getCompoundOrEmpty("Location").copy());
        var outside = graph(0); ((CompoundTag) at(outside, 0).getListOrEmpty("Connections").getFirst()).putInt("To", 91);
        var missingPoint = graph(0); var ref = new CompoundTag(); ref.store("Id", UUIDUtil.CODEC, UUID.randomUUID()); ref.putString("Type", "create:station"); var refs = new ListTag(); refs.add(ref); edgeAt(missingPoint, 0).getCompoundOrEmpty("Signals").put("Points", refs);
        var dimension = graph(0); at(dimension, 0).getCompoundOrEmpty("Location").putInt("D", 8);
        for (var source : List.of(missingReverse, duplicate, outside, missingPoint, dimension)) {
            var original = source.copy(); boolean rejected = false;
            try { convert(h, source); } catch (RuntimeException expected) { rejected = true; }
            h.assertTrue(rejected && original.equals(source), "A real source topology error fails explicitly and never becomes an empty/default graph or mutates its source");
        }
        h.succeed();
    }

    @GameTest public void unknownOriginalPointVersionAndDuplicateMailSlotAreExplicitFailures(GameTestHelper h) {
        var unknownPoint = graph(0); unknownPoint.getCompoundOrEmpty("Points").put("other:unported", new ListTag());
        var unknownGraph = graph(0); unknownGraph.putInt("FutureGraphVersion", 9);
        var mail = graph(0); var station = station(UUID.randomUUID(), 0); var port = (CompoundTag) station.getListOrEmpty("Ports").getFirst();
        var inventory = port.getCompoundOrEmpty("OfflineBuffer"); inventory.getListOrEmpty("Items").add(inventory.getListOrEmpty("Items").getFirst().copy()); addPoint(mail, "create:station", station, false);
        for (var source : List.of(unknownPoint, unknownGraph, mail)) {
            var original = source.copy(); boolean rejected = false;
            try { convert(h, source); } catch (RuntimeException expected) { rejected = true; }
            h.assertTrue(rejected && original.equals(source), "Unknown source schemas and duplicate real mail custody fail before native publication or source mutation");
        }
        h.succeed();
    }

    @GameTest public void typedOriginalPointsRemainSingleNativeInstancesThroughActualGraphRoundTrip(GameTestHelper h) {
        var source = graph(0); UUID station = UUID.randomUUID(), signal = UUID.randomUUID();
        addPoint(source, "create:station", station(station, 0), false); addPoint(source, "create:signal", signal(signal, 0), true);
        var migrated = convert(h, source); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var nativeTag = TrackGraph.CODEC.encodeStart(ops, migrated).getOrThrow(); var actual = TrackGraph.CODEC.parse(ops, nativeTag).getOrThrow(); actual.validate();
        for (var location : actual.getNodes()) for (var edge : actual.getConnectionsFrom(actual.locateNode(location)).values()) {
            h.assertTrue(edge.getEdgeData().getPoints().contains(actual.getPoint(StationEdgePoints.STATION, station))
                    && edge.getEdgeData().getPoints().contains(actual.getPoint(SignalEdgePoints.SIGNAL, signal)),
                "Both actual directed edges reference the one native typed point object owned by their canonical graph registry");
        }
        h.succeed();
    }
}
