package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.toolbox.ToolboxInventory;
import com.simibubi.create.content.trains.schedule.NeoForgeTrainScheduleImport;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Typed original component owners. Vanilla DATA_COMPONENTS/ITEM_STACK/ENTITY_TREE DFU each run
 * once at the actual containing boundary. Create's untyped remainder branches are converted here.
 */
public final class NeoForgeItemComponentImport {
    private static final Set<String> SOURCE_VANILLA = Set.of(
        "custom_data", "max_stack_size", "max_damage", "damage", "unbreakable",
        "custom_name", "item_name", "lore", "rarity", "enchantments",
        "can_place_on", "can_break", "attribute_modifiers", "custom_model_data", "hide_additional_tooltip",
        "hide_tooltip", "repair_cost", "creative_slot_lock", "enchantment_glint_override", "intangible_projectile",
        "food", "fire_resistant", "tool", "stored_enchantments", "dyed_color",
        "map_color", "map_id", "map_decorations", "map_post_processing", "charged_projectiles",
        "bundle_contents", "potion_contents", "suspicious_stew_effects", "writable_book_content", "written_book_content",
        "trim", "debug_stick_state", "entity_data", "bucket_entity_data", "block_entity_data",
        "instrument", "ominous_bottle_amplifier", "jukebox_playable", "recipes", "lodestone_tracker",
        "firework_explosion", "fireworks", "profile", "note_block_sound", "banner_patterns",
        "base_color", "pot_decorations", "container", "block_state", "bees",
        "lock", "container_loot");
    private static final Set<String> LEGACY_CLIPBOARD = Set.of("clipboard_type", "clipboard_pages", "clipboard_read_only",
        "clipboard_copied_values", "clipboard_previously_opened_page");

    public static CompoundTag patch(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return finish(source, prepareComponents(source, version, registries, true, scope), version, registries, DataComponentPatch.CODEC, scope);
    }
    public static CompoundTag map(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return finish(source, prepareComponents(source, version, registries, false, scope), version, registries, DataComponentMap.CODEC, scope);
    }
    public static CompoundTag prepareMapForVanillaFixer(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return prepareComponents(source, version, registries, false, scope);
    }
    private static <T> CompoundTag finish(CompoundTag original, CompoundTag prepared, int version, HolderLookup.Provider registries, Codec<T> codec, NeoForgeMigrationScope scope) {
        var fixed = DataFixers.getDataFixer().update(References.DATA_COMPONENTS, new Dynamic<Tag>(NbtOps.INSTANCE, prepared),
            version, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if (!(fixed instanceof CompoundTag nativeComponents)) throw invalid("Actual component DFU did not produce a component compound");
        return canonical(codec, finishComponentsAfterVanillaFixer(original, nativeComponents, version, registries, scope), registries);
    }
    /** Completes only explicitly typed old leaves after their single actual containing vanilla DFU. */
    public static CompoundTag finishItemAfterVanillaFixer(CompoundTag original, CompoundTag actualFixed,
                                                         int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(version); var result = actualFixed.copy();
        if (original.contains("components")) result.put("components", finishComponentsAfterVanillaFixer(
            compound(original, "components"), result.getCompoundOrEmpty("components"), version, registries,
            original.contains("id") ? Identifier.parse(string(original, "id")) : null, scope));
        return result;
    }
    /** Actual BE component maps share their containing BLOCK_ENTITY/ITEM_STACK vanilla DFU. */
    public static CompoundTag finishMapAfterVanillaFixer(CompoundTag original, CompoundTag actualFixed,
                                                       int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(version);
        return canonical(DataComponentMap.CODEC, finishComponentsAfterVanillaFixer(original, actualFixed, version, registries, scope), registries);
    }
    public static CompoundTag finishMapAfterVanillaFixer(CompoundTag original, CompoundTag actualFixed,
                                                       int version, HolderLookup.Provider registries) {
        return finishMapAfterVanillaFixer(original, actualFixed, version, registries, null);
    }
    private static CompoundTag finishComponentsAfterVanillaFixer(CompoundTag original, CompoundTag actualFixed,
                                                                int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return finishComponentsAfterVanillaFixer(original, actualFixed, version, registries, null, scope);
    }
    private static CompoundTag finishComponentsAfterVanillaFixer(CompoundTag original, CompoundTag actualFixed,
                                                                int version, HolderLookup.Provider registries, Identifier actualItem, NeoForgeMigrationScope scope) {
        var result = actualFixed.copy();
        for (String originalKey : original.keySet()) {
            if (originalKey.startsWith("!")) continue;
            var type = Identifier.parse(originalKey);
            if (!type.getNamespace().equals("minecraft")) continue;
            String key = type.toString(); Tag old = original.get(originalKey); var componentScope = child(scope, type.getNamespace() + "_" + type.getPath(), 0);
            switch (type.getPath()) {
                case "custom_name", "item_name" -> result.put(key, originalText(old, version, registries, componentScope));
                case "lore" -> {
                    var lines = new ListTag(); for (Tag line : asList(old, "original lore")) lines.add(originalText(line, version, registries, child(componentScope, "line", lines.size()))); result.put(key, lines);
                }
                case "written_book_content" -> {
                    var source = asCompound(old, "original written book"); var book = compound(result, key).copy(); var pages = new ListTag();
                    if (!source.contains("pages")) break;
                    for (Tag value : list(source, "pages")) {
                        if (value instanceof net.minecraft.nbt.StringTag) {
                            var page = new CompoundTag(); page.put("raw", originalText(value, version, registries, child(child(componentScope, "page", pages.size()), "raw", 0))); pages.add(page); continue;
                        }
                        var row = asCompound(value, "original filtered book page"); keys(row, "filtered book page", "raw", "filtered"); var page = new CompoundTag();
                        page.put("raw", originalText(row.get("raw"), version, registries, child(child(componentScope, "page", pages.size()), "raw", 0)));
                        if (row.contains("filtered")) page.put("filtered", originalText(row.get("filtered"), version, registries, child(child(componentScope, "page", pages.size()), "filtered", 0))); pages.add(page);
                    }
                    book.put("pages", pages); result.put(key, book);
                }
                case "container" -> result.put(key, finishSparseItems(asList(old, "original container"),
                    list(result, key), "slot", "item", version, registries, componentScope));
                case "bundle_contents", "charged_projectiles" -> {
                    var source = asList(old, "original nested items"); var fixed = list(result, key);
                    if (source.size() != fixed.size()) throw invalid("Actual DFU changed the original nested item owner count");
                    var items = new ListTag(); for (int i = 0; i < source.size(); i++) items.add(finishItemAfterVanillaFixer(
                        asCompound(source.get(i), "original nested item"), asCompound(fixed.get(i), "actual fixed nested item"), version, registries, child(componentScope, "entry", i))); result.put(key, items);
                }
                case "food" -> {
                    var food = asCompound(old, "original food");
                    if (food.contains("using_converts_to")) result.put("minecraft:use_remainder", finishItemAfterVanillaFixer(
                        compound(food, "using_converts_to"), compound(result, "minecraft:use_remainder"), version, registries, child(componentScope, "using_converts_to", 0)));
                }
                case "entity_data" -> {
                    var entity = asCompound(old, "original item entity").copy();
                    if (!entity.isEmpty()) {
                        if (!entity.contains("id")) {
                            var item = actualItem == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(actualItem);
                            if (!(item instanceof net.minecraft.world.item.SpawnEggItem)) throw invalid("Original entity component has no actual containing egg type");
                            var actualType = net.minecraft.world.item.SpawnEggItem.getType(new net.minecraft.world.item.ItemStack(item));
                            if (actualType == null) throw invalid("Original egg has no registered entity type");
                            entity.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(actualType).toString());
                        }
                        result.put(key, NeoForgeVanillaEntityImport.finishAfterVanillaFixer(entity, compound(result, key), version, registries, componentScope));
                    }
                }
                case "bees" -> {
                    var source = asList(old, "original bee component"); var actual = list(result, key);
                    var inert = NeoForgeInertHiveSlots.fromOriginal(source);
                    if (source.size() - inert.size() != actual.size()) throw invalid("Actual DFU changed genuine original bee occupant count");
                    var carried = result.contains(NeoForgeInertHiveSlots.COMPONENT_KEY) ? NeoForgeInertHiveSlots.read(compound(result, NeoForgeInertHiveSlots.COMPONENT_KEY)) : NeoForgeInertHiveSlots.EMPTY;
                    if (!inert.equals(carried)) throw invalid("Actual DFU changed original inert bee component occupancy/timers/provenance");
                    var bees = new ListTag(); int nativeIndex = 0;
                    for (int i = 0; i < source.size(); i++) {
                        var originalBee = asCompound(source.get(i), "original bee occupant");
                        if (NeoForgeInertHiveSlots.isInertOriginal(originalBee)) continue;
                        var fixedBee = asCompound(actual.get(nativeIndex++), "actual bee occupant").copy();
                        for (String timer : Set.of("ticks_in_hive", "min_ticks_in_hive")) if (integer(originalBee, timer) != integer(fixedBee, timer)) throw invalid("Actual DFU changed original bee component residence time");
                        if (originalBee.contains("entity_data") && !compound(originalBee, "entity_data").isEmpty()) fixedBee.put("entity_data",
                            NeoForgeVanillaEntityImport.finishAfterVanillaFixer(compound(originalBee, "entity_data"), compound(fixedBee, "entity_data"), version, registries, child(componentScope, "bee", i)));
                        bees.add(fixedBee);
                    }
                    result.put(key, bees);
                }
                case "block_entity_data" -> {
                    var source = asCompound(old, "original item block entity");
                    if (!source.isEmpty()) {
                        var fixed = compound(result, key).copy();
                        var actualType = Identifier.parse(source.contains("id") ? string(source, "id") : string(fixed, "id"));
                        if (NeoForgeVanillaBlockEntityImport.supports(actualType)) {
                            result.put(key, NeoForgeVanillaBlockEntityImport.finishAfterVanillaFixer(actualType, source, fixed, version, registries, componentScope));
                        } else if (actualType.getNamespace().equals("minecraft") && source.contains("Items")) {
                            fixed.put("Items", finishSparseItems(list(source, "Items"), list(fixed, "Items"), "Slot", null, version, registries, componentScope)); result.put(key, fixed);
                        }
                    }
                }
                default -> {} // Other source leaves remain owned by the actual containing vanilla DFU.
            }
        }
        return result;
    }
    private static Tag originalText(Tag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!(source instanceof net.minecraft.nbt.StringTag flattened)) throw invalid("Original vanilla text is not its genuine flattened string");
        return NeoForgeTextComponentImport.flattened(flattened, version, registries, scope);
    }
    private static ListTag finishSparseItems(ListTag original, ListTag actualFixed, String slotKey, String itemKey,
                                            int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var originals = new java.util.HashMap<Integer, CompoundTag>();
        for (Tag value : original) {
            var row = asCompound(value, "original sparse text owner"); int slot = originalSlot(row, slotKey);
            if (originals.put(slot, row) != null) throw invalid("Duplicate original sparse text owner");
        }
        var result = new ListTag(); var seen = new HashSet<Integer>();
        for (Tag value : actualFixed) {
            var row = asCompound(value, "actual fixed sparse text owner"); int slot = originalSlot(row, slotKey);
            var old = originals.get(slot);
            if (old == null || !seen.add(slot)) throw invalid("Actual DFU changed the original sparse item owner");
            var next = row.copy();
            if (itemKey != null) next.put(itemKey, finishItemAfterVanillaFixer(compound(old, itemKey), compound(row, itemKey), version, registries, child(scope, "slot", slot)));
            else {
                var sourceItem = old.copy(); sourceItem.remove(slotKey); var nativeItem = row.copy(); nativeItem.remove(slotKey);
                next = finishItemAfterVanillaFixer(sourceItem, nativeItem, version, registries, child(scope, "slot", slot)); next.put(slotKey, row.get(slotKey).copy());
            }
            result.add(next);
        }
        if (seen.size() != originals.size()) throw invalid("Actual DFU lost an original sparse item owner");
        return result;
    }
    private static int originalSlot(CompoundTag row, String key) {
        if (key.equals("Slot") && row.get(key) instanceof ByteTag slot) return Byte.toUnsignedInt(slot.byteValue());
        return integer(row, key);
    }
    /** Source3955 vanilla fields remain source3955. Only untyped Create component branches become native. */
    public static CompoundTag prepareItemForVanillaFixer(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(version); keys(source, "item stack", "id", "count", "components");
        if (source.isEmpty()) return source.copy();
        string(source, "id");
        if (source.contains("count") && (integer(source, "count") < 1 || integer(source, "count") > 99))
            throw invalid("Original item count outside the actual source codec range1..99");
        var next = source.copy();
        if (source.contains("components")) next.put("components", prepareComponents(compound(source, "components"), version, registries, true, Identifier.parse(string(source, "id")), scope));
        return next;
    }
    private static CompoundTag prepareComponents(CompoundTag source, int version, HolderLookup.Provider registries, boolean patch, NeoForgeMigrationScope scope) {
        return prepareComponents(source, version, registries, patch, null, scope);
    }
    private static CompoundTag prepareComponents(CompoundTag source, int version, HolderLookup.Provider registries, boolean patch, Identifier actualItem, NeoForgeMigrationScope scope) {
        version(version); var result = new CompoundTag(); var seen = new HashSet<Identifier>();
        boolean oldClipboard = false;
        for (String original : source.keySet()) {
            boolean removed = original.startsWith("!"); String spelling = removed ? original.substring(1) : original;
            var id = Identifier.parse(spelling); var componentScope = child(scope, id.getNamespace() + "_" + id.getPath(), 0);
            if (!seen.add(id)) throw invalid("Duplicate original component identity/removal " + id);
            if (id.getNamespace().equals("create") && id.getPath().equals("inert_hive_slots")) throw invalid("Inert hive carrier did not exist in genuine original3955 components");
            if (id.getNamespace().equals("minecraft") && !SOURCE_VANILLA.contains(id.getPath())) throw invalid("Component did not exist in original3955: " + id);
            if (removed && !patch) throw invalid("A source component map cannot contain removals");
            if (removed && (!(source.get(original) instanceof CompoundTag unit) || !unit.isEmpty()))
                throw invalid("Malformed original removed-component marker");
            if (id.getNamespace().equals("create") && LEGACY_CLIPBOARD.contains(id.getPath())) { oldClipboard |= !removed; continue; }
            String key = (removed ? "!" : "") + id;
            if (removed) { result.put(key, source.get(original).copy()); continue; }
            Tag value = source.get(original);
            if (id.getNamespace().equals("create")) value = create(id.getPath(), value, source, version, registries, componentScope);
            else if (id.getNamespace().equals("minecraft")) value = vanilla(id.getPath(), value, version, registries, actualItem, componentScope);
            result.put(key, value.copy());
        }
        if (oldClipboard && !source.contains("create:clipboard_content") && !source.contains("!create:clipboard_content"))
            result.put("create:clipboard_content", legacyClipboard(source, version, registries, scope));
        for (String original : source.keySet()) if (!original.startsWith("!") && Identifier.parse(original).equals(Identifier.parse("minecraft:bees"))) {
            var inert = NeoForgeInertHiveSlots.fromOriginal(asList(source.get(original), "original bee component"));
            if (!inert.isEmpty()) result.put(NeoForgeInertHiveSlots.COMPONENT_KEY, inert.write());
        }
        return result;
    }
    private static Tag create(String type, Tag value, CompoundTag actualComponents, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return switch (type) {
            case "filter_items", "package_contents", "linked_controller_items" -> container(asList(value, type), version, registries, scope);
            case "sand_paper_polishing" -> {
                var data = asCompound(value, type); keys(data, "sandpaper polishing component", "item");
                var next = new CompoundTag(); next.put("item", item(compound(data, "item"), version, registries, child(scope, "item", 0))); yield next;
            }
            case "toolbox_inventory" -> toolbox(value, version, registries, scope);
            case "package_order_context" -> orderWithCrafts(asCompound(value, type), version, registries, scope);
            case "package_order_data" -> {
                var data = asCompound(value, type); keys(data, type, "order_id", "link_index", "is_final_link", "fragment_index", "is_final", "order_context");
                integer(data, "order_id"); integer(data, "link_index"); integer(data, "fragment_index"); bool(data, "is_final_link"); bool(data, "is_final");
                var next = data.copy();
                if (data.contains("order_context")) next.put("order_context", orderWithCrafts(compound(data, "order_context"), version, registries, child(scope, "order_context", 0)));
                yield next;
            }
            case "auto_request_data" -> {
                var data = asCompound(value, type); keys(data, type, "encoded_request", "encoded_target_address", "target_offset", "target_dim", "is_valid");
                var next = data.copy(); next.put("encoded_request", orderWithCrafts(compound(data, "encoded_request"), version, registries, child(scope, "encoded_request", 0))); yield next;
            }
            case "clipboard_content" -> clipboard(asCompound(value, type), version, registries, scope);
            case "train_schedule" -> NeoForgeTrainScheduleImport.schedule(asCompound(value, type), version, registries, scope);
            case "minecart_contraption_data" -> com.simibubi.create.content.contraptions.NeoForgeContraptionEntityImport.packed(asCompound(value, type), version, registries, scope);
            case "shaper_block_data" -> {
                var ops = registries.createSerializationContext(NbtOps.INSTANCE);
                if (!actualComponents.contains("create:shaper_block_used")) throw invalid("Original Worldshaper block data has no actual selected state");
                var state = net.minecraft.world.level.block.state.BlockState.CODEC.parse(ops, actualComponents.get("create:shaper_block_used")).getOrThrow();
                yield NeoForgeBlockEntityImport.convertSafe(NeoForgeBlockEntityImport.originalTypeFor(state), asCompound(value, type), version, registries, scope);
            }
            // These pinned components contain no ItemStack/entity/block-entity subtree. Their actual
            // registered native codec is validated by the enclosing map/patch or ITEM_STACK boundary.
            case "banktank_air", "belt_first_shaft", "inferred_from_recipe", "placement_pattern", "shaper_brush", "shaper_brush_params",
                 "shaper_placement_options", "shaper_tool", "shaper_block_used", "shaper_swap", "filter_items_respect_nbt", "filter_items_blacklist",
                 "attribute_filter_whitelist_mode", "attribute_filter_matched_attributes", "track_connecting_from", "track_extend_curve",
                 "track_targeting_item_selected_pos", "track_targeting_item_selected_direction", "track_targeting_item_bezier", "schematic_deployed",
                 "schematic_owner", "schematic_file", "schematic_anchor", "schematic_rotation", "schematic_mirror", "schematic_bounds", "schematic_hash",
                 "chromatic_compound_collecting_light", "sand_paper_jei", "toolbox_uuid", "sequenced_assembly", "symmetry_wand", "symmetry_wand_enable",
                 "symmetry_wand_simulate", "potion_fluid_bottle_type", "schematicannon_options", "shopping_list", "shopping_list_address", "package_address",
                 "click_to_link_data" -> value.copy();
            default -> throw invalid("Unreviewed original Create component " + type);
        };
    }
    private static Tag vanilla(String type, Tag value, int version, HolderLookup.Provider registries, Identifier actualItem, NeoForgeMigrationScope scope) {
        return switch (type) {
            case "container" -> prepareContainer(asList(value, type), version, registries, scope);
            case "bundle_contents", "charged_projectiles" -> {
                var next = new ListTag();
                for (Tag item : asList(value, type)) next.add(prepareItemForVanillaFixer(asCompound(item, type), version, registries, child(scope, "entry", next.size())));
                yield next;
            }
            case "food" -> {
                var next = asCompound(value, type).copy();
                if (next.contains("using_converts_to")) next.put("using_converts_to", prepareItemForVanillaFixer(compound(next, "using_converts_to"), version, registries, child(scope, "using_converts_to", 0)));
                yield next;
            }
            case "entity_data" -> {
                var entity = asCompound(value, type).copy();
                if (entity.isEmpty()) yield entity;
                if (!entity.contains("id")) {
                    var item = actualItem == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(actualItem);
                    if (!(item instanceof net.minecraft.world.item.SpawnEggItem)) throw invalid("Original entity component has no actual containing entity type");
                    // A real registered egg determines its real default type. This temporary type
                    // probe carries no original components, UUID or material ownership.
                    var actualType = net.minecraft.world.item.SpawnEggItem.getType(new net.minecraft.world.item.ItemStack(item));
                    if (actualType == null) throw invalid("Original egg has no native default entity type");
                    entity.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(actualType).toString());
                }
                yield NeoForgeEntityItemImport.prepare(entity, version, registries, scope);
            }
            case "bees" -> {
                var next = new ListTag(); var original = asList(value, type);
                for (int index = 0; index < original.size(); index++) {
                    var row = asCompound(original.get(index), "bee component").copy();
                    if (NeoForgeInertHiveSlots.isInertOriginal(row)) continue;
                    row.put("entity_data", NeoForgeEntityItemImport.prepare(compound(row, "entity_data"), version, registries, child(scope, "bee", index)));
                    next.add(row);
                }
                yield next;
            }
            case "block_entity_data" -> {
                var data = asCompound(value, type).copy(); if (data.isEmpty()) yield data;
                Identifier actualType = data.contains("id") ? Identifier.parse(string(data, "id")) : null;
                if (actualItem != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(actualItem) instanceof net.minecraft.world.item.BlockItem blockItem) {
                    var factoryType = NeoForgeBlockEntityImport.originalTypeFor(blockItem.getBlock().defaultBlockState());
                    if (actualType != null && !actualType.equals(factoryType)) throw invalid("Original block component id disagrees with its actual block item factory");
                    actualType = factoryType;
                }
                if (actualType == null) throw invalid("Original block component has no actual block entity type");
                data.putString("id", actualType.toString());
                yield NeoForgeBlockEntityImport.prepareForVanillaFixer(actualType, data, version, registries, scope);
            }
            default -> value.copy(); // Actual vanilla DATA_COMPONENTS schema owns all other vanilla leaves.
        };
    }
    public static ListTag container(ListTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var result = containerRows(source, version, registries, false, scope);
        return (ListTag) ItemContainerContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
            ItemContainerContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), result).getOrThrow()).getOrThrow();
    }
    private static ListTag prepareContainer(ListTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return containerRows(source, version, registries, true, scope);
    }
    private static ListTag containerRows(ListTag source, int version, HolderLookup.Provider registries, boolean prepare, NeoForgeMigrationScope scope) {
        version(version); if (source.size() > 256) throw invalid("Original component container exceeds actual source256slots");
        var seen = new HashSet<Integer>(); var result = new ListTag();
        for (Tag entry : source) {
            var row = asCompound(entry, "component container slot"); keys(row, "component container slot", "slot", "item");
            int slot = integer(row, "slot");
            if (slot < 0 || slot > 255 || !seen.add(slot)) throw invalid("Duplicate/out-of-range original component slot");
            var oldItem = compound(row, "item");
            if (oldItem.isEmpty()) throw invalid("Original sparse component slot cannot be empty");
            var next = row.copy(); next.put("item", prepare ? prepareItemForVanillaFixer(oldItem, version, registries, child(scope, "slot", slot)) : item(oldItem, version, registries, child(scope, "slot", slot))); result.add(next);
        }
        return result;
    }
    private static Tag toolbox(Tag value, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (value instanceof ListTag legacy) return container(legacy, version, registries, scope);
        var data = asCompound(value, "toolbox component"); keys(data, "toolbox component", "items", "filters");
        var oldSlots = compound(data, "items"); keys(oldSlots, "toolbox sparse storage", "items", "size");
        int size = integer(oldSlots, "size"); if (size < 0 || size > 32) throw invalid("Original toolbox size exceeds32slots");
        var stacks = new CompoundTag();
        for (String key : compound(oldSlots, "items").keySet()) {
            int index; try { index = Integer.parseInt(key); } catch (NumberFormatException malformed) { throw invalid("Noncanonical original toolbox slot"); }
            if (index < 0 || index >= size || !key.equals(Integer.toString(index))) throw invalid("Out-of-range original toolbox slot");
            var stack = item(compound(compound(oldSlots, "items"), key), version, registries, child(scope, "stock", index));
            if (stack.isEmpty()) throw invalid("Empty original sparse toolbox item"); stacks.put(key, stack);
        }
        var filters = list(data, "filters"); if (filters.size() != 8) throw invalid("Original toolbox must retain eight filters");
        var nextFilters = new ListTag();
        for (Tag filter : filters) {
            var stack = item(asCompound(filter, "toolbox filter"), version, registries, child(scope, "filter", nextFilters.size()));
            if (!stack.isEmpty() && stack.getIntOr("count", 1) != 1) throw invalid("Native toolbox would truncate the original filter count");
            nextFilters.add(stack);
        }
        var slots = new CompoundTag(); slots.put("items", stacks); slots.putInt("size", size);
        var next = new CompoundTag(); next.put("items", slots); next.put("filters", nextFilters);
        return canonical(ToolboxInventory.CODEC, next, registries);
    }
    private static CompoundTag orderWithCrafts(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var next = source.copy();
        if (source.contains("entries")) { keys(source, "legacy package order", "entries"); next = order(source, version, registries, scope); }
        else {
            keys(source, "package order with crafts", "ordered_stacks", "ordered_crafts");
            next.put("ordered_stacks", order(compound(source, "ordered_stacks"), version, registries, child(scope, "ordered_stacks", 0))); var crafts = new ListTag();
            for (Tag value : list(source, "ordered_crafts")) {
                var row = asCompound(value, "ordered craft"); keys(row, "ordered craft", "pattern", "count");
                if (integer(row, "count") < 0) throw invalid("Negative original ordered craft count");
                var craft = row.copy(); craft.put("pattern", order(compound(row, "pattern"), version, registries, child(child(scope, "ordered_craft", crafts.size()), "pattern", 0))); crafts.add(craft);
            }
            next.put("ordered_crafts", crafts);
        }
        return canonical(com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CODEC, next, registries);
    }
    private static CompoundTag order(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "package order", "entries"); var next = new CompoundTag(); var entries = new ListTag();
        for (Tag value : list(source, "entries")) {
            var row = asCompound(value, "ordered stack"); keys(row, "ordered stack", "item_stack", "count");
            if (integer(row, "count") < 0) throw invalid("Negative original order quantity");
            var entry = row.copy(); entry.put("item_stack", item(compound(row, "item_stack"), version, registries, child(child(scope, "entry", entries.size()), "item_stack", 0))); entries.add(entry);
        }
        next.put("entries", entries); return next;
    }
    private static CompoundTag clipboard(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "clipboard content", "type", "pages", "read_only", "previously_opened_page", "copied_values");
        var next = source.copy(); next.put("pages", pages(list(source, "pages"), version, registries, child(scope, "pages", 0)));
        if (source.contains("copied_values")) next.put("copied_values", copiedValues(compound(source, "copied_values"), version, registries, child(scope, "copied_values", 0)));
        return canonical(ClipboardContent.CODEC, next, registries);
    }
    private static ListTag pages(ListTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var pages = new ListTag();
        for (Tag page : source) {
            var entries = new ListTag();
            for (Tag value : asList(page, "clipboard page")) {
                var entryScope = child(child(scope, "page", pages.size()), "entry", entries.size()); var row = asCompound(value, "clipboard entry"); keys(row, "clipboard entry", "checked", "text", "icon", "item_amount");
                bool(row, "checked"); integer(row, "item_amount"); var next = row.copy();
                next.put("icon", item(compound(row, "icon"), version, registries, child(entryScope, "icon", 0)));
                next.put("text", NeoForgeTextComponentImport.structured(row.get("text"), version, registries, child(entryScope, "text", 0))); entries.add(next);
            }
            pages.add(entries);
        }
        return pages;
    }
    private static CompoundTag copiedValues(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var next = source.copy();
        if (source.contains("Filtering")) {
            var row = compound(source, "Filtering").copy();
            if (row.contains("Filter")) row.put("Filter", item(compound(row, "Filter"), version, registries, child(scope, "filter", 0))); next.put("Filtering", row);
        }
        if (source.contains("Frequencies")) {
            var row = compound(source, "Frequencies").copy();
            for (String key : Set.of("First", "Last")) if (row.contains(key)) row.put(key, item(compound(row, key), version, registries, child(scope, "frequency_" + key.toLowerCase(java.util.Locale.ROOT), 0))); next.put("Frequencies", row);
        }
        return next;
    }
    private static CompoundTag legacyClipboard(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var next = new CompoundTag(); next.putString("type", source.contains("create:clipboard_type") ? string(source, "create:clipboard_type") : "empty");
        next.put("pages", source.contains("create:clipboard_pages") ? pages(asList(source.get("create:clipboard_pages"), "legacy clipboard pages"), version, registries, child(scope, "create_clipboard_pages", 0)) : new ListTag());
        if (source.contains("create:clipboard_read_only") && !compound(source, "create:clipboard_read_only").isEmpty()) throw invalid("Malformed original clipboard read-only unit");
        next.putBoolean("read_only", source.contains("create:clipboard_read_only"));
        next.putInt("previously_opened_page", source.contains("create:clipboard_previously_opened_page") ? integer(source, "create:clipboard_previously_opened_page") : 0);
        if (source.contains("create:clipboard_copied_values")) next.put("copied_values", copiedValues(compound(source, "create:clipboard_copied_values"), version, registries, child(scope, "create_clipboard_copied_values", 0)));
        return canonical(ClipboardContent.CODEC, next, registries);
    }
    private static void version(int version) { if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original component version"); }
    private static CompoundTag asCompound(Tag value, String context) { if (!(value instanceof CompoundTag tag)) throw invalid("Malformed original " + context); return tag; }
    private static ListTag asList(Tag value, String context) { if (!(value instanceof ListTag tag)) throw invalid("Malformed original " + context); return tag; }
    private static <T> CompoundTag canonical(Codec<T> codec, Tag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE); return (CompoundTag) codec.encodeStart(ops, codec.parse(ops, tag).getOrThrow()).getOrThrow();
    }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) {
        return scope == null ? null : scope.item(role, index);
    }
    private NeoForgeItemComponentImport() {}
    /** Binary-compatible unscoped entrypoints; packed values require explicit real owner scope. */
    public static CompoundTag patch(CompoundTag source, int version, HolderLookup.Provider registries) {
        return patch(source, version, registries, null);
    }

    public static CompoundTag map(CompoundTag source, int version, HolderLookup.Provider registries) {
        return map(source, version, registries, null);
    }

    public static CompoundTag prepareMapForVanillaFixer(CompoundTag source, int version, HolderLookup.Provider registries) {
        return prepareMapForVanillaFixer(source, version, registries, null);
    }

    public static CompoundTag finishItemAfterVanillaFixer(CompoundTag original, CompoundTag actualFixed,
                                                         int version, HolderLookup.Provider registries) {
        return finishItemAfterVanillaFixer(original, actualFixed, version, registries, null);
    }

    public static CompoundTag prepareItemForVanillaFixer(CompoundTag source, int version, HolderLookup.Provider registries) {
        return prepareItemForVanillaFixer(source, version, registries, null);
    }

    public static ListTag container(ListTag source, int version, HolderLookup.Provider registries) {
        return container(source, version, registries, null);
    }

}
