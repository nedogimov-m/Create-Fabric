package com.simibubi.create.content.contraptions;

import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.Create;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/** Source mounted cargo conversion; no source/default inventory can silently replace an unknown owner. */
public final class NeoForgeMountedStorageImport {
    public static CompoundTag cargo(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original mounted cargo version");
        keys(source, "carriage cargo", "items", "fluids", "TicksSinceLastExchange");
        var result = new CompoundTag();
        if (source.contains("TicksSinceLastExchange")) {
            int idle = integer(source, "TicksSinceLastExchange"); if (idle < 0) throw invalid("Negative original cargo idle timer"); result.putInt("TicksSinceLastExchange", idle);
        }
        for (String kind : Set.of("items", "fluids")) {
            var entries = new ListTag(); var positions = new HashSet<BlockPos>();
            if (source.contains(kind)) for (Tag value : list(source, kind)) {
                if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original mounted cargo row");
                keys(entry, "mounted cargo row", "pos", "storage");
                var position = pos(compound(entry, "pos")); if (!positions.add(position)) throw invalid("Duplicate original mounted cargo position");
                var row = new CompoundTag(); row.put("pos", compound(entry, "pos").copy());
                row.put("storage", kind.equals("items") ? itemStorage(compound(entry, "storage"), sourceVersion, registries, scope == null ? null : scope.block(position).item("mounted_items", 0))
                    : fluidStorage(compound(entry, "storage"), registries, scope == null ? null : scope.block(position).item("mounted_fluids", 0))); entries.add(row);
            }
            if (!entries.isEmpty()) result.put(kind, entries);
        }
        return result;
    }

    public static CompoundTag itemStorage(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original mounted item version");
        keys(source, "mounted item storage", "type", "value"); String type = string(source, "type");
        var result = new CompoundTag(); result.putString("type", type);
        switch (type) {
            case "create:simple", "create:chest", "create:dispenser", "create:vault", "create:fallback" ->
                result.put("value", slots(compound(source, "value"), 65536, sourceVersion, registries, scope));
            case "create:depot", "create:creative_crate" -> result.put("value", item(compound(source, "value"), sourceVersion, registries, child(scope, "value", 0)));
            case "create:toolbox" -> {
                var value = compound(source, "value"); keys(value, "toolbox", "items", "filters");
                var next = new CompoundTag(); next.put("items", slots(compound(value, "items"), 32, sourceVersion, registries, child(scope, "stock", 0)));
                var filters = list(value, "filters"); if (filters.size() != 8) throw invalid("Source toolbox must retain eight filters");
                var converted = new ListTag();
                for (Tag entry : filters) {
                    if (!(entry instanceof CompoundTag filter)) throw invalid("Malformed original toolbox filter");
                    var migrated = item(filter, sourceVersion, registries, child(scope, "filter", converted.size()));
                    if (!migrated.isEmpty() && migrated.getIntOr("count", 1) != 1) throw invalid("Source toolbox filter must retain its one-item template count");
                    converted.add(migrated);
                }
                next.put("filters", converted); result.put("value", next);
            }
            default -> throw invalid("Original mounted type has no reviewed native schema/owner: " + type);
        }
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var nativeStorage = MountedItemStorage.CODEC.parse(ops, result).getOrThrow();
        return (CompoundTag) MountedItemStorage.CODEC.encodeStart(ops, nativeStorage).getOrThrow();
    }
    private static CompoundTag slots(CompoundTag source, int maximum, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "ItemSlots", "items", "size"); int size = integer(source, "size");
        if (size < 0 || size > maximum) throw invalid("Original ItemSlots exceeds supported native bounds");
        var values = compound(source, "items"); var next = new CompoundTag(); var identities = new HashSet<Integer>();
        for (String key : values.keySet()) {
            int index;
            try { index = Integer.parseInt(key); } catch (NumberFormatException malformed) { throw invalid("Malformed original sparse slot index " + key); }
            if (index < 0 || index >= size || !Integer.toString(index).equals(key) || !identities.add(index))
                throw invalid("Duplicate/noncanonical/out-of-range original sparse slot " + key);
            var stack = item(compound(values, key), sourceVersion, registries, child(scope, "slot", index));
            if (stack.isEmpty()) throw invalid("Original sparse item entry cannot be empty"); next.put(key, stack);
        }
        var result = new CompoundTag(); result.put("items", next); result.putInt("size", size); return result;
    }

    public static CompoundTag fluidStorage(CompoundTag source, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        String type = string(source, "type"); var result = new CompoundTag(); result.putString("type", type);
        CompoundTag original;
        if (type.equals("create:fluid_tank")) { keys(source, "fluid tank", "type", "capacity", "fluid"); original = source; }
        else if (type.equals("create:creative_fluid_tank")) { keys(source, "creative fluid tank", "type", "value"); original = compound(source, "value"); keys(original, "creative fluid contents", "capacity", "fluid"); }
        else throw invalid("Original mounted fluid type has no reviewed native schema/owner: " + type);
        int capacityMb = integer(original, "capacity"); if (capacityMb < 0) throw invalid("Negative original fluid capacity");
        long capacity = Math.multiplyExact((long) capacityMb, 81);
        var contents = compound(original, "fluid"); var fluid = fluid(contents, registries, scope);
        if (fluid.amount > capacity) throw invalid("Original fluid exceeds its actual saved capacity");
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (type.equals("create:fluid_tank")) {
            var amount = new CompoundTag(); amount.put("variant", FluidVariant.CODEC.encodeStart(ops, fluid.variant).getOrThrow()); amount.putLong("amount", fluid.amount);
            var value = new CompoundTag(); value.putLong("capacity", capacity); value.put("fluid", amount); result.put("value", value);
        } else {
            if (!fluid.variant.isBlank() && fluid.amount != capacity) throw invalid("Original creative template amount disagrees with its capacity");
            result.putLong("capacity", capacity); result.put("variant", FluidVariant.CODEC.encodeStart(ops, fluid.variant).getOrThrow());
        }
        var nativeStorage = MountedFluidStorage.CODEC.parse(ops, result).getOrThrow();
        return (CompoundTag) MountedFluidStorage.CODEC.encodeStart(ops, nativeStorage).getOrThrow();
    }
    private record Fluid(FluidVariant variant, long amount) {}
    private static Fluid fluid(CompoundTag source, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var converted = com.simibubi.create.foundation.utility.NeoForgeFluidStackImport.convert(source, NeoForgeSavedDataImport.SOURCE_DATA_VERSION, registries, scope);
        return new Fluid(converted.variant(), converted.amount());
    }
    private NeoForgeMountedStorageImport() {}
    public static CompoundTag cargo(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) { return cargo(source, sourceVersion, registries, null); }
    public static CompoundTag itemStorage(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) { return itemStorage(source, sourceVersion, registries, null); }
    public static CompoundTag fluidStorage(CompoundTag source, HolderLookup.Provider registries) { return fluidStorage(source, registries, null); }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) { return scope == null ? null : scope.item(role, index); }

}
