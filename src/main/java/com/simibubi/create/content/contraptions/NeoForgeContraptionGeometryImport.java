package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Dynamic;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.state.BlockState;

/** Genuine common source3955 geometry. Subtype and actual entity/item ownership are checked by callers. */
final class NeoForgeContraptionGeometryImport {
    record Geometry(CompoundTag tag, List<BlockPos> seats, Map<BlockPos,BlockState> blocks) {}
    static Geometry convert(CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope) {
        Objects.requireNonNull(scope,"Actual containing original structure owner required");
        bool(source,"Stalled");bool(source,"BottomlessSupply");var anchor=pos(compound(source,"Anchor"));
        var result=source.copy();result.store("Anchor",BlockPos.CODEC,anchor);
        result.putString("AssemblyPhase","REMOVED");result.putBoolean("Disassembled",false);
        var states=new LinkedHashMap<BlockPos,BlockState>();result.put("Blocks",blocks(source.get("Blocks"),states,version,registries,scope));
        if(states.isEmpty())throw invalid("Original contraption has no captured geometry");
        var seats=positions(list(source,"Seats"),states.keySet(),"contraption seat");result.put("Seats",positionRows(seats));
        result.put("CapturedMultiblocks",multiblocks(list(source,"CapturedMultiblocks"),states.keySet()));
        result.put("Actors",actors(list(source,"Actors"),states,version,registries,scope));
        var disabled=new ListTag();int index=0;for(var entry:list(source,"DisabledActors")){
            if(!(entry instanceof CompoundTag stack))throw invalid("Malformed original disabled actor");
            disabled.add(item(stack,version,registries,scope.item("disabled_actor",index)));index++;
        }result.put("DisabledActors",disabled);
        var interactors=positions(list(source,"Interactors"),states.keySet(),"contraption interactor");var actual=new HashSet<BlockPos>();
        states.forEach((position,state)->{if(MovingInteractionBehaviour.REGISTRY.get(state)!=null)actual.add(position);});
        if(!Set.copyOf(interactors).equals(actual))throw invalid("Original/native interaction registrations disagree about captured geometry");
        result.put("Interactors",positionRows(interactors));glue(list(source,"Superglue"));if(source.contains("BoundsFront"))bounds(list(source,"BoundsFront"));
        var cargo=new CompoundTag();for(String kind:Set.of("items","fluids"))if(source.contains(kind))cargo.put(kind,source.get(kind).copy());
        var convertedCargo=NeoForgeMountedStorageImport.cargo(cargo,version,registries,scope);
        for(String kind:Set.of("items","fluids"))if(convertedCargo.contains(kind)){
            for(var value:list(convertedCargo,kind)){
                var position=pos(compound((CompoundTag)value,"pos"));if(!states.containsKey(position))throw invalid("Original mounted storage lies outside captured geometry");
            }result.put(kind,convertedCargo.get(kind));
        }
        return new Geometry(result,List.copyOf(seats),Map.copyOf(states));
    }
    static CompoundTag blocks(Tag source, Map<BlockPos, BlockState> states, int sourceVersion,
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
                // Genuine CompoundTag.CODEC hover JSON loses numeric NBT widths:
                // source LongTag0 decodes as ByteTag0. All integral widths retain
                // the exact saved64-bit packed value; float/string truncation refuses.
                var packedPosition=entry.get("Pos");
                if (!(packedPosition instanceof LongTag || packedPosition instanceof IntTag || packedPosition instanceof ShortTag || packedPosition instanceof ByteTag)) throw invalid("Malformed original packed block position");
                int index = integer(entry, "State");
                if (index < 0 || index >= nativeStates.size()) throw invalid("Original captured state index is outside its actual palette");
                var position = BlockPos.of(((net.minecraft.nbt.NumericTag)packedPosition).longValue()); var actual = nativeStates.get(index);
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

    static BlockState state(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
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

    static CompoundTag block(CompoundTag source, BlockPos position, BlockState actual, int sourceVersion,
            HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return NeoForgeCapturedBlockEntityImport.convert(source, position, actual, sourceVersion, registries, scope);
    }

    static ListTag actors(ListTag source, Map<BlockPos, BlockState> states, int sourceVersion,
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
                throw invalid("Original contraption is missing a genuine captured movement context");
        return result;
    }

    static List<BlockPos> positions(ListTag source, Set<BlockPos> body, String context) {
        var result = new ArrayList<BlockPos>(); var seen = new HashSet<BlockPos>();
        for (Tag value : source) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original " + context);
            keys(entry, context, "Pos"); var position = pos(compound(entry, "Pos"));
            if (!body.contains(position) || !seen.add(position)) throw invalid("Duplicate/outside-body original " + context);
            result.add(position);
        }
        return result;
    }
    static ListTag positionRows(List<BlockPos> positions) {
        var result = new ListTag(); for (var position : positions) { var row = new CompoundTag(); row.store("Pos", BlockPos.CODEC, position); result.add(row); } return result;
    }
    static ListTag multiblocks(ListTag source, Set<BlockPos> body) {
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
    static void bounds(ListTag source) {
        if (source.size() != 6) throw invalid("Original body bounds must retain six floats");
        for (Tag value : source) if (!(value instanceof FloatTag coordinate) || !Float.isFinite(coordinate.floatValue())) throw invalid("Malformed original body bounds");
        for (int axis = 0; axis < 3; axis++) if (((FloatTag) source.get(axis)).floatValue() > ((FloatTag) source.get(axis + 3)).floatValue()) throw invalid("Inverted original body bounds");
    }
    static void glue(ListTag source) {
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
    private NeoForgeContraptionGeometryImport() {}
}
