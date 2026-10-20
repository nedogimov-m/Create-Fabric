package com.simibubi.create.content.trains.entity;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Dynamic;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMovementContextImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeVanillaEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeTextComponentImport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.state.BlockState;

/** Strict genuine carriage geometry/owner structure, with explicit shared BE/actor leaf conversion. */
final class NeoForgeCarriageBodyImport {
    record Converted(CompoundTag entity, List<BlockPos> seats, Map<BlockPos, BlockState> blocks, UUID body) {}

    static Converted convert(CompoundTag source, UUID train, int carriage, int sourceVersion,
            HolderLookup.Provider registries) {
        keys(source, "carriage body entity", "id", "UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
            "Invulnerable", "PortalCooldown", "CustomName", "CustomNameVisible", "Silent", "NoGravity", "Glowing", "HasVisualFire",
            "Tags", "TicksFrozen", "Contraption", "Stalled", "Initialized", "InitialOrientation", "CachedMotion", "ForceYaw", "Placed",
            "Yaw", "Pitch", "OnCoupling", "TrainId", "CarriageIndex");
        if (!string(source, "id").equals("create:carriage_contraption")
                || !BuiltInRegistries.ENTITY_TYPE.getKey(AllEntityTypes.CARRIAGE_CONTRAPTION).toString().equals("create:carriage_contraption"))
            throw invalid("Original carriage body has no actual matching registered native entity type");
        NeoForgeVanillaEntityImport.validateBase(source);
        UUID body = uuid(source, "UUID");
        if (!uuid(source, "TrainId").equals(train) || integer(source, "CarriageIndex") != carriage)
            throw invalid("Original carriage body names a different actual Train/carriage owner");
        if (source.contains("Passengers")) throw invalid("Original carriage body cannot duplicate its globally owned passenger rows");
        bool(source, "Stalled"); bool(source, "Initialized"); bool(source, "Placed");
        floating(source, "Yaw"); floating(source, "Pitch");
        if (source.contains("ForceYaw")) floating(source, "ForceYaw");
        if (source.contains("InitialOrientation")) Direction.valueOf(string(source, "InitialOrientation"));
        if (source.contains("OnCoupling") || bool(source, "Placed"))
            throw invalid("A globally owned original railway body cannot also have a mounted-item/cart owner");
        if (source.contains("CachedMotion")) {
            var motion = vector(list(source, "CachedMotion"));
            for (Tag coordinate : motion)
                if (((net.minecraft.nbt.DoubleTag) coordinate).doubleValue() != 0)
                    throw invalid("A railway body cannot silently consume unresolved original minecart cached motion");
        }
        vector(list(source, "Pos")); vector(list(source, "Motion"));
        var rotation = list(source, "Rotation");
        if (rotation.size() != 2) throw invalid("Original entity rotation must retain two floats");
        for (Tag value : rotation) if (!(value instanceof FloatTag angle) || !Float.isFinite(angle.floatValue()))
            throw invalid("Malformed original entity rotation");
        var converted = body(compound(source, "Contraption"), sourceVersion, registries, NeoForgeMigrationScope.train(train, carriage));
        var entity = fixed(source, References.ENTITY, sourceVersion);
        if (!uuid(entity, "UUID").equals(body) || !string(entity, "id").equals("create:carriage_contraption"))
            throw invalid("Vanilla entity migration cannot change an original Create body UUID/type");
        entity.put("Contraption", converted.tag);
        if (source.contains("CustomName")) {
            string(source, "CustomName");
            Tag name = NeoForgeTextComponentImport.flattened((net.minecraft.nbt.StringTag) source.get("CustomName"), sourceVersion, registries, NeoForgeMigrationScope.entity(body).item("custom_name", 0));
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            var component = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(ops, name).getOrThrow();
            entity.put("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(ops, component).getOrThrow());
        }
        // Actual native Entity.load reads fall_distance; custom Create entity IDs are not allowed
        // to hide this genuine vanilla base-field transformation inside an untyped DFU remainder.
        if (source.contains("FallDistance")) {
            entity.putDouble("fall_distance", floating(source, "FallDistance")); entity.remove("FallDistance");
        }
        // Source readAdditional applies a genuine explicit ForceYaw before carriage anchoring.
        if (source.contains("ForceYaw")) entity.putFloat("Yaw", floating(source, "ForceYaw"));
        return new Converted(entity, converted.seats, converted.blocks, body);
    }

    private record Body(CompoundTag tag, List<BlockPos> seats, Map<BlockPos, BlockState> blocks) {}
    private static Body body(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "carriage contraption", "Type", "Blocks", "CapturedMultiblocks", "Actors", "DisabledActors", "Interactors",
            "Superglue", "Seats", "SubContraptions", "Anchor", "Stalled", "BottomlessSupply", "BoundsFront", "AssemblyDirection",
            "FrontControls", "BackControls", "FrontBlazeConductor", "BackBlazeConductor", "ConductorSeats", "SoundQueue");
        if (!string(source, "Type").equals("create:carriage")) throw invalid("Original train body is not an actual carriage contraption");
        var direction = Direction.valueOf(string(source, "AssemblyDirection"));
        if (!direction.getAxis().isHorizontal()) throw invalid("Original carriage assembly direction is not horizontal");
        for (String field : new String[] {"Stalled", "BottomlessSupply", "FrontControls", "BackControls", "FrontBlazeConductor", "BackBlazeConductor"}) bool(source, field);
        pos(compound(source, "Anchor"));
        var result = source.copy();
        result.put("AssemblyDirection", Direction.CODEC.encodeStart(NbtOps.INSTANCE, direction).getOrThrow());
        result.putString("AssemblyPhase", "REMOVED"); result.putBoolean("Disassembled", false);
        var states = new LinkedHashMap<BlockPos, BlockState>();
        result.put("Blocks", blocks(source.get("Blocks"), states, sourceVersion, registries, scope));
        if (states.size() <= 1) throw invalid("Original carriage assembly requires more than one actual body block");
        var seats = positions(list(source, "Seats"), states.keySet(), "carriage seat");
        result.put("Seats", positionRows(seats));
        var conductorSeats = new ListTag(); var conductorOwners = new HashSet<BlockPos>();
        for (Tag value : list(source, "ConductorSeats")) {
            if (!(value instanceof CompoundTag seat)) throw invalid("Malformed original conductor seat");
            keys(seat, "conductor seat", "Pos", "Forward", "Backward");
            var position = pos(compound(seat, "Pos"));
            if (!seats.contains(position) || !conductorOwners.add(position)) throw invalid("Original conductor seat is duplicate/outside actual carriage seats");
            bool(seat, "Forward"); bool(seat, "Backward"); var next = seat.copy(); next.store("Pos", BlockPos.CODEC, position); conductorSeats.add(next);
        }
        result.put("ConductorSeats", conductorSeats);
        result.put("CapturedMultiblocks", multiblocks(list(source, "CapturedMultiblocks"), states.keySet()));
        result.put("Actors", actors(list(source, "Actors"), states, sourceVersion, registries, scope));
        var disabled = new ListTag();
        for (Tag value : list(source, "DisabledActors")) {
            if (!(value instanceof CompoundTag stack)) throw invalid("Malformed original disabled actor filter");
            disabled.add(item(stack, sourceVersion, registries, scope.item("disabled_actor", disabled.size())));
        }
        result.put("DisabledActors", disabled);
        var interactors = positions(list(source, "Interactors"), states.keySet(), "interactor");
        var actualInteractors = new HashSet<BlockPos>();
        states.forEach((position, state) -> { if (MovingInteractionBehaviour.REGISTRY.get(state) != null) actualInteractors.add(position); });
        if (!Set.copyOf(interactors).equals(actualInteractors)) throw invalid("Original/native interaction registrations disagree about captured body positions");
        result.put("Interactors", positionRows(interactors));
        // Actual source CarriageContraption.canBeStabilized always returns false. A nonempty child
        // list would have no separately serialized child body after source Carriage.serialize strips
        // entity passengers, so it cannot be normalized into an invented native child receipt.
        if (!list(source, "SubContraptions").isEmpty()) throw invalid("Original carriage names unowned stabilized child bodies");
        glue(list(source, "Superglue"));
        if (source.contains("BoundsFront")) bounds(list(source, "BoundsFront"));
        result.put("SoundQueue", sound(compound(source, "SoundQueue"), states));
        return new Body(result, List.copyOf(seats), Map.copyOf(states));
    }

    private static CompoundTag blocks(Tag source, Map<BlockPos, BlockState> states, int sourceVersion,
            HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var palette = new ListTag(); var rows = new ListTag(); var nativeStates = new ArrayList<BlockState>();
        if (source instanceof CompoundTag packed) {
            keys(packed, "captured block palette", "Palette", "BlockList");
            for (Tag value : list(packed, "Palette")) {
                if (!(value instanceof CompoundTag state)) throw invalid("Malformed original captured block state");
                var actual = state(state, sourceVersion, registries); nativeStates.add(actual);
                palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(actual));
            }
            for (Tag value : list(packed, "BlockList")) {
                if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original captured block row");
                keys(entry, "captured block row", "Pos", "State", "Data", "UpdateTag");
                if (!(entry.get("Pos") instanceof LongTag)) throw invalid("Malformed original packed block position");
                int index = integer(entry, "State");
                if (index < 0 || index >= nativeStates.size()) throw invalid("Original captured state index is outside its actual palette");
                var position = BlockPos.of(entry.getLong("Pos").orElseThrow()); var actual = nativeStates.get(index);
                if (states.putIfAbsent(position, actual) != null) throw invalid("Duplicate original body block position");
                rows.add(block(entry, position, actual, sourceVersion, registries, scope));
            }
        } else if (source instanceof ListTag oldBlocks) {
            for (Tag value : oldBlocks) {
                if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original legacy captured block row");
                keys(entry, "legacy captured block row", "Pos", "X", "Y", "Z", "Block", "Data", "UpdateTag");
                BlockPos position;
                if (entry.contains("Pos")) {
                    if (entry.contains("X") || entry.contains("Y") || entry.contains("Z")) throw invalid("Ambiguous original legacy block position");
                    position = pos(compound(entry, "Pos"));
                } else {
                    var coordinates = new CompoundTag(); for (String axis : new String[] {"X", "Y", "Z"}) coordinates.putInt(axis, integer(entry, axis));
                    position = pos(coordinates);
                }
                var actual = state(compound(entry, "Block"), sourceVersion, registries);
                if (states.putIfAbsent(position, actual) != null) throw invalid("Duplicate original legacy body block position");
                int index = nativeStates.indexOf(actual);
                if (index < 0) { index = nativeStates.size(); nativeStates.add(actual); palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(actual)); }
                var next = block(entry, position, actual, sourceVersion, registries, scope);
                for (String old : new String[] {"X", "Y", "Z", "Block"}) next.remove(old);
                next.putLong("Pos", position.asLong()); next.putInt("State", index); rows.add(next);
            }
        } else throw invalid("Original actual body block geometry is missing");
        var result = new CompoundTag(); result.put("Palette", palette); result.put("BlockList", rows); return result;
    }

    private static BlockState state(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
        keys(source, "block state", "Name", "Properties");
        Identifier.parse(string(source, "Name"));
        if (source.contains("Properties")) for (String key : compound(source, "Properties").keySet()) string(compound(source, "Properties"), key);
        var fixed = fixed(source, References.BLOCK_STATE, sourceVersion);
        // This explicit source/target schema transformation is the same actual Name/Properties to
        // id/properties mapping used by native Contraption.readState for source captured palettes.
        if (!fixed.contains("id") && fixed.contains("Name")) {
            fixed.putString("id", string(fixed, "Name")); fixed.remove("Name");
            if (fixed.contains("Properties")) { fixed.put("properties", compound(fixed, "Properties").copy()); fixed.remove("Properties"); }
        }
        keys(fixed, "native block state", "id", "properties");
        var id = Identifier.parse(string(fixed, "id"));
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw invalid("Original block has no actual registered native identity " + id);
        var block = BuiltInRegistries.BLOCK.getValue(id);
        if (fixed.contains("properties")) for (String key : compound(fixed, "properties").keySet()) {
            var property = block.getStateDefinition().getProperty(key);
            if (property == null || property.getValue(string(compound(fixed, "properties"), key)).isEmpty())
                throw invalid("Unknown original/native block property cannot become a silent default: " + id + "/" + key);
        }
        var actual = BlockState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), fixed).getOrThrow();
        return actual;
    }

    private static CompoundTag block(CompoundTag source, BlockPos position, BlockState actual, int sourceVersion,
            HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return com.simibubi.create.foundation.utility.NeoForgeCapturedBlockEntityImport.convert(
            source, position, actual, sourceVersion, registries, scope);
    }

    private static ListTag actors(ListTag source, Map<BlockPos, BlockState> states, int sourceVersion,
            HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var result = new ListTag(); var seen = new HashSet<BlockPos>();
        for (Tag value : source) {
            if (!(value instanceof CompoundTag actor)) throw invalid("Malformed original movement context");
            var position = pos(compound(actor, "Pos")); var state = states.get(position);
            if (state == null || !seen.add(position) || MovementBehaviour.REGISTRY.get(state) == null)
                throw invalid("Original actor is duplicate/outside geometry or lacks its actual native behavior");
            var next = NeoForgeMovementContextImport.convert(state, actor, sourceVersion, registries, scope.block(position));
            next.store("Pos", BlockPos.CODEC, position); result.add(next);
        }
        // A missing source actor would call native startMoving during decoding and invent a fresh
        // context over an existing owner. Require actual captured source contexts for every actor.
        for (var entry : states.entrySet())
            if (MovementBehaviour.REGISTRY.get(entry.getValue()) != null && !seen.contains(entry.getKey()))
                throw invalid("Original carriage is missing a genuine captured movement context");
        return result;
    }

    private static List<BlockPos> positions(ListTag source, Set<BlockPos> body, String context) {
        var result = new ArrayList<BlockPos>(); var seen = new HashSet<BlockPos>();
        for (Tag value : source) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original " + context);
            keys(entry, context, "Pos"); var position = pos(compound(entry, "Pos"));
            if (!body.contains(position) || !seen.add(position)) throw invalid("Duplicate/outside-body original " + context);
            result.add(position);
        }
        return result;
    }
    private static ListTag positionRows(List<BlockPos> positions) {
        var result = new ListTag(); for (var position : positions) { var row = new CompoundTag(); row.store("Pos", BlockPos.CODEC, position); result.add(row); } return result;
    }
    private static ListTag multiblocks(ListTag source, Set<BlockPos> body) {
        var result = new ListTag(); var roots = new HashSet<BlockPos>(); var allParts = new HashSet<BlockPos>();
        for (Tag value : source) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original captured multiblock");
            keys(entry, "captured multiblock", "Controller", "Parts"); var controller = pos(compound(entry, "Controller"));
            if (!body.contains(controller) || !roots.add(controller)) throw invalid("Duplicate/outside-body original multiblock controller");
            var parts = positions(list(entry, "Parts"), body, "captured multiblock part");
            if (parts.isEmpty()) throw invalid("Original captured multiblock has no actual parts");
            for (var part : parts) if (!allParts.add(part)) throw invalid("Original captured multiblock part has two controllers");
            var next = new CompoundTag(); next.store("Controller", BlockPos.CODEC, controller); next.put("Parts", positionRows(parts)); result.add(next);
        }
        return result;
    }
    private static CompoundTag sound(CompoundTag source, Map<BlockPos, BlockState> body) {
        keys(source, "carriage sound queue", "Offset", "Sources"); int offset = integer(source, "Offset");
        var result = source.copy(); var rows = new ListTag();
        for (Tag value : list(source, "Sources")) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original carriage sound source");
            keys(entry, "arrival sound source", "Tick", "Pos"); int tick = integer(entry, "Tick"); Math.addExact(tick, offset);
            var position = pos(compound(entry, "Pos"));
            if (!body.containsKey(position) || !ArrivalSoundQueue.isPlayable(body.get(position))) throw invalid("Original arrival sound source lacks its actual playable body block");
            var next = entry.copy(); next.store("Pos", BlockPos.CODEC, position); rows.add(next);
        }
        result.put("Sources", rows); return result;
    }
    private static void bounds(ListTag source) {
        if (source.size() != 6) throw invalid("Original body bounds must retain six floats");
        for (Tag value : source) if (!(value instanceof FloatTag coordinate) || !Float.isFinite(coordinate.floatValue())) throw invalid("Malformed original body bounds");
        for (int axis = 0; axis < 3; axis++) if (((FloatTag) source.get(axis)).floatValue() > ((FloatTag) source.get(axis + 3)).floatValue()) throw invalid("Inverted original body bounds");
    }
    private static void glue(ListTag source) {
        for (Tag value : source) {
            if (!(value instanceof CompoundTag box)) throw invalid("Malformed original superglue box");
            keys(box, "superglue box", "From", "To");
            var from = vector(list(box, "From")); var to = vector(list(box, "To"));
            for (int axis = 0; axis < 3; axis++)
                if (((net.minecraft.nbt.DoubleTag) from.get(axis)).doubleValue() > ((net.minecraft.nbt.DoubleTag) to.get(axis)).doubleValue())
                    throw invalid("Inverted original superglue extent");
        }
    }
    static CompoundTag fixed(CompoundTag source, com.mojang.datafixers.DSL.TypeReference type, int sourceVersion) {
        Tag result = DataFixers.getDataFixer().update(type, new Dynamic<Tag>(NbtOps.INSTANCE, source.copy()), sourceVersion,
            SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if (!(result instanceof CompoundTag data)) throw invalid("Actual vanilla migration lost an original compound owner");
        return data;
    }
    private NeoForgeCarriageBodyImport() {}
}
