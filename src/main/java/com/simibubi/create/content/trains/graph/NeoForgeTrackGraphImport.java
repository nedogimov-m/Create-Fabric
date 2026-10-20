package com.simibubi.create.content.trains.graph;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.trains.observer.ObserverEdgePoints;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalEdgePoints;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationEdgePoints;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/** Actual source graph/point/curve schemas, normalized and validated with the complete native graph codec. */
public final class NeoForgeTrackGraphImport {
    public static TrackGraph convert(CompoundTag source, DimensionPalette dimensions,
            int sourceVersion, HolderLookup.Provider registries) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original railway data version");
        StationEdgePoints.register(); SignalEdgePoints.register(); ObserverEdgePoints.register();
        keys(source, "graph", "Id", "Color", "Nodes", "Points");
        uuid(source, "Id"); integer(source, "Color");
        var result = new CompoundTag(); result.put("Id", source.get("Id").copy()); result.putInt("Color", integer(source, "Color"));
        var nodes = new ListTag();
        for (Tag value : list(source, "Nodes")) {
            if (!(value instanceof CompoundTag node)) throw invalid("Source graph node is not a compound");
            keys(node, "graph node", "Location", "Normal", "Connections");
            var next = new CompoundTag(); next.put("Location", location(compound(node, "Location"), dimensions));
            next.put("Normal", vector(list(node, "Normal"))); var connections = new ListTag(); var targets = new HashSet<Integer>();
            if (node.contains("Connections")) for (Tag entry : list(node, "Connections")) {
                if (!(entry instanceof CompoundTag connection)) throw invalid("Source graph connection is not a compound");
                keys(connection, "graph connection", "To", "EdgeData");
                int to = integer(connection, "To"); if (!targets.add(to)) throw invalid("Duplicate source directed graph edge");
                var edge = compound(connection, "EdgeData");
                keys(edge, "track edge", "Signals", "Material", "Positions", "Starts", "Axes", "Normals", "Primary", "Girder", "Smoothing");
                var nativeConnection = new CompoundTag(); nativeConnection.putInt("To", to);
                nativeConnection.putString("Material", string(edge, "Material"));
                TrackMaterial.CODEC.parse(NbtOps.INSTANCE, nativeConnection.get("Material")).getOrThrow();
                if (edge.contains("Positions")) nativeConnection.put("Turn", curve(edge));
                else for (String key : Set.of("Starts", "Axes", "Normals", "Primary", "Girder", "Smoothing"))
                    if (edge.contains(key)) throw invalid("Incomplete source curved track geometry");
                nativeConnection.put("EdgeData", edgeData(compound(edge, "Signals"), dimensions));
                connections.add(nativeConnection);
            }
            next.put("Connections", connections); nodes.add(next);
        }
        result.put("Nodes", nodes);
        var points = new ListTag(); var originalPoints = compound(source, "Points");
        for (String type : originalPoints.keySet()) {
            var identifier = Identifier.parse(type);
            if (!Set.of("create:station", "create:signal", "create:observer").contains(type)
                    || !EdgePointType.TYPES.containsKey(identifier)) throw invalid("Unknown original edge point type/schema " + type);
            for (Tag value : list(originalPoints, type)) {
                if (!(value instanceof CompoundTag point)) throw invalid("Malformed original edge point");
                points.add(point(type, point, dimensions, sourceVersion, registries));
            }
        }
        result.put("Points", points);
        // Actual native decoding checks every reciprocal edge, point identity/reference, geometry,
        // owner, signal-group state and intersection. No graph manager/world is synthesized.
        return TrackGraph.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), result).getOrThrow();
    }

    /** Pure original invalidation cargo conversion, without constructing/admitting a dummy graph. */
    public static CompoundTag convertMigrationSnapshot(Identifier type, CompoundTag source,
            int sourceVersion, HolderLookup.Provider registries) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Unknown original point migration version");
        StationEdgePoints.register(); SignalEdgePoints.register(); ObserverEdgePoints.register();
        if (!Set.of("create:station", "create:signal", "create:observer").contains(type.toString()))
            throw invalid("Unreviewed original point migration type " + type);
        var palette = list(source, "DimensionPalette");
        for (Tag value : palette) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original point dimension palette");
            keys(entry, "point dimension palette", "Id"); Identifier.parse(string(entry, "Id"));
        }
        var dimensions = DimensionPalette.read(source);
        var original = source.copy(); original.remove("DimensionPalette");
        var encoded = point(type.toString(), original, dimensions, sourceVersion, registries);
        var actual = com.simibubi.create.content.trains.signal.TrackEdgePoint.CODEC
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), encoded).getOrThrow();
        var result = new CompoundTag(); var nativeDimensions = new DimensionPalette();
        actual.write(result, registries, nativeDimensions); nativeDimensions.write(result);
        return result;
    }

    private static CompoundTag location(CompoundTag source, DimensionPalette dimensions) {
        keys(source, "track node location", "Pos", "D", "YO");
        pos(compound(source, "Pos")); dimensions.decode(integer(source, "D"));
        if (source.contains("YO")) integer(source, "YO");
        return (CompoundTag) TrackNodeLocation.CODEC.encodeStart(NbtOps.INSTANCE, TrackNodeLocation.read(source, dimensions)).getOrThrow();
    }
    private static ListTag locations(CompoundTag source, String key, DimensionPalette dimensions) {
        var pair = list(source, key); if (pair.size() != 2) throw invalid("Source railway endpoint pair must have two entries");
        var result = new ListTag();
        for (Tag value : pair) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Source railway endpoint is not a compound");
            result.add(location(entry, dimensions));
        }
        return result;
    }
    private static CompoundTag edgeData(CompoundTag source, DimensionPalette dimensions) {
        keys(source, "edge signals", "PassiveGroup", "SignalGroup", "Points", "Intersections");
        var result = new CompoundTag();
        if (source.contains("PassiveGroup")) {
            if (!bool(source, "PassiveGroup") || source.contains("SignalGroup")) throw invalid("Ambiguous original passive signal group");
            result.store("SignalGroup", UUIDUtil.CODEC, EdgeData.passiveGroup);
        } else if (source.contains("SignalGroup")) { uuid(source, "SignalGroup"); result.put("SignalGroup", source.get("SignalGroup").copy()); }
        var points = new ListTag();
        if (source.contains("Points")) for (Tag value : list(source, "Points")) {
            if (!(value instanceof CompoundTag point)) throw invalid("Malformed source edge point reference");
            keys(point, "edge point reference", "Id", "Type"); uuid(point, "Id");
            var type = Identifier.parse(string(point, "Type"));
            if (!EdgePointType.TYPES.containsKey(type)) throw invalid("Unknown source edge point reference type " + type);
            points.add(point.copy());
        }
        result.put("Points", points); var intersections = new ListTag();
        if (source.contains("Intersections")) for (Tag value : list(source, "Intersections")) {
            if (!(value instanceof CompoundTag crossing)) throw invalid("Malformed source railway intersection");
            keys(crossing, "intersection", "Id", "GroupId", "Location", "TargetLocation", "TargetEdge");
            uuid(crossing, "Id"); if (crossing.contains("GroupId")) uuid(crossing, "GroupId");
            number(crossing, "Location"); number(crossing, "TargetLocation");
            var next = crossing.copy(); next.put("TargetEdge", locations(crossing, "TargetEdge", dimensions));
            TrackEdgeIntersection.CODEC.parse(NbtOps.INSTANCE, next).getOrThrow(); intersections.add(next);
        }
        result.put("Intersections", intersections); return result;
    }
    private static CompoundTag curve(CompoundTag source) {
        var result = new CompoundTag(); var positions = new ListTag();
        for (Tag value : pair(source, "Positions")) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed source curve position");
            keys(entry, "curve position", "Pos"); positions.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, pos(compound(entry, "Pos"))).getOrThrow());
        }
        result.put("Positions", positions);
        for (String key : Set.of("Starts", "Axes", "Normals")) {
            var values = new ListTag();
            for (Tag value : pair(source, key)) {
                if (!(value instanceof CompoundTag entry)) throw invalid("Malformed source curve vector");
                keys(entry, "curve vector", "V"); values.add(vector(list(entry, "V")));
            }
            result.put(key, values);
        }
        result.putBoolean("Primary", bool(source, "Primary")); result.putBoolean("Girder", bool(source, "Girder"));
        result.putString("Material", string(source, "Material"));
        if (source.contains("Smoothing")) {
            var smoothing = new ListTag();
            for (Tag value : pair(source, "Smoothing")) {
                if (!(value instanceof CompoundTag entry)) throw invalid("Malformed source curve smoothing");
                keys(entry, "curve smoothing", "V"); integer(entry, "V"); smoothing.add(entry.get("V").copy());
            }
            result.put("Smoothing", smoothing);
        }
        BezierConnection.CODEC.parse(NbtOps.INSTANCE, result).getOrThrow(); return result;
    }
    private static ListTag pair(CompoundTag source, String key) {
        var result = list(source, key); if (result.size() != 2) throw invalid("Source " + key + " must contain exactly two entries"); return result;
    }
    private static CompoundTag point(String type, CompoundTag source, DimensionPalette dimensions,
            int sourceVersion, HolderLookup.Provider registries) {
        var fields = new HashSet<>(Set.of("Id", "Edge", "Position"));
        if (type.equals("create:signal")) for (int i = 1; i <= 2; i++)
            fields.addAll(Set.of("Tiles" + i, "Group" + i, "Update" + i, "Type" + i, "State" + i));
        else {
            fields.addAll(Set.of("BlockEntityPos", "BlockEntityDimension"));
            fields.addAll(type.equals("create:station") ? Set.of("Name", "Assembling", "Ports") : Set.of("Activated", "Filter", "TrainId"));
        }
        keys(source, "typed edge point", fields.toArray(String[]::new));
        uuid(source, "Id"); number(source, "Position");
        var base = new CompoundTag(); base.put("Id", source.get("Id").copy()); base.putDouble("Position", number(source, "Position"));
        base.put("Edge", locations(source, "Edge", dimensions));
        var data = new CompoundTag(); data.put("Point", base);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (type.equals("create:signal")) {
            var signalDimension = TrackNodeLocation.read(compoundPairSource(source, "Edge", 0), dimensions).dimension;
            for (int i = 1; i <= 2; i++) {
                var side = new CompoundTag(); var owners = new ListTag();
                if (source.contains("Tiles" + i)) for (Tag value : list(source, "Tiles" + i)) {
                    if (!(value instanceof CompoundTag tile)) throw invalid("Malformed source signal owner");
                    keys(tile, "signal tile", "Pos", "Power");
                    var owner = new CompoundTag(); owner.put("Owner", GlobalPos.CODEC.encodeStart(ops,
                        GlobalPos.of(signalDimension, pos(compound(tile, "Pos")))).getOrThrow()); owner.putBoolean("Power", bool(tile, "Power")); owners.add(owner);
                }
                side.put("Tiles", owners);
                side.put("Type", SignalType.CODEC.encodeStart(ops, SignalType.valueOf(string(source, "Type" + i))).getOrThrow());
                side.put("State", SignalState.CODEC.encodeStart(ops, SignalState.valueOf(string(source, "State" + i))).getOrThrow());
                if (source.contains("Group" + i)) { uuid(source, "Group" + i); side.put("Group", source.get("Group" + i).copy()); }
                if (source.contains("Update" + i) && !bool(source, "Update" + i)) throw invalid("Original signal update marker must be true");
                side.putBoolean("Update", source.contains("Update" + i)); data.put(i == 1 ? "First" : "Second", side);
            }
        } else {
            var owner = GlobalPos.of(dimensions.decode(integer(source, "BlockEntityDimension")), pos(compound(source, "BlockEntityPos")));
            data.put("Owner", GlobalPos.CODEC.encodeStart(ops, owner).getOrThrow());
            if (type.equals("create:station")) {
                data.putString("Name", string(source, "Name")); data.putBoolean("Assembling", bool(source, "Assembling"));
                var ports = new ListTag(); var positions = new HashSet<BlockPos>();
                for (Tag value : list(source, "Ports")) {
                    if (!(value instanceof CompoundTag port)) throw invalid("Malformed source station mail port");
                    keys(port, "station mail port", "Pos", "Address", "OfflineBuffer", "Primed");
                    var pos = pos(compound(port, "Pos")); if (!positions.add(pos)) throw invalid("Duplicate source station mail position");
                    var mail = new CompoundTag(); mail.putString("Address", string(port, "Address")); mail.putBoolean("Primed", bool(port, "Primed"));
                    mail.put("OfflineBuffer", itemHandler(compound(port, "OfflineBuffer"), GlobalPackagePort.SLOTS, sourceVersion, registries));
                    var next = new CompoundTag(); next.put("Pos", BlockPos.CODEC.encodeStart(ops, pos).getOrThrow()); next.put("Data", mail);
                    GlobalStation.Port.CODEC.parse(ops, next).getOrThrow(); ports.add(next);
                }
                data.put("Ports", ports);
            } else {
                data.putInt("Activated", integer(source, "Activated")); data.put("Filter", item(compound(source, "Filter"), sourceVersion, registries));
                if (source.contains("TrainId")) { uuid(source, "TrainId"); data.put("TrainId", source.get("TrainId").copy()); }
            }
        }
        var result = new CompoundTag(); result.putString("Type", type); result.put("Data", data); return result;
    }
    private static CompoundTag compoundPairSource(CompoundTag source, String key, int index) {
        var values = pair(source, key); if (!(values.get(index) instanceof CompoundTag value)) throw invalid("Malformed source endpoint pair"); return value;
    }
    private NeoForgeTrackGraphImport() {}
}
