package com.simibubi.create.content.trains.entity;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.contraptions.NeoForgeStandaloneEntityImport;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/** A complete original carriage proposal; no native world/entity/manager owner is synthesized. */
final class NeoForgeCarriageImport {
    record Converted(CompoundTag tag, Set<UUID> identities) {}
    private record Edge(UUID parent, int order) {}
    private record Tree(Set<UUID> ids, Map<UUID, Edge> edges) {}

    static Converted convert(CompoundTag source, UUID train, int index, TrackGraph graph,
            DimensionPalette dimensions, int sourceVersion, HolderLookup.Provider registries) {
        keys(source, "carriage", "FirstBogey", "SecondBogey", "Spacing", "FrontConductor", "BackConductor", "Stalled",
            "Entity", "Passengers", "EntityPositioning");
        int spacing = integer(source, "Spacing");
        if (spacing < 0 || source.contains("SecondBogey") && spacing == 0) throw invalid("Invalid original carriage bogey spacing");
        bool(source, "FrontConductor"); bool(source, "BackConductor"); bool(source, "Stalled");
        var result = source.copy();
        result.put("FirstBogey", NeoForgeTrainStateImport.bogey(compound(source, "FirstBogey"), graph, dimensions));
        if (source.contains("SecondBogey"))
            result.put("SecondBogey", NeoForgeTrainStateImport.bogey(compound(source, "SecondBogey"), graph, dimensions));
        var body = NeoForgeCarriageBodyImport.convert(compound(source, "Entity"), train, index, sourceVersion, registries);
        bogeyBodies(source, body, spacing);
        var cargo = NeoForgeCarriageCargoImport.convert(source, dimensions, sourceVersion, registries, com.simibubi.create.foundation.utility.NeoForgeMigrationScope.train(train, index));
        var identities = new HashSet<UUID>(); identities.add(body.body());
        var passengers = new CompoundTag();
        for (String key : compound(source, "Passengers").keySet()) {
            if (!key.startsWith("Seat")) throw invalid("Unknown original carriage passenger row");
            int seat;
            try { seat = Integer.parseInt(key.substring(4)); }
            catch (NumberFormatException malformed) { throw invalid("Malformed original carriage passenger seat"); }
            if (seat < 0 || seat >= body.seats().size() || !key.equals("Seat" + seat))
                throw invalid("Original passenger seat is noncanonical/outside its actual carriage");
            var original = compound(compound(source, "Passengers"), key);
            CompoundTag actual;
            if (original.contains("PlayerPassenger")) {
                keys(original, "direct original player reference", "PlayerPassenger");
                var player = uuid(original, "PlayerPassenger");
                if (!identities.add(player)) throw invalid("Original body/player/passenger UUID has two carriage owners");
                actual = original.copy();
            } else {
                var before = tree(original, false);
                actual = NeoForgeStandaloneEntityImport.convertTree(original, sourceVersion, registries);
                var after = tree(actual, true);
                if (!before.equals(after)) throw invalid("Vanilla passenger migration changed original UUID/parent/order custody");
                // This typed graph is a pure validation proposal only. The original body UUID is
                // used as an existing validation revision; no new epoch/token is emitted or owned.
                var proposal = CarriagePassengerGraph.fromLegacy(actual, body.body());
                proposal.requireDisjointBodies(Set.of(body.body()));
                if (!proposal.identities().equals(before.ids)) throw invalid("Native passenger parser lost an actual source passenger");
                for (var id : before.ids) if (!identities.add(id)) throw invalid("Original passenger body UUID has two carriage owners");
            }
            passengers.put(key, actual);
        }
        result.put("Entity", body.entity()); result.put("Passengers", passengers);
        result.put("EntityPositioning", cargo.positioning()); result.putInt("CargoVersion", 1); result.put("Cargo", cargo.cargo());
        // Native decoding validates real bogeys, dimensional frames, body extent, mounted codecs
        // and exactly one real TrainCargoManager. It neither loads a fake world nor binds a manager.
        var nativeCarriage = Carriage.read(result, registries, graph, dimensions);
        var custody = nativeCarriage.custodyIdentities();
        var nativeIds = new HashSet<UUID>(custody.bodies()); nativeIds.addAll(custody.passengers());
        if (!nativeIds.equals(identities) || custody.bodies().size() != 1 || !nativeCarriage.storage.ownsContents())
            throw invalid("Native carriage decoding did not retain its exact original body/passenger/cargo custody");
        return new Converted(result, Set.copyOf(identities));
    }

    private static void bogeyBodies(CompoundTag source, NeoForgeCarriageBodyImport.Converted body, int spacing) {
        var bogeys = body.blocks().entrySet().stream().filter(entry -> entry.getValue().getBlock() instanceof AbstractBogeyBlock<?>).toList();
        boolean second = source.contains("SecondBogey");
        if (bogeys.size() != (second ? 2 : 1)) throw invalid("Original carriage bogey model/body block cardinality disagrees");
        var firstState = body.blocks().get(BlockPos.ZERO);
        if (firstState == null || !(firstState.getBlock() instanceof AbstractBogeyBlock<?> firstBlock)
                || !BuiltInRegistries.BLOCK.getKey(firstBlock).toString().equals(string(compound(source, "FirstBogey"), "Type"))
                || firstBlock.isUpsideDown(firstState) != bool(compound(source, "FirstBogey"), "UpsideDown"))
            throw invalid("Original first bogey model does not match its actual anchored body block");
        var direction = Direction.valueOf(string(compound(compound(source, "Entity"), "Contraption"), "AssemblyDirection"));
        if (firstState.getValue(AbstractBogeyBlock.AXIS) != direction.getAxis())
            throw invalid("Original first bogey body axis disagrees with its actual assembly direction");
        if (second) for (var entry : bogeys) if (!entry.getKey().equals(BlockPos.ZERO)) {
            var position = entry.getKey(); var state = entry.getValue(); var block = (AbstractBogeyBlock<?>) state.getBlock();
            if (position.getY() != 0 || direction.getAxis() == Direction.Axis.X && (position.getZ() != 0 || Math.abs((long) position.getX()) != spacing)
                    || direction.getAxis() == Direction.Axis.Z && (position.getX() != 0 || Math.abs((long) position.getZ()) != spacing)
                    || state.getValue(AbstractBogeyBlock.AXIS) != direction.getAxis()
                    || !BuiltInRegistries.BLOCK.getKey(block).toString().equals(string(compound(source, "SecondBogey"), "Type"))
                    || block.isUpsideDown(state) != bool(compound(source, "SecondBogey"), "UpsideDown"))
                throw invalid("Original second bogey model/spacing does not match its actual body block");
        }
    }

    private static Tree tree(CompoundTag source, boolean target) {
        record Visit(CompoundTag tag, UUID parent, int order, int depth) {}
        var pending = new ArrayDeque<Visit>(); pending.add(new Visit(source, null, 0, 0));
        var ids = new HashSet<UUID>(); var edges = new HashMap<UUID, Edge>();
        while (!pending.isEmpty()) {
            var visit = pending.removeFirst();
            if (visit.depth > CarriagePassengerGraph.MAX_DEPTH || ids.size() >= CarriagePassengerGraph.MAX_NODES)
                throw invalid("Original passenger tree exceeds actual native bounds");
            var data = visit.tag;
            if (data.contains("PlayerPassenger") || data.contains("GraphVersion"))
                throw invalid("Original NPC tree cannot include an invented player/graph schema");
            UUID id = uuid(data, "UUID");
            if (!ids.add(id)) throw invalid("Duplicate original NPC tree UUID");
            if (visit.parent != null) edges.put(id, new Edge(visit.parent, visit.order));
            var type = Identifier.parse(string(data, "id"));
            if (!target) {
                if (!type.getNamespace().equals("minecraft") && !Set.of("create:package", "create:crafting_blueprint", "create:potato_projectile", "create:seat", "create:super_glue").contains(type.toString()))
                    throw invalid("Original train NPC needs its actual supported complete leaf schema; independent contraption/global owners cannot become train passengers: " + type);
                if (data.contains("neoforge:attachments") && !compound(data,"neoforge:attachments").isEmpty())
                    throw invalid("Original train NPC cannot acquire a separate minecart controller or attachment owner");
            }
            if (type.toString().equals("minecraft:player") || target &&
                    (!BuiltInRegistries.ENTITY_TYPE.containsKey(type) || !BuiltInRegistries.ENTITY_TYPE.getValue(type).canSerialize()))
                throw invalid("Unknown/nonserializable original NPC type requires a genuine pre-factory schema bridge: " + type);
            if (data.contains("Passengers")) {
                var passengers = list(data, "Passengers"); int order = 0;
                for (Tag value : passengers) {
                    if (!(value instanceof CompoundTag child)) throw invalid("Malformed original NPC child");
                    pending.addLast(new Visit(child, id, order++, visit.depth + 1));
                }
            }
        }
        return new Tree(Set.copyOf(ids), Map.copyOf(edges));
    }
    private NeoForgeCarriageImport() {}
}
