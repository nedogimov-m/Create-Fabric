package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Dynamic;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.SequenceContext;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/** Explicit pinned original block-entity schemas; never treats arbitrary id/count compounds as stock. */
public final class NeoForgeBlockEntityImport {
    // Exact BE identities of the pinned generated create:safe_nbt block tag. Repeated block
    // variants (sticky pistons, three chassis and four funnels) share their actual source type.
    private static final Set<String> FULL_SAFE_TYPES = Set.of("motor", "creative_fluid_tank", "mechanical_piston",
        "windmill_bearing", "mechanical_bearing", "clockwork_bearing", "rope_pulley", "cart_assembler", "chassis",
        "sequenced_gearshift", "rotation_speed_controller", "funnel", "redstone_link", "analog_lever", "placard",
        "pulse_repeater", "pulse_extender", "pulse_timer", "clipboard");
    public static CompoundTag convert(Identifier originalType, CompoundTag source, int version,
                                      HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original block entity version");
        if (source.contains("id") && !Identifier.parse(string(source, "id")).equals(originalType))
            throw invalid("Original block entity id disagrees with its containing typed owner");
        if (originalType.getNamespace().equals("minecraft")) return vanilla(originalType, source, version, registries, scope);
        if (!originalType.getNamespace().equals("create")) throw invalid("Original addon block entity is not supported: " + originalType);
        CompoundTag result;
        var logistics = com.simibubi.create.content.logistics.NeoForgeLogisticsBlockEntityImport.convert(originalType, source, version, registries, scope);
        if (logistics.isPresent()) result = logistics.get();
        else {
            result = source.copy();
            switch (originalType.getPath()) {
                case "basin" -> basin(source, result, version, registries, scope);
                case "millstone" -> millstone(source, result, version, registries, scope);
                case "saw" -> saw(source, result, version, registries, scope);
                case "bogey" -> bogey(source);
                default -> {
                    var other = NeoForgeRemainingBlockEntityImport.convert(originalType, source, version, registries, scope);
                    if (other.isEmpty()) other = NeoForgeMachineBlockEntityImport.convert(originalType, source, version, registries, scope);
                    if (other.isEmpty()) other = NeoForgeFluidMachineBlockEntityImport.convert(originalType, source, version, registries, scope);
                    if (other.isEmpty()) other = NeoForgeSimpleMachineBlockEntityImport.convert(originalType, source, version, registries, scope);
                    if (other.isEmpty()) other = NeoForgeControlMachineBlockEntityImport.convert(originalType, source, version, registries, scope);
        if (other.isEmpty()) other = NeoForgeComplexMachineBlockEntityImport.convert(originalType, source, version, registries, scope);
        if (other.isEmpty()) other = NeoForgeRailDisplayBlockEntityImport.convert(originalType, source, version, registries, scope);
                    if (other.isEmpty()) throw invalid("Original Create block entity has no complete reviewed schema yet: " + originalType);
                    result = other.get();
                }
            }
        }
        common(originalType, source, result, version, registries, scope);
        if (source.contains("id")) {
            String nativeType = switch (originalType.toString()) {
                case "create:cursed_bell" -> "create:haunted_bell";
                case "create:track_station" -> "create:station";
                default -> originalType.toString();
            };
            result.putString("id", nativeType);
        }
        return result;
    }
    public static CompoundTag convert(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries) {
        return convert(type, source, version, registries, null);
    }
    public static CompoundTag convert(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return convert(Identifier.parse(string(source, "id")), source, version, registries, scope);
    }
    public static CompoundTag convert(CompoundTag source, int version, HolderLookup.Provider registries) {
        return convert(source, version, registries, null);
    }
    /** Pinned schematic/Worldshaper payload, whose id/coordinates were deliberately stripped. */
    public static CompoundTag convertSafe(Identifier originalType, CompoundTag source, int version,
                                         HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original safe BE version");
        if (originalType.getNamespace().equals("minecraft")) return vanilla(originalType, source, version, registries, scope);
        if (!originalType.getNamespace().equals("create")) throw invalid("Unreviewed original addon safe NBT writer");
        if (FULL_SAFE_TYPES.contains(originalType.getPath())) return convert(originalType, source, version, registries, scope);
        var owned = NeoForgeRemainingBlockEntityImport.convertSafe(originalType, source, version, registries, scope);
        if (owned.isEmpty()) owned = NeoForgeMachineBlockEntityImport.convertSafe(originalType, source, version, registries, scope);
        if (owned.isEmpty()) owned = NeoForgeSimpleMachineBlockEntityImport.convertSafe(originalType, source, version, registries, scope);
        if (owned.isEmpty()) owned = NeoForgeControlMachineBlockEntityImport.convertSafe(originalType, source, version, registries, scope);
        if (owned.isEmpty()) owned = NeoForgeComplexMachineBlockEntityImport.convertSafe(originalType, source, version, registries, scope);
        if (owned.isEmpty()) owned = NeoForgeRailDisplayBlockEntityImport.convertSafe(originalType, source, version, registries, scope);
        CompoundTag result;
        if (owned.isPresent()) result = owned.get();
        else {
            // Smart.writeSafe exports only safe behaviours. No inventory or operation fields may
            // be inferred from a partial tag; additional domain writers require explicit branches.
            keys(source, "partial safe Smart BE", "components", "Filter", "FilterAmount", "UpTo", "Filters",
                "FrequencyFirst", "FrequencyLast", "LastKnownPosition", "ScrollValue", "ScrollOption", "Freq", "Bracket");
            result = source.copy();
        }
        common(originalType, source, result, version, registries, scope); return result;
    }
    /** Registry type inspection only. The temporary factory position is never a source coordinate. */
    public static Identifier originalTypeFor(net.minecraft.world.level.block.state.BlockState actualState) {
        if (!(actualState.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory))
            throw invalid("Actual original state has no block entity factory");
        var temporary = factory.newBlockEntity(BlockPos.ZERO, actualState);
        if (temporary == null) throw invalid("Actual original state factory returned no block entity");
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(temporary.getType());
        if (type == null) throw invalid("Actual state factory has no registered native block entity identity");
        if (type.equals(Identifier.parse("create:station"))) return Identifier.parse("create:track_station");
        if (type.equals(Identifier.parse("create:haunted_bell"))) return Identifier.parse("create:cursed_bell");
        return type;
    }
    /** Prepare unknown Create branches, retaining genuine vanilla3955 fields for one enclosing DFU. */
    public static CompoundTag prepareForVanillaFixer(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original block entity version");
        if (source.contains("id") && !type.equals(Identifier.parse(string(source, "id")))) throw invalid("Contradictory original block entity identity");
        if (type.getNamespace().equals("create")) return convert(type, source, version, registries, scope);
        if (!type.getNamespace().equals("minecraft") || !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(type))
            throw invalid("Unknown original vanilla BE schema " + type);
        if (NeoForgeVanillaBlockEntityImport.supports(type))
            return NeoForgeVanillaBlockEntityImport.prepareForVanillaFixer(type, source, version, registries, scope);
        var result = source.copy();
        int size = switch (type.getPath()) {
            case "chest", "trapped_chest", "barrel", "shulker_box" -> 27;
            case "dispenser", "dropper", "crafter" -> 9;
            case "hopper", "brewing_stand" -> 5;
            case "furnace", "smoker", "blast_furnace" -> 3;
            case "campfire" -> 4;
            case "chiseled_bookshelf" -> 6;
            default -> -1;
        };
        if (size >= 0 && source.contains("Items")) {
            var entries = new ListTag(); var seen = new HashSet<Integer>();
            for (Tag value : list(source, "Items")) {
                if (!(value instanceof CompoundTag row) || !(row.get("Slot") instanceof ByteTag index)) throw invalid("Malformed original vanilla inventory row");
                int slot = Byte.toUnsignedInt(index.byteValue());
                if (slot >= size || !seen.add(slot)) throw invalid("Duplicate/out-of-range original vanilla inventory row");
                keys(row, "vanilla inventory item", "Slot", "id", "count", "components");
                var stack = row.copy(); stack.remove("Slot");
                stack = NeoForgeItemComponentImport.prepareItemForVanillaFixer(stack, version, registries, child(child(scope, "items", 0), "slot", slot));
                if (stack.isEmpty()) throw invalid("Original sparse vanilla row cannot be empty");
                stack.putByte("Slot", index.byteValue()); entries.add(stack);
            }
            result.put("Items", entries);
        }
        String itemField = switch (type.getPath()) { case "jukebox" -> "RecordItem"; case "lectern" -> "Book";
            case "brushable_block", "decorated_pot" -> "item"; default -> null; };
        if (itemField != null && source.contains(itemField)) result.put(itemField,
            NeoForgeItemComponentImport.prepareItemForVanillaFixer(compound(source, itemField), version, registries, child(scope, itemField.toLowerCase(Locale.ROOT), 0)));
        if (type.getPath().equals("beehive") && source.contains("bees")) {
            var bees = new ListTag();
            for (Tag value : list(source, "bees")) {
                if (!(value instanceof CompoundTag row)) throw invalid("Malformed original beehive occupant");
                keys(row, "beehive occupant", "entity_data", "ticks_in_hive", "min_ticks_in_hive"); var next = row.copy();
                if (row.contains("entity_data") && !compound(row, "entity_data").isEmpty()) next.put("entity_data", NeoForgeEntityItemImport.prepare(compound(row, "entity_data"), version, registries, child(child(scope, "bee", bees.size()), "entity_data", 0)));
                bees.add(next);
            }
            result.put("bees", bees);
        }
        if (type.getPath().equals("mob_spawner")) {
            if (source.contains("SpawnData")) result.put("SpawnData", spawnData(compound(source, "SpawnData"), version, registries, child(scope, "spawn_data", 0)));
            if (source.contains("SpawnPotentials")) {
                var potentials = new ListTag();
                for (Tag value : list(source, "SpawnPotentials")) {
                    if (!(value instanceof CompoundTag row)) throw invalid("Malformed original spawner potential");
                    keys(row, "spawner potential", "data", "weight"); var next = row.copy();
                    next.put("data", spawnData(compound(row, "data"), version, registries, child(scope, "spawn_potential", potentials.size()))); potentials.add(next);
                }
                result.put("SpawnPotentials", potentials);
            }
        }
        if (source.contains("components")) result.put("components", NeoForgeItemComponentImport.map(compound(source, "components"), version, registries, child(scope, "components", 0)));
        return result;
    }
    private static CompoundTag spawnData(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "spawn data", "entity", "custom_spawn_rules", "equipment"); var next = source.copy();
        if (source.contains("entity") && !compound(source, "entity").isEmpty()) next.put("entity", NeoForgeEntityItemImport.prepare(compound(source, "entity"), version, registries, child(scope, "entity", 0)));
        return next;
    }
    private static CompoundTag vanilla(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(type)) throw invalid("Unknown original vanilla block entity " + type);
        if (NeoForgeVanillaBlockEntityImport.supports(type))
            return NeoForgeVanillaBlockEntityImport.convert(type, source, version, registries, scope);
        var input = source.copy(); input.putString("id", type.toString());
        Tag updated = DataFixers.getDataFixer().update(References.BLOCK_ENTITY,
            new Dynamic<Tag>(NbtOps.INSTANCE, input), version, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if (!(updated instanceof CompoundTag result)) throw invalid("Vanilla block entity fixer did not return a compound");
        int slots = switch (type.getPath()) {
            case "chest", "trapped_chest", "barrel", "shulker_box" -> 27;
            case "dispenser", "dropper", "crafter" -> 9;
            case "hopper", "brewing_stand" -> 5;
            case "furnace", "smoker", "blast_furnace" -> 3;
            case "campfire" -> 4;
            case "chiseled_bookshelf" -> 6;
            default -> -1;
        };
        if (slots >= 0 && source.contains("Items")) {
            var contents = new ListTag(); var occupied = new HashSet<Integer>();
            for (Tag value : list(source, "Items")) {
                if (!(value instanceof CompoundTag entry) || !(entry.get("Slot") instanceof ByteTag ordinal))
                    throw invalid("Malformed original vanilla container slot");
                keys(entry, "vanilla container item", "Slot", "id", "count", "components");
                int index = Byte.toUnsignedInt(ordinal.byteValue());
                if (index >= slots || !occupied.add(index)) throw invalid("Duplicate/out-of-range original vanilla slot");
                var stack = entry.copy(); stack.remove("Slot"); var next = item(stack, version, registries, child(child(scope, "items", 0), "slot", index));
                if (next.isEmpty()) throw invalid("Physical original vanilla container item became empty");
                next.putByte("Slot", ordinal.byteValue()); contents.add(next);
            }
            result.put("Items", contents);
        }
        if (slots < 0 && !Set.of("sign", "hanging_sign", "banner", "bed", "skull", "conduit", "beacon", "ender_chest",
                "piston", "comparator", "command_block", "structure_block", "jigsaw", "sculk_sensor", "calibrated_sculk_sensor",
                "sculk_shrieker", "end_gateway", "end_portal", "bell", "enchanting_table", "daylight_detector").contains(type.getPath()))
            throw invalid("Original vanilla block entity needs its own reviewed stock schema: " + type);
        return result;
    }
    private static void common(Identifier originalType, CompoundTag source, CompoundTag result, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (source.contains("Bracket")) result.put("Bracket", NeoForgeBlockStateImport.convert(compound(source, "Bracket"), version, registries));
        if (source.contains("components")) result.put("components", NeoForgeItemComponentImport.map(compound(source, "components"), version, registries, child(scope, "components", 0)));
        if (source.contains("Source") && !originalType.equals(Identifier.parse("create:display_link"))) result.store("Source", BlockPos.CODEC, pos(compound(source, "Source")));
        if (source.contains("Speed")) floating(source, "Speed");
        if (source.contains("NeedsSpeedUpdate")) bool(source, "NeedsSpeedUpdate");
        if (source.contains("Sequence")) {
            var sequence = compound(source, "Sequence"); keys(sequence, "kinetic sequence", "Mode", "Value");
            number(sequence, "Value");
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            result.put("Sequence", SequenceContext.CODEC.encodeStart(ops, SequenceContext.CODEC.parse(ops, sequence).getOrThrow()).getOrThrow());
        }
        if (source.contains("Network")) {
            var network = compound(source, "Network"); keys(network, "kinetic network", "Id", "Stress", "Capacity", "Size", "AddedStress", "AddedCapacity");
            if (!(network.get("Id") instanceof LongTag)) throw invalid("Original network Id is not a long");
            for (String key : Set.of("Stress", "Capacity", "AddedStress", "AddedCapacity")) if (network.contains(key)) floating(network, key);
            if (integer(network, "Size") < 0) throw invalid("Negative original kinetic network size");
        }
        filter(source, result, version, registries, child(scope, "filtering", 0));
        if (source.contains("Filters")) {
            var seen = new HashSet<Direction>();
            for (Tag value : list(source, "Filters")) {
                if (!(value instanceof CompoundTag row)) throw invalid("Malformed original sided filter");
                keys(row, "sided filter", "Side", "Filter", "FilterAmount", "UpTo");
                int side = integer(row, "Side");
                if (side < 0 || side > 5) throw invalid("Invalid original filter side");
                var direction = Direction.from3DDataValue(side);
                if (!seen.add(direction)) throw invalid("Duplicate original filter side");
                var next = row.copy(); next.remove("Side"); filter(row, next, version, registries, child(scope, "sided_filter", side));
                result.put("Filter" + direction.name(), next);
            }
            result.remove("Filters");
        }
        for (String key : Set.of("FrequencyFirst", "FrequencyLast")) if (source.contains(key)) result.put(key, item(compound(source, key), version, registries, child(scope, key.toLowerCase(Locale.ROOT), 0)));
        if (source.contains("LastKnownPosition") && !(source.get("LastKnownPosition") instanceof LongTag))
            throw invalid("Original link position is not a packed long");
    }
    private static void filter(CompoundTag source, CompoundTag result, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        ItemStack stack = ItemStack.EMPTY;
        if (source.contains("Filter")) {
            var next = item(compound(source, "Filter"), version, registries, child(scope, "filter", 0)); result.put("Filter", next);
            stack = ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), next).getOrThrow();
        }
        int count = source.contains("FilterAmount") ? integer(source, "FilterAmount") : 0;
        if (source.contains("UpTo")) bool(source, "UpTo");
        // Source setFilter only clamps when !upTo; a real saved upTo count may therefore exceed
        // the new sample's max stack size. It is an extraction predicate, never extra physical stock.
        if (count < 0) throw invalid("Negative original filter amount");
        if (source.contains("Filter") || source.contains("FilterAmount") || source.contains("UpTo")) {
            result.putInt("FilterAmount", count);
            result.putBoolean("UpTo", count == 0 || source.contains("UpTo") && bool(source, "UpTo"));
        }
    }
    private static CompoundTag slot(Tag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var stack = ItemStack.OPTIONAL_CODEC.parse(ops, tag).getOrThrow();
        var result = new CompoundTag(); result.put("variant", ItemVariant.CODEC.encodeStart(ops, ItemVariant.of(stack)).getOrThrow());
        result.putLong("amount", stack.getCount()); return result;
    }
    private static void inventory(CompoundTag source, CompoundTag target, String oldKey, String prefix, int size, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var stacks = itemHandler(compound(source, oldKey), size, version, registries, child(scope, oldKey.toLowerCase(Locale.ROOT), 0));
        for (int i = 0; i < stacks.size(); i++) target.put(prefix + i, slot(stacks.get(i), registries));
    }
    private static ListTag items(CompoundTag source, String key, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var result = new ListTag();
        for (Tag entry : list(source, key)) {
            if (!(entry instanceof CompoundTag stack)) throw invalid("Malformed original item list " + key);
            result.add(item(stack, version, registries, child(child(scope, key.toLowerCase(Locale.ROOT), 0), "entry", result.size())));
        }
        return result;
    }
    private static void basin(CompoundTag source, CompoundTag result, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var inventory = new CompoundTag();
        inventory(source, inventory, "InputItems", "Input", 9, version, registries, scope);
        inventory(source, inventory, "OutputItems", "Output", 9, version, registries, scope);
        result.put("Inventory", inventory); result.remove("InputItems"); result.remove("OutputItems");
        var fluids = new CompoundTag();
        for (String kind : Set.of("Input", "Output")) {
            var tanks = list(source, kind + "Tanks");
            if (tanks.size() != 2) throw invalid("Original basin must have two " + kind + " fluid segments");
            for (int i = 0; i < 2; i++) {
                if (!(tanks.get(i) instanceof CompoundTag tank)) throw invalid("Malformed original basin fluid segment");
                keys(tank, "basin segment", "TankContent", "Level");
                var fluid = NeoForgeFluidStackImport.tank(compound(tank, "TankContent"), version, registries, child(scope, kind.toLowerCase(Locale.ROOT) + "_tank", i));
                if (fluid.amount() > 81000) throw invalid("Original basin segment exceeds actual capacity");
                fluids.put(kind + i, fluid.slot(registries));
            }
            result.remove(kind + "Tanks");
        }
        result.put("Fluids", fluids);
        result.put("Overflow", items(source, "Overflow", version, registries, scope));
        var overflow = new ListTag();
        for (Tag value : list(source, "FluidOverflow")) {
            if (!(value instanceof CompoundTag fluid)) throw invalid("Malformed original basin fluid overflow");
            overflow.add(NeoForgeFluidStackImport.convert(fluid, version, registries, child(scope, "fluid_overflow", overflow.size())).slot(registries));
        }
        result.put("FluidOverflow", overflow);
        if (source.contains("PreferredSpoutput")) result.store("PreferredSpoutput", Direction.CODEC, direction(source, "PreferredSpoutput"));
        var disabled = new ListTag(); var seen = new HashSet<Direction>();
        for (Tag tag : list(source, "DisabledSpoutput")) {
            if (!(tag instanceof StringTag text)) throw invalid("Malformed original basin disabled spout");
            var side = Direction.valueOf(text.value());
            if (!side.getAxis().isHorizontal() || !seen.add(side)) throw invalid("Invalid/duplicate original disabled spout");
            disabled.add(StringTag.valueOf(side.getSerializedName()));
        }
        result.put("DisabledSpoutput", disabled);
        for (String key : Set.of("VisualizedItems", "VisualizedFluids")) result.remove(key);
    }
    private static Direction direction(CompoundTag source, String key) {
        var side = Direction.valueOf(string(source, key));
        if (!side.getAxis().isHorizontal()) throw invalid("Original basin preferred spout must be horizontal"); return side;
    }
    private static void millstone(CompoundTag source, CompoundTag result, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var inventory = new CompoundTag();
        inventory(source, inventory, "InputInventory", "Input", 1, version, registries, scope);
        inventory(source, inventory, "OutputInventory", "Output", 9, version, registries, scope);
        int timer = integer(source, "Timer");
        if (timer < -512 || timer > 1000000) throw invalid("Original millstone timer outside native range");
        result.put("Inventory", inventory); result.remove("InputInventory"); result.remove("OutputInventory");
        result.put("Processing", compound(inventory, "Input0").get("variant").copy());
        if (source.contains("PendingResults")) throw invalid("Unknown original millstone pending results field/version");
    }
    private static void saw(CompoundTag source, CompoundTag result, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var original = compound(source, "Inventory");
        keys(original, "saw ProcessingInventory", "Size", "Items", "ProcessingTime", "RecipeTime", "AppliedRecipe");
        var slots = original.copy(); slots.remove("ProcessingTime"); slots.remove("RecipeTime"); slots.remove("AppliedRecipe");
        var stacks = itemHandler(slots, 32, version, registries, child(scope, "inventory", 0));
        float remaining = floating(original, "ProcessingTime"), duration = floating(original, "RecipeTime");
        int index = integer(source, "RecipeIndex"); boolean applied = bool(original, "AppliedRecipe");
        if (remaining < -1 || remaining > 1000000 || duration < 0 || duration > 1000000 || index < 0)
            throw invalid("Original saw state outside native range");
        var inventory = new CompoundTag(); var outputs = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            var stack = (CompoundTag) stacks.get(i);
            if (stack.isEmpty()) continue;
            if (applied) outputs.add(stack.copy());
            else if (i == 0) inventory.put("Input", stack.copy());
            else throw invalid("Unapplied original saw has output stock outside its input slot");
        }
        inventory.put("Outputs", outputs); inventory.putFloat("ProcessingTime", remaining); inventory.putFloat("RecipeTime", duration);
        inventory.putBoolean("AppliedRecipe", applied && !outputs.isEmpty()); inventory.putInt("RecipeIndex", index);
        if (!applied && inventory.contains("Input")) inventory.putBoolean("SourceRecipeIndex", true);
        result.put("Inventory", inventory); result.remove("RecipeIndex"); result.remove("PlayEvent");
        if (source.contains("Breaking")) result.store("Breaking", BlockPos.CODEC, pos(compound(source, "Breaking")));
        if (source.contains("Progress") && (integer(source, "Progress") < 0 || integer(source, "Progress") > 9)) throw invalid("Original saw breaking progress outside native range");
        if (source.contains("NextTick") && (integer(source, "NextTick") < -1 || integer(source, "NextTick") > 1000000)) throw invalid("Original saw breaking timer outside native range");
    }
    private static void bogey(CompoundTag source) {
        var data = compound(source, "BogeyData"); keys(data, "standard bogey", "BogeyStyle", "UpsideDown");
        var style = Identifier.parse(string(data, "BogeyStyle")); bool(data, "UpsideDown");
        if (!com.simibubi.create.AllBogeyStyles.BOGEY_STYLES.containsKey(style)) throw invalid("Unknown original bogey style " + style);
    }
    private NeoForgeBlockEntityImport() {}
    public static CompoundTag prepareForVanillaFixer(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries) { return prepareForVanillaFixer(type, source, version, registries, null); }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) { return scope == null ? null : scope.item(role, index); }

}
