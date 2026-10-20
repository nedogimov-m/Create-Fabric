package com.simibubi.create.content.trains.entity;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import static com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity.BOGEY_DATA_KEY;
import static com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity.BOGEY_STYLE_KEY;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.foundation.utility.NeoForgeTextComponentImport;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.schedule.NeoForgeTrainScheduleImport;
import com.simibubi.create.content.trains.signal.SignalEdgePoints;
import com.simibubi.create.content.trains.station.StationEdgePoints;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Callback-free source train motion/formation/reference preflight, separate from body/cargo ownership. */
final class NeoForgeTrainStateImport {
    record Header(UUID id, TrackGraph graph, CompoundTag withoutCarriages) {}

    static Header header(CompoundTag source, Map<UUID, TrackGraph> graphs, DimensionPalette dimensions,
            int sourceVersion, HolderLookup.Provider registries) {
        keys(source, "Train", "Id", "Owner", "Graph", "Carriages", "CarriageSpacing", "DoubleEnded", "Speed", "Throttle",
            "SpeedBeforeStall", "Fuel", "TargetSpeed", "IconType", "MapColorIndex", "Name", "Station", "Backwards", "Derailed",
            "UpdateSignals", "SignalBlocks", "ReservedSignalBlocks", "OccupiedObservers", "MigratingPoints", "Runtime", "Navigation");
        UUID id = uuid(source, "Id");
        if (source.contains("Owner")) uuid(source, "Owner");
        TrackGraph graph = null;
        if (source.contains("Graph")) {
            graph = graphs.get(uuid(source, "Graph"));
            if (graph == null) throw invalid("Original Train refers to an absent persistent graph");
        }
        int carriages = list(source, "Carriages").size();
        if (carriages == 0 || carriages > Train.MAX_CARRIAGES) throw invalid("Original Train formation exceeds actual native bounds");
        if (!(source.get("CarriageSpacing") instanceof IntArrayTag)) throw invalid("Malformed original Train formation spacing");
        int[] spacing = source.getIntArray("CarriageSpacing").orElseThrow();
        if (spacing.length != carriages - 1) throw invalid("Original Train spacing count disagrees with its actual carriages");
        for (int value : spacing) if (value < 0 || value > 512) throw invalid("Original Train spacing exceeds actual native bounds");
        for (String field : new String[] {"DoubleEnded", "Backwards", "Derailed", "UpdateSignals"}) bool(source, field);
        number(source, "Speed"); number(source, "TargetSpeed");
        double throttle = number(source, "Throttle");
        if (throttle < 0 || throttle > 1 || integer(source, "Fuel") < 0) throw invalid("Invalid original Train throttle/fuel state");
        if (source.contains("SpeedBeforeStall")) number(source, "SpeedBeforeStall");
        integer(source, "MapColorIndex");
        var icon = Identifier.parse(string(source, "IconType"));
        if (!TrainIconType.REGISTRY.containsKey(icon)) throw invalid("Unknown original Train icon cannot become the native default");
        string(source, "Name");
        if (source.contains("Station")) {
            UUID station = uuid(source, "Station");
            if (graph == null || graph.getPoint(StationEdgePoints.STATION, station) == null)
                throw invalid("Original Train station does not belong to its actual persistent graph");
        }
        references(source, "SignalBlocks", true);
        references(source, "ReservedSignalBlocks", false);
        references(source, "OccupiedObservers", false);
        var result = source.copy(); result.remove("Carriages");
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag fixedName = NeoForgeTextComponentImport.flattened((net.minecraft.nbt.StringTag) source.get("Name"), sourceVersion, registries, com.simibubi.create.foundation.utility.NeoForgeMigrationScope.train(id).item("name", 0));
        var name = ComponentSerialization.CODEC.parse(ops, fixedName).getOrThrow();
        result.put("Name", ComponentSerialization.CODEC.encodeStart(ops, name).getOrThrow());
        result.put("Runtime", NeoForgeTrainScheduleImport.runtime(compound(source, "Runtime"), sourceVersion, registries, com.simibubi.create.foundation.utility.NeoForgeMigrationScope.train(id).item("runtime", 0)));
        result.put("Navigation", navigation(compound(source, "Navigation"), graph, dimensions));
        var migrating = new ListTag();
        for (Tag value : list(source, "MigratingPoints")) {
            if (!(value instanceof CompoundTag point)) throw invalid("Malformed original Train migration point");
            keys(point, "Train migration point", "Curve", "Fallback", "Position", "Nodes");
            bool(point, "Curve"); vector(list(point, "Fallback"));
            if (number(point, "Position") < 0) throw invalid("Negative original migration distance");
            locations(point, "Nodes", dimensions);
            migrating.add(TrainMigration.read(point, dimensions).write(dimensions));
        }
        result.put("MigratingPoints", migrating);
        return new Header(id, graph, result);
    }

    static CompoundTag bogey(CompoundTag source, TrackGraph graph, DimensionPalette dimensions) {
        keys(source, "carriage bogey", "Type", "Points", "UpsideDown", BOGEY_DATA_KEY);
        var id = Identifier.parse(string(source, "Type"));
        if (!BuiltInRegistries.BLOCK.containsKey(id) || !(BuiltInRegistries.BLOCK.getValue(id) instanceof AbstractBogeyBlock<?> type))
            throw invalid("Unknown original actual bogey block " + id);
        boolean down = bool(source, "UpsideDown");
        if (down && !type.canBeUpsideDown()) throw invalid("Original bogey cannot silently lose its upside-down state");
        var data = compound(source, BOGEY_DATA_KEY);
        keys(data, "standard bogey data", BOGEY_STYLE_KEY, "UpsideDown");
        if (!AllBogeyStyles.BOGEY_STYLES.containsKey(Identifier.parse(string(data, BOGEY_STYLE_KEY))))
            throw invalid("Unknown original bogey style cannot become the native default");
        if (bool(data, "UpsideDown") != down) throw invalid("Original bogey orientation owners disagree");
        var points = list(source, "Points");
        if (points.size() != 2) throw invalid("Original bogey must retain exactly two wheel points");
        var result = source.copy(); var next = new ListTag();
        for (Tag value : points) {
            if (!(value instanceof CompoundTag point)) throw invalid("Malformed original bogey travelling point");
            keys(point, "travelling point", "Nodes", "Position", "UpsideDown");
            if (!point.isEmpty()) {
                if (number(point, "Position") < 0) throw invalid("Negative original wheel distance");
                bool(point, "UpsideDown"); locations(point, "Nodes", dimensions);
                if (graph != null) {
                    var pair = list(point, "Nodes");
                    var first = graph.locateNode(TrackNodeLocation.read((CompoundTag) pair.get(0), dimensions));
                    var second = graph.locateNode(TrackNodeLocation.read((CompoundTag) pair.get(1), dimensions));
                    var edge = first == null || second == null ? null : graph.getConnectionsFrom(first).get(second);
                    if (edge == null || number(point, "Position") > edge.getLength())
                        throw invalid("Original wheel point does not belong to its actual graph edge");
                }
            }
            var actualPoint = TravellingPoint.read(point, graph, dimensions);
            if (point.isEmpty()) actualPoint.upsideDown = down;
            next.add(actualPoint.write(dimensions));
        }
        result.put("Points", next);
        // Actual native decoding must preserve the original block/style/orientation and both points.
        var actual = CarriageBogey.read(result, graph, dimensions);
        var encoded = actual.write(dimensions);
        if (!encoded.equals(result)) throw invalid("Native bogey reader/writer would normalize original bogey data");
        return result;
    }

    private static CompoundTag navigation(CompoundTag source, TrackGraph graph, DimensionPalette dimensions) {
        keys(source, "Train navigation", "Destination", "DistanceToDestination", "DistanceStartedAt", "BehindTrain", "AnnounceArrival",
            "Path", "BlockingSignal", "BlockingSignalSide", "DistanceToSignal", "TicksWaitingForSignal");
        if (source.isEmpty()) return source.copy();
        UUID destination = uuid(source, "Destination");
        if (graph == null || graph.getPoint(StationEdgePoints.STATION, destination) == null)
            throw invalid("Original navigation cannot be dropped because its actual destination/graph is missing");
        number(source, "DistanceToDestination");
        if (number(source, "DistanceStartedAt") < 0) throw invalid("Negative original navigation start distance");
        bool(source, "BehindTrain"); bool(source, "AnnounceArrival");
        for (Tag value : list(source, "Path")) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original navigation path");
            keys(entry, "navigation path entry", "Nodes"); locations(entry, "Nodes", dimensions);
            var endpoints = list(entry, "Nodes");
            for (Tag endpoint : endpoints)
                if (graph.locateNode(TrackNodeLocation.read((CompoundTag) endpoint, dimensions)) == null)
                    throw invalid("Original navigation node cannot be silently removed from its actual path");
        }
        if (source.contains("BlockingSignal")) {
            if (graph.getPoint(SignalEdgePoints.SIGNAL, uuid(source, "BlockingSignal")) == null)
                throw invalid("Original blocking signal is absent from its actual graph");
            bool(source, "BlockingSignalSide"); number(source, "DistanceToSignal");
            if (integer(source, "TicksWaitingForSignal") < 0) throw invalid("Negative original signal wait ticks");
        } else for (String field : new String[] {"BlockingSignalSide", "DistanceToSignal", "TicksWaitingForSignal"})
            if (source.contains(field)) throw invalid("Original signal wait fields lack their actual signal owner");
        return source.copy();
    }

    static void locations(CompoundTag source, String key, DimensionPalette dimensions) {
        var pair = list(source, key);
        if (pair.size() != 2) throw invalid("Original railway endpoint pair must retain exactly two locations");
        for (Tag value : pair) {
            if (!(value instanceof CompoundTag location)) throw invalid("Malformed original railway endpoint");
            keys(location, "railway endpoint", "Pos", "D", "YO"); pos(compound(location, "Pos")); dimensions.decode(integer(location, "D"));
            if (location.contains("YO")) { int offset = integer(location, "YO"); if (offset < -32768 || offset > 32767) throw invalid("Original node smoothing exceeds native bounds"); }
        }
    }

    private static void references(CompoundTag source, String key, boolean boundary) {
        var seen = new HashSet<UUID>();
        for (Tag value : list(source, key)) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original Train UUID reference");
            if (boundary) keys(entry, "occupied signal group", "Id", "Boundary");
            else keys(entry, "Train UUID reference", "Id");
            if (!seen.add(uuid(entry, "Id"))) throw invalid("Duplicate original Train UUID reference in " + key);
            if (boundary && entry.contains("Boundary")) uuid(entry, "Boundary");
        }
    }
    private NeoForgeTrainStateImport() {}
}
