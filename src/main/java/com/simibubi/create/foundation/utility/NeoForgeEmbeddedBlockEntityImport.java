package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Actual falling-block BE payloads, including sparse vanilla stock; no world owner is published. */
final class NeoForgeEmbeddedBlockEntityImport {
    private static final Set<String> BASE = Set.of("id", "x", "y", "z", "keepPacked", "components", "NeoForgeData", "neoforge:attachments");
    private static int capacity(Identifier type) {
        if (!type.getNamespace().equals("minecraft")) return -1;
        return switch (type.getPath()) {
            case "chest", "trapped_chest", "barrel", "shulker_box" -> 27;
            case "dispenser", "dropper", "crafter" -> 9;
            case "hopper", "brewing_stand" -> 5;
            case "furnace", "smoker", "blast_furnace" -> 3;
            case "campfire" -> 4;
            case "chiseled_bookshelf" -> 6;
            default -> -1;
        };
    }
    static CompoundTag prepare(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        validateBase(type, source);
        CompoundTag result;
        if (type.getNamespace().equals("create")) result = NeoForgeBlockEntityImport.convert(type, source, version, registries, scope);
        else if (NeoForgeVanillaBlockEntityImport.supports(type)) result = NeoForgeVanillaBlockEntityImport.prepareForVanillaFixer(type, source, version, registries, scope);
        else {
            validateContainer(type, source); result = source.copy();
            if (source.contains("Items")) {
                var rows = new ListTag();
                for (Tag value : list(source, "Items")) {
                    var original = (CompoundTag) value; int slot = Byte.toUnsignedInt(((ByteTag) original.get("Slot")).byteValue());
                    var stack = original.copy(); stack.remove("Slot");
                    var prepared = NeoForgeItemComponentImport.prepareItemForVanillaFixer(stack, version, registries, slotScope(scope, slot));
                    prepared.put("Slot", original.get("Slot").copy()); rows.add(prepared);
                }
                result.put("Items", rows);
            }
            if (source.contains("components")) result.put("components", NeoForgeItemComponentImport.prepareMapForVanillaFixer(compound(source, "components"), version, registries, child(scope, "components", 0)));
            for (String extension : Set.of("NeoForgeData", "neoforge:attachments")) result.remove(extension);
        }
        // This temporary tag is the actual containing schema view. Its selector is
        // derived from saved BlockState, never asserted as an original saved id.
        // Restore the genuine source absence after the containing ENTITY_TREE DFU.
        if (!type.getNamespace().equals("create") && !result.contains("id")) result.putString("id", type.toString());
        return result;
    }
    static CompoundTag finish(Identifier type, CompoundTag source, CompoundTag actualFixed, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        validateBase(type, source); CompoundTag result;
        if (type.getNamespace().equals("create")) {
            var prepared = prepare(type, source, version, registries, scope);
            if (!prepared.equals(actualFixed)) throw invalid("Containing vanilla fixer changed an untyped already-native original Create BE payload");
            result = prepared;
        } else if (NeoForgeVanillaBlockEntityImport.supports(type)) {
            result = NeoForgeVanillaBlockEntityImport.finishAfterVanillaFixer(type, source, actualFixed, version, registries, scope);
        } else {
            validateContainer(type, source); result = actualFixed.copy();
            if (!type.equals(Identifier.parse(string(result, "id")))) throw invalid("Containing fixer changed the actual embedded vanilla container type");
            if (source.contains("Items")) {
                var original = list(source, "Items"); var actual = list(result, "Items");
                if (original.size() != actual.size()) throw invalid("Containing fixer changed original embedded sparse stock row count/order");
                var migrated = new ListTag();
                for (int i = 0; i < original.size(); i++) {
                    var before = (CompoundTag) original.get(i);
                    if (!(actual.get(i) instanceof CompoundTag after) || !before.get("Slot").equals(after.get("Slot"))) throw invalid("Containing fixer changed original embedded stock slot custody");
                    int slot = Byte.toUnsignedInt(((ByteTag) before.get("Slot")).byteValue()); var oldItem = before.copy(); oldItem.remove("Slot"); var nativeItem = after.copy(); nativeItem.remove("Slot");
                    nativeItem = NeoForgeItemComponentImport.finishItemAfterVanillaFixer(oldItem, nativeItem, version, registries, slotScope(scope, slot));
                    keys(nativeItem, "embedded native container stack", "id", "count", "components"); var ops = registries.createSerializationContext(NbtOps.INSTANCE);
                    var stack = ItemStack.CODEC.parse(ops, nativeItem).getOrThrow(); var canonical = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                    if (!item(oldItem, version, registries, slotScope(scope, slot)).equals(canonical)) throw invalid("Embedded native stock lost its exact source item/component/count lineage");
                    var next = ((CompoundTag) canonical).copy(); next.put("Slot", before.get("Slot").copy()); migrated.add(next);
                }
                result.put("Items", migrated);
            }
            if (source.contains("components")) result.put("components", NeoForgeItemComponentImport.finishMapAfterVanillaFixer(compound(source, "components"), result.getCompoundOrEmpty("components"), version, registries, child(scope, "components", 0)));
            if (source.contains("CustomName")) result.put("CustomName", NeoForgeTextComponentImport.flattened((StringTag) source.get("CustomName"), version, registries, child(scope, "customname", 0)));
            for (String extension : Set.of("NeoForgeData", "neoforge:attachments")) result.remove(extension);
        }
        for (String coordinate : Set.of("x", "y", "z")) {
            if (source.contains(coordinate) != result.contains(coordinate) || source.contains(coordinate) && !source.get(coordinate).equals(result.get(coordinate)))
                throw invalid("Embedded BE payload acquired/lost an original saved coordinate");
        }
        if (!source.contains("id")) result.remove("id");
        return result;
    }
    private static void validateBase(Identifier type, CompoundTag source) {
        if (source.contains("id") && !type.equals(Identifier.parse(string(source, "id")))) throw invalid("Saved falling BlockState and its actual BE payload type disagree");
        for (String coordinate : Set.of("x", "y", "z")) if (source.contains(coordinate) && !(source.get(coordinate) instanceof IntTag)) throw invalid("Original embedded BE coordinate lost its source int");
        if (source.contains("keepPacked")) bool(source, "keepPacked");
    }
    private static void validateContainer(Identifier type, CompoundTag source) {
        int capacity = capacity(type); if (capacity < 0) throw invalid("Unreviewed embedded vanilla BE material owner " + type);
        var allowed = new HashSet<>(BASE); allowed.add("Items");
        if (!Set.of("campfire", "chiseled_bookshelf").contains(type.getPath())) allowed.addAll(Set.of("CustomName", "Lock"));
        if (Set.of("chest", "trapped_chest", "barrel", "shulker_box", "dispenser", "dropper", "hopper", "crafter").contains(type.getPath())) allowed.addAll(Set.of("LootTable", "LootTableSeed"));
        allowed.addAll(switch (type.getPath()) {
            case "hopper" -> Set.of("TransferCooldown");
            case "furnace", "smoker", "blast_furnace" -> Set.of("BurnTime", "CookTime", "CookTimeTotal", "RecipesUsed");
            case "brewing_stand" -> Set.of("BrewTime", "Fuel");
            case "crafter" -> Set.of("crafting_ticks_remaining", "disabled_slots", "triggered");
            case "campfire" -> Set.of("CookingTimes", "CookingTotalTimes");
            case "chiseled_bookshelf" -> Set.of("last_interacted_slot");
            default -> Set.of();
        });
        keys(source, "embedded original " + type, allowed.toArray(String[]::new));
        for (String extension : Set.of("NeoForgeData", "neoforge:attachments")) if (source.contains(extension) && !compound(source, extension).isEmpty()) throw invalid("Embedded vanilla BE has a separate unconverted extension owner");
        for (String text : Set.of("CustomName", "Lock", "LootTable")) if (source.contains(text)) string(source, text);
        if (source.contains("LootTable")) { Identifier.parse(string(source, "LootTable")); if (source.contains("Items")) throw invalid("Embedded container cannot own source stock and deferred loot together"); }
        if (source.contains("LootTableSeed") && !(source.get("LootTableSeed") instanceof LongTag)) throw invalid("Original embedded loot seed lost its long");
        var occupied = new HashSet<Integer>();
        if (source.contains("Items")) for (Tag value : list(source, "Items")) {
            if (!(value instanceof CompoundTag row) || !(row.get("Slot") instanceof ByteTag slot)) throw invalid("Original embedded stock Slot must remain an actual BYTE");
            keys(row, "embedded original sparse stock", "Slot", "id", "count", "components"); int index = Byte.toUnsignedInt(slot.byteValue());
            if (index >= capacity || !occupied.add(index) || !row.contains("id")) throw invalid("Duplicate/outside/empty original embedded stock slot");
        }
        for (String key : Set.of("TransferCooldown", "BurnTime", "CookTime", "CookTimeTotal", "crafting_ticks_remaining", "last_interacted_slot")) if (source.contains(key) && !(source.get(key) instanceof IntTag)) throw invalid("Original embedded timer lost source int " + key);
        if (source.contains("BrewTime") && !(source.get("BrewTime") instanceof ShortTag) || source.contains("Fuel") && !(source.get("Fuel") instanceof ByteTag)) throw invalid("Original brewing timer/fuel lost actual scalar type");
        if (source.contains("triggered")) bool(source, "triggered");
        for (String key : Set.of("CookingTimes", "CookingTotalTimes", "disabled_slots")) if (source.contains(key) && !(source.get(key) instanceof IntArrayTag)) throw invalid("Original embedded field lost actual source INT array " + key);
        for (String key : Set.of("CookingTimes", "CookingTotalTimes")) if (source.contains(key) && ((IntArrayTag) source.get(key)).getAsIntArray().length != 4) throw invalid("Original campfire timers lost their four real slots");
        if (source.contains("disabled_slots")) { var disabled = new HashSet<Integer>(); for (int slot : ((IntArrayTag) source.get("disabled_slots")).getAsIntArray()) if (slot < 0 || slot >= 9 || !disabled.add(slot)) throw invalid("Original crafter disabled slots are duplicated/outside actual extent"); }
        if (source.contains("last_interacted_slot") && (integer(source, "last_interacted_slot") < -1 || integer(source, "last_interacted_slot") >= 6)) throw invalid("Original bookshelf interaction lost its real slot");
        if (source.contains("RecipesUsed")) { var recipes = compound(source, "RecipesUsed"); for (String id : recipes.keySet()) { Identifier.parse(id); if (!(recipes.get(id) instanceof IntTag)) throw invalid("Original furnace recipe count lost int"); } }
    }
    private static NeoForgeMigrationScope slotScope(NeoForgeMigrationScope scope, int slot) { return child(child(scope, "items", 0), "slot", slot); }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) { return scope == null ? null : scope.item(role, index); }
    private NeoForgeEmbeddedBlockEntityImport() {}
}
