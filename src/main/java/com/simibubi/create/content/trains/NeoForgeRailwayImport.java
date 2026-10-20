package com.simibubi.create.content.trains;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.trains.entity.NeoForgeTrainImport;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.NeoForgeTrackGraphImport;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.observer.ObserverEdgePoints;
import com.simibubi.create.content.trains.observer.TrackObserver;
import com.simibubi.create.content.trains.signal.EdgeGroupColor;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.SignalEdgePoints;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationEdgePoints;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataPairImport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** One genuine original graph/traffic/body/cargo proposal, then one coordinated native publication. */
public final class NeoForgeRailwayImport {
    public static final String VERSION = "create-tracks-1.21.1-schema-1";

    /** Concrete final ordinary native owners only; foreign SavedData callbacks are outside this bridge. */
    public static NeoForgeSavedDataPairImport.Pair<RailwaySavedData, TrainTrafficSavedData> load(MinecraftServer server) {
        return NeoForgeSavedDataPairImport.load(server, RailwaySavedData.TYPE, TrainTrafficSavedData.TYPE,
            "create_tracks.dat", "railway-v1", VERSION, NeoForgeRailwayImport::convert);
    }

    public static NeoForgeSavedDataPairImport.Converted convert(CompoundTag source, int sourceVersion,
            HolderLookup.Provider registries) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original railway scope data version");
        keys(source, "railway scope", "RailGraphs", "SignalBlocks", "Trains", "DimensionPalette");
        for (Tag value : list(source, "DimensionPalette")) {
            if (!(value instanceof CompoundTag dimension)) throw invalid("Malformed original dimension palette entry");
            keys(dimension, "dimension palette entry", "Id");
            net.minecraft.resources.Identifier.parse(string(dimension, "Id"));
        }
        var dimensions = DimensionPalette.read(source);
        var graphs = new LinkedHashMap<UUID, TrackGraph>();
        for (Tag value : list(source, "RailGraphs")) {
            if (!(value instanceof CompoundTag original)) throw invalid("Malformed original railway graph");
            var graph = NeoForgeTrackGraphImport.convert(original, dimensions, sourceVersion, registries);
            if (graphs.putIfAbsent(graph.id, graph) != null) throw invalid("Duplicate original persistent railway graph UUID");
        }
        var groups = new LinkedHashMap<UUID, SignalEdgeGroup>();
        for (Tag value : list(source, "SignalBlocks")) {
            if (!(value instanceof CompoundTag original)) throw invalid("Malformed original signal group");
            var group = group(original);
            if (groups.putIfAbsent(group.id, group) != null) throw invalid("Duplicate original signal group UUID");
        }
        var points = preflightTopology(graphs, groups);
        var trains = new LinkedHashMap<UUID, Train>(); var trainTags = new ListTag(); var entityIds = new HashSet<UUID>();
        var stationReservations = new HashMap<UUID, UUID>();
        for (Tag value : list(source, "Trains")) {
            if (!(value instanceof CompoundTag original)) throw invalid("Malformed original Train");
            var converted = NeoForgeTrainImport.convert(original, graphs, dimensions, sourceVersion, registries);
            var train = converted.train();
            if (trains.putIfAbsent(train.id, train) != null) throw invalid("Duplicate original persistent Train UUID");
            for (var identity : converted.entityIdentities())
                if (!entityIds.add(identity)) throw invalid("Global original body/passenger UUID custody is shared between trains");
            for (var entry : train.occupiedSignalBlocks.entrySet()) {
                requireGroup(groups, entry.getKey());
                if (entry.getValue() != null && !points.signals.containsKey(entry.getValue()))
                    throw invalid("Original occupied signal group names an absent actual signal boundary");
            }
            for (var group : train.reservedSignalBlocks) requireGroup(groups, group);
            for (var observer : train.occupiedObservers)
                if (!points.observers.containsKey(observer)) throw invalid("Original Train names an absent actual occupied observer");
            if (train.currentStation != null && stationReservations.putIfAbsent(train.currentStation, train.id) != null)
                throw invalid("Two original Trains claim one actual station reservation");
            trainTags.add(converted.tag());
        }
        // All proposed models remain genuinely detached. No temporary/fake manager is registered
        // merely to satisfy TrainManager's actual server-owner validation.
        for (var graph : graphs.values()) if (graph.manager() != null) throw invalid("Source proposal unexpectedly entered a native graph manager");
        for (var train : trains.values()) if (train.trafficManager() != null) throw invalid("Source proposal unexpectedly entered native traffic custody");
        var graphTags = new ListTag(); var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (var graph : graphs.values()) graphTags.add(TrackGraph.CODEC.encodeStart(ops, graph).getOrThrow());
        var topology = new CompoundTag(); topology.putInt("GraphFormatVersion", 1); topology.put("RailGraphs", graphTags);
        var groupTags = new ListTag(); for (var group : groups.values()) groupTags.add(group.write());
        var traffic = new CompoundTag(); traffic.putInt("TrafficFormatVersion", 1); traffic.put("Trains", trainTags);
        traffic.put("SignalBlocks", groupTags); dimensions.write(traffic);
        var wrappedTraffic = new CompoundTag(); wrappedTraffic.put("TrainTraffic", traffic);
        // The publication primitive repeats both actual native SavedData codecs before durable intent.
        return new NeoForgeSavedDataPairImport.Converted(topology, wrappedTraffic);
    }

    private static SignalEdgeGroup group(CompoundTag source) {
        keys(source, "signal group", "Id", "Color", "Connected", "Fallback");
        uuid(source, "Id"); EdgeGroupColor.valueOf(string(source, "Color")); bool(source, "Fallback");
        var seenKeys = new HashSet<UUID>();
        for (Tag value : list(source, "Connected")) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original intersecting signal group");
            keys(entry, "connected signal group", "Key", "Value");
            if (!seenKeys.add(uuid(entry, "Key"))) throw invalid("Duplicate original intersection key within a signal group");
            uuid(entry, "Value");
        }
        return SignalEdgeGroup.read(source);
    }

    private record Points(Map<UUID, GlobalStation> stations, Map<UUID, SignalBoundary> signals,
            Map<UUID, TrackObserver> observers) {}
    private record Directed(TrackGraph graph, TrackNode first, TrackNode second, TrackEdge edge) {}
    private record EdgeKey(TrackNodeLocation first, TrackNodeLocation second) {}
    private record Signature(UUID id, double location, TrackNodeLocation targetFirst, TrackNodeLocation targetSecond,
            double targetLocation) {}

    private static Points preflightTopology(Map<UUID, TrackGraph> graphs, Map<UUID, SignalEdgeGroup> groups) {
        var stations = new HashMap<UUID, GlobalStation>(); var signals = new HashMap<UUID, SignalBoundary>();
        var observers = new HashMap<UUID, TrackObserver>(); var worldOwners = new HashMap<GlobalPos, String>();
        var allEdges = new ArrayList<Directed>(); var edgeOwners = new HashMap<EdgeKey, List<Directed>>(); var usedGroups = new HashSet<UUID>();
        for (var graph : graphs.values()) {
            graph.validate();
            var fallback = groups.get(graph.id);
            if (fallback == null || !fallback.fallbackGroup) throw invalid("Original graph has no actual original fallback signal group owner");
            usedGroups.add(graph.id);
            for (var station : graph.getPoints(StationEdgePoints.STATION)) {
                if (stations.putIfAbsent(station.id, station) != null) throw invalid("One original station UUID belongs to two graphs");
                claim(worldOwners, station.owner(), "station " + station.id);
                for (var port : station.ports()) claim(worldOwners, GlobalPos.of(station.owner().dimension(), port.pos()), "mail port of station " + station.id);
            }
            for (var observer : graph.getPoints(ObserverEdgePoints.OBSERVER)) {
                if (observers.putIfAbsent(observer.id, observer) != null) throw invalid("One original observer UUID belongs to two graphs");
                claim(worldOwners, observer.owner(), "observer " + observer.id);
            }
            for (var signal : graph.getPoints(SignalEdgePoints.SIGNAL)) {
                if (signals.putIfAbsent(signal.id, signal) != null) throw invalid("One original signal UUID belongs to two graphs");
                for (boolean side : new boolean[] {true, false}) {
                    for (var owner : signal.owners(side).keySet()) claim(worldOwners, owner, "signal " + signal.id);
                    if (signal.group(side) != null) { requireGroup(groups, signal.group(side)); usedGroups.add(signal.group(side)); }
                }
            }
            for (var location : graph.getNodes()) {
                var first = graph.locateNode(location);
                for (var entry : graph.getConnectionsFrom(first).entrySet()) {
                    var directed = new Directed(graph, first, entry.getKey(), entry.getValue()); allEdges.add(directed);
                    edgeOwners.computeIfAbsent(new EdgeKey(first.getLocation(), entry.getKey().getLocation()), ignored -> new ArrayList<>()).add(directed);
                    var group = directed.edge.getEdgeData().getEffectiveEdgeGroupId(graph);
                    if (group != null) { requireGroup(groups, group); usedGroups.add(group); }
                }
            }
        }
        var actualLinks = new HashMap<UUID, Map<UUID, UUID>>();
        for (var local : allEdges) {
            var signatures = new HashSet<Signature>();
            for (var crossing : local.edge.getEdgeData().getIntersections()) {
                var signature = new Signature(crossing.id, crossing.location, crossing.target.getFirst(), crossing.target.getSecond(), crossing.targetLocation);
                if (!signatures.add(signature)) throw invalid("Duplicate exact original directed intersection geometry");
                var candidates = edgeOwners.getOrDefault(new EdgeKey(crossing.target.getFirst(), crossing.target.getSecond()), List.of());
                if (candidates.size() > 1) throw invalid("Original crossing target edge has ambiguous cross-graph ownership");
                Directed target = candidates.isEmpty() ? null : candidates.getFirst();
                if (target == null || crossing.targetLocation < 0 || crossing.targetLocation > target.edge.getLength() + 1e-6)
                    throw invalid("Original crossing names an absent/outside actual target edge");
                boolean reciprocal = false;
                for (var other : target.edge.getEdgeData().getIntersections()) {
                    if (!other.id.equals(crossing.id) || !near(other.location, crossing.targetLocation)) continue;
                    boolean forward = other.target.getFirst().equals(local.first.getLocation()) && other.target.getSecond().equals(local.second.getLocation());
                    boolean reverse = other.target.getFirst().equals(local.second.getLocation()) && other.target.getSecond().equals(local.first.getLocation());
                    if (forward && near(other.targetLocation, crossing.location) || reverse && near(other.targetLocation, local.edge.getLength() - crossing.location)) reciprocal = true;
                }
                if (!reciprocal) throw invalid("Original crossing has no actual reciprocal cross-graph geometry");
                UUID localGroup = local.edge.getEdgeData().getGroupAtPosition(local.graph, crossing.location);
                UUID otherGroup = target.edge.getEdgeData().getGroupAtPosition(target.graph, crossing.targetLocation);
                if (crossing.groupId != null && !crossing.groupId.equals(localGroup))
                    throw invalid("Original persisted crossing group disagrees with its actual local geometry");
                if (localGroup != null) { requireGroup(groups, localGroup); usedGroups.add(localGroup); }
                if (otherGroup != null) { requireGroup(groups, otherGroup); usedGroups.add(otherGroup); }
                if (localGroup != null && otherGroup != null) {
                    UUID previous = actualLinks.computeIfAbsent(localGroup, ignored -> new HashMap<>()).putIfAbsent(crossing.id, otherGroup);
                    if (previous != null && !previous.equals(otherGroup)) throw invalid("Original intersection UUID has ambiguous actual group linkage");
                }
            }
        }
        for (var group : groups.values()) {
            if (group.fallbackGroup && !graphs.containsKey(group.id)) throw invalid("Original fallback signal group has no actual graph owner");
            if (!usedGroups.contains(group.id)) throw invalid("Original orphan signal group would be silently removed by native hydration");
            for (var link : group.intersecting.entrySet()) {
                requireGroup(groups, link.getValue());
                if (!link.getValue().equals(actualLinks.getOrDefault(group.id, Map.of()).get(link.getKey())))
                    throw invalid("Original saved signal linkage lacks its actual cross-graph intersection");
            }
        }
        return new Points(Map.copyOf(stations), Map.copyOf(signals), Map.copyOf(observers));
    }
    private static boolean near(double first, double second) { return Math.abs(first - second) <= 1e-6; }
    private static void requireGroup(Map<UUID, SignalEdgeGroup> groups, UUID id) {
        if (!groups.containsKey(id)) throw invalid("Original railway reference lacks its actual saved signal group " + id);
    }
    private static void claim(Map<GlobalPos, String> owners, GlobalPos position, String owner) {
        if (owners.putIfAbsent(position, owner) != null) throw invalid("Original world block/mailbox has multiple graph owners: " + position);
    }
    private NeoForgeRailwayImport() {}
}
