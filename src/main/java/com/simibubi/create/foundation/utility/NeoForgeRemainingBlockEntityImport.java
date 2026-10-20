package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.equipment.toolbox.ToolboxInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Original3955 domain conversion for the remaining fourteen reviewed block-entity owners. */
public final class NeoForgeRemainingBlockEntityImport {
    private static final Set<String> TYPES = Set.of("schematicannon", "schematic_table", "chain_conveyor", "item_drain",
        "belt", "mechanical_arm", "crushing_wheel_controller", "mechanical_crafter", "placard", "lectern_controller",
        "toolbox", "copycat", "backtank", "clipboard");

    public static Optional<CompoundTag> convert(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries) {
        return convert(type, source, version, registries, null);
    }

    public static Optional<CompoundTag> convert(Identifier type, CompoundTag source, int version,
                                               HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!owned(type)) return Optional.empty();
        var context = context(version, registries, scope);
        var result = Objects.requireNonNull(source, "source").copy();
        switch (type.getPath()) {
            case "schematicannon" -> cannon(source, result, context);
            case "schematic_table" -> {
                result.put("Inventory", itemHandler(compound(source, "Inventory"), 2, version, registries, context.leaf("inventory", 0)));
                if (source.contains("Uploading") || source.contains("Schematic") || source.contains("Progress"))
                    throw invalid("Original schematic-table upload fields are client packets, not a persisted paid operation");
            }
            case "chain_conveyor" -> chain(source, result, context);
            case "item_drain" -> drain(source, result, context);
            case "belt" -> belt(source, result, context);
            case "mechanical_arm" -> arm(source, result, context);
            case "crushing_wheel_controller" -> crusher(source, result, context);
            case "mechanical_crafter" -> crafter(source, result, context);
            case "placard" -> {
                result.put("Item", context.item(compound(source, "Item"), "item", 0));
                bounded(integer(source, "PoweredTicks"), 0, 19, "placard power countdown");
            }
            case "lectern_controller" -> {
                result.put("ControllerData", NeoForgeItemComponentImport.container(list(source, "ControllerData"), version, registries, context.leaf("controllerdata", 0)));
                if (source.contains("User")) result.put("User", context.encode(UUIDUtil.CODEC, uuid(source, "User")));
            }
            case "toolbox" -> toolbox(source, result, context);
            case "copycat" -> copycat(source, result, context);
            case "backtank" -> {
                result.put("Components", NeoForgeItemComponentImport.patch(compound(source, "Components"), version, registries, context.leaf("components", 0)));
                int enchantment = integer(source, "CapacityEnchantment");
                if (enchantment < 0) throw invalid("Negative original backtank capacity enchantment");
                if (integer(source, "Air") < 0) throw invalid("Negative original backtank air");
                bounded(integer(source, "Timer"), 0, 20, "backtank timer");
                textName(source, result, context);
            }
            case "clipboard" -> {
                if (source.contains("components")) result.put("components", NeoForgeItemComponentImport.map(compound(source, "components"), version, registries, context.leaf("components", 0)));
                if (source.contains("LastEdit")) result.put("LastEdit", context.encode(UUIDUtil.CODEC, uuid(source, "LastEdit")));
            }
            default -> throw new AssertionError(type);
        }
        return Optional.of(result);
    }

    /** Original SafeNBTWriter output is partial placement data, never a full saved inventory owner. */
    public static Optional<CompoundTag> convertSafe(Identifier type, CompoundTag source, int version,
                                                   HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!owned(type)) return Optional.empty();
        var context = context(version, registries, scope);
        var result = Objects.requireNonNull(source, "source").copy();
        switch (type.getPath()) {
            case "chain_conveyor" -> result.put("Connections", connections(list(source, "Connections"), context));
            case "mechanical_arm" -> result.put("InteractionPoints", points(list(source, "InteractionPoints"), context));
            case "mechanical_crafter" -> {
                if (source.contains("ConnectedInput")) {
                    result.put("OriginalConnectedInput", connectedInput(compound(source, "ConnectedInput"), context));
                    result.remove("ConnectedInput");
                }
            }
            case "lectern_controller" -> result.put("ControllerData", NeoForgeItemComponentImport.container(list(source, "ControllerData"), version, registries, context.leaf("controllerdata", 0)));
            case "copycat" -> copycat(source, result, context);
            default -> { }
        }
        return Optional.of(result);
    }

    private static boolean owned(Identifier type) {
        Objects.requireNonNull(type, "sourceType");
        return type.getNamespace().equals("create") && TYPES.contains(type.getPath());
    }
    private static Context context(int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported original remaining-BE version " + version);
        return new Context(version, Objects.requireNonNull(registries, "registries"), scope);
    }
    private record Context(int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        CompoundTag item(CompoundTag source, String role, int index) { return NeoForgeSaveCodecs.item(source, version, registries, leaf(role, index)); }
        NeoForgeMigrationScope leaf(String role, int index) { return scope == null ? null : scope.item(role, index); }
        <T> T decode(Codec<T> codec, Tag value) { return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
        <T> Tag encode(Codec<T> codec, T value) { return codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
        <T> Tag canonical(Codec<T> codec, Tag value) { return encode(codec, decode(codec, value)); }
        ItemStack stack(Tag value) { return decode(ItemStack.OPTIONAL_CODEC, value); }
        NeoForgeMigrationScope actualScope() {
            if (scope == null) throw invalid("Actual containing source-owner coordinates are required for new native identities");
            return scope;
        }
    }

    private static void cannon(CompoundTag source, CompoundTag result, Context context) {
        result.put("Inventory", itemHandler(compound(source, "Inventory"), 5, context.version, context.registries, context.leaf("inventory", 0)));
        for (String name : List.of("Progress", "PaperProgress")) bounded(floating(source, name), 0, 1, "cannon " + name);
        for (String name : List.of("RemainingFuel", "AmountPlaced", "AmountToPlace"))
            if (integer(source, name) < 0) throw invalid("Negative original cannon " + name);
        string(source, "Status"); floating(source, "DefaultYaw");
        var state = SchematicannonBlockEntity.State.valueOf(string(source, "State"));
        if (source.contains("Running") && (!bool(source, "Running") || state != SchematicannonBlockEntity.State.RUNNING))
            throw invalid("Original cannon running marker disagrees with actual state");
        if (source.contains("MissingItem")) {
            var item = context.item(compound(source, "MissingItem"), "missingitem", 0);
            if (item.isEmpty()) result.remove("MissingItem"); else result.put("MissingItem", item);
        }
        var options = compound(source, "Options"); keys(options, "cannon options", "replace_mode", "skip_missing", "replace_block_entities");
        result.put("Options", context.canonical(SchematicannonBlockEntity.SchematicannonOptions.CODEC, options));
        if (source.contains("Printer")) result.put("Printer", printer(compound(source, "Printer"), context));
        var launches = new ListTag(); int index = 0;
        for (Tag value : list(source, "FlyingBlocks")) launches.add(launch(asCompound(value, "schematic launch"), index++, context));
        result.put("FlyingBlocks", launches);
    }
    private static CompoundTag printer(CompoundTag source, Context context) {
        keys(source, "SchematicPrinter", "CurrentPos", "Anchor", "EntityProgress", "PrintStage", "DeferredBlocks");
        var result = source.copy();
        for (String name : List.of("CurrentPos", "Anchor")) if (source.contains(name))
            result.put(name, context.encode(BlockPos.CODEC, pos(compound(source, name))));
        if (integer(source, "EntityProgress") < -1) throw invalid("Invalid original printer entity cursor");
        if (!Set.of("BLOCKS", "DEFERRED_BLOCKS", "ENTITIES").contains(string(source, "PrintStage"))) throw invalid("Unknown original printer stage");
        var deferred = new ListTag(); var seen = new HashSet<BlockPos>();
        for (Tag value : list(source, "DeferredBlocks")) {
            var old = asCompound(value, "deferred schematic block"); keys(old, "deferred schematic block", "Pos");
            var pos = pos(compound(old, "Pos")); if (!seen.add(pos)) throw invalid("Duplicate original deferred schematic target");
            var row = new CompoundTag(); row.put("Pos", context.encode(BlockPos.CODEC, pos)); deferred.add(row);
        }
        result.put("DeferredBlocks", deferred); return result;
    }
    private static CompoundTag launch(CompoundTag source, int index, Context context) {
        keys(source, "LaunchedItem", "TotalTicks", "TicksLeft", "Stack", "Target", "BlockState", "Data", "Length", "Casing", "Entity");
        int total = integer(source, "TotalTicks"), left = integer(source, "TicksLeft");
        if (total < 0 || left < 0 || left > total) throw invalid("Invalid original schematic flight duration");
        var target = pos(compound(source, "Target")); var result = source.copy();
        result.put("Target", context.encode(BlockPos.CODEC, target)); result.put("Stack", context.item(compound(source, "Stack"), "flying_stack", index));
        result.put("JobId", context.encode(UUIDUtil.CODEC, context.actualScope().nativeJobIdentity("cannon_flying", index)));
        // Source consumed requirements before launch, but persisted only an icon. It supplies no
        // refundable PaidOriginals or receipt. Native original-job loading already handles absence.
        if (source.contains("BlockState")) {
            if (source.contains("Entity")) throw invalid("Original launch cannot simultaneously own block and entity payloads");
            Tag encoded = blockState(compound(source, "BlockState"), context); result.put("BlockState", encoded);
            var state = context.decode(BlockState.CODEC, encoded);
            if (source.contains("Data")) {
                if (!(state.getBlock() instanceof EntityBlock factory)) throw invalid("Original launched Data has no actual block-entity factory");
                var owner = factory.newBlockEntity(target, state);
                if (owner == null) throw invalid("Actual launched block state refuses its block-entity metadata factory");
                var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType());
                if (type == null) throw invalid("Actual launched block entity has no registered native type");
                if (type.getNamespace().equals("create")) type = switch (type.getPath()) {
                    case "station" -> Identifier.parse("create:track_station");
                    case "haunted_bell" -> Identifier.parse("create:cursed_bell");
                    default -> type;
                };
                result.put("Data", NeoForgeBlockEntityImport.convertSafe(type, compound(source, "Data"), context.version,
                    context.registries, context.actualScope().block(target)));
            }
            if (source.contains("Length")) {
                int length = integer(source, "Length");
                if (!(source.get("Casing") instanceof IntArrayTag casings) || length < 2 || casings.getAsIntArray().length != length)
                    throw invalid("Original belt launch must retain its complete actual casing geometry");
                for (int casing : casings.getAsIntArray()) bounded(casing, 0, 2, "belt launch casing ordinal");
                if (source.contains("Data")) throw invalid("Original belt launch does not serialize a separate block-entity payload");
            } else if (source.contains("Casing")) throw invalid("Original casing map lacks a belt launch length");
        } else {
            if (source.contains("Data") || source.contains("Length") || source.contains("Casing")) throw invalid("Original entity flight carries incompatible block fields");
            result.put("Entity", NeoForgeSavedVanillaEntityImport.convert(compound(source, "Entity"), context.version, context.registries));
        }
        LaunchedItem.fromNBT(result, context.registries, context.registries.lookupOrThrow(Registries.BLOCK));
        return result;
    }

    private static void chain(CompoundTag source, CompoundTag result, Context context) {
        var offsets = connections(list(source, "Connections"), context); result.put("Connections", offsets);
        var actual = new HashSet<BlockPos>(); for (Tag value : offsets) actual.add(context.decode(BlockPos.CODEC, value));
        var loops = new ListTag(); int index = 0;
        for (Tag value : list(source, "LoopingPackages")) loops.add(parcel(asCompound(value, "looping package"), "chain_looping", index++, context));
        result.put("LoopingPackages", loops);
        var travels = new ListTag(); var seen = new HashSet<BlockPos>(); index = 0;
        for (Tag value : list(source, "TravellingPackages")) {
            var old = asCompound(value, "travelling package group"); keys(old, "travelling package group", "Target", "Packages");
            var target = pos(compound(old, "Target"));
            if (!actual.contains(target) || !seen.add(target)) throw invalid("Original travelling package has duplicate/missing actual chain connection");
            var next = new CompoundTag(); next.put("Target", context.encode(BlockPos.CODEC, target)); var packages = new ListTag();
            for (Tag row : list(old, "Packages")) packages.add(parcel(asCompound(row, "travelling package"), "chain_travelling", index++, context));
            next.put("Packages", packages); travels.add(next);
        }
        result.put("TravellingPackages", travels);
    }
    private static ListTag connections(ListTag source, Context context) {
        var result = new ListTag(); var seen = new HashSet<BlockPos>();
        for (Tag value : source) {
            var offset = context.decode(BlockPos.CODEC, value);
            double horizontal = Math.hypot(offset.getX(), offset.getZ()) - 1.5;
            if (!seen.add(offset) || offset.distSqr(BlockPos.ZERO) < 2.5 * 2.5 || horizontal <= 0 || Math.abs((double) offset.getY()) > horizontal)
                throw invalid("Invalid actual stored chain-conveyor connection geometry");
            result.add(context.encode(BlockPos.CODEC, offset));
        }
        return result;
    }
    private static CompoundTag parcel(CompoundTag source, String role, int index, Context context) {
        keys(source, "ChainConveyorPackage", "Position", "Item"); floating(source, "Position");
        var item = context.item(compound(source, "Item"), role, index); var stack = context.stack(item);
        if (!PackageItem.isPackage(stack) || stack.getCount() != 1) throw invalid("Original chain package must retain one genuine parcel");
        var result = source.copy(); result.put("Item", item);
        result.put("Identity", context.encode(UUIDUtil.CODEC, context.actualScope().nativeTransportIdentity(role, index)));
        return result;
    }

    private static void drain(CompoundTag source, CompoundTag result, Context context) {
        bounded(integer(source, "ProcessingTicks"), 0, 20, "item-drain processing countdown");
        if (source.contains("HeldItem")) {
            var held = transported(compound(source, "HeldItem"), "drain_held", 0, context);
            bounded(floating(held, "Pos"), 0, 1, "drain held position");
            result.put("HeldItem", held);
        }
        var tanks = list(source, "Tanks"); if (tanks.size() != 1) throw invalid("Original item drain must have exactly one actual fluid tank");
        var segment = asCompound(tanks.get(0), "item-drain tank segment"); keys(segment, "item-drain tank segment", "TankContent", "Level");
        var fluid = NeoForgeFluidStackImport.tank(compound(segment, "TankContent"), context.version, context.registries, context.leaf("tank", 0));
        if (fluid.amount() > 1500L * 81) throw invalid("Original drain fluid exceeds its actual capacity");
        var level = compound(segment, "Level"); keys(level, "tank interpolation", "Speed", "Target", "Value", "Force");
        floats(level, "Speed", "Target", "Value"); if (level.contains("Force")) bool(level, "Force");
        result.put("Tank", fluid.slot(context.registries)); result.remove("Tanks");
    }

    private static void belt(CompoundTag source, CompoundTag result, Context context) {
        boolean controller = bool(source, "IsController"); int length = integer(source, "Length"), index = integer(source, "Index");
        if (length < 0 || length == 1 || index < 0 || controller && index != 0 || length == 0 && index != 0 || length > 0 && index >= length)
            throw invalid("Invalid original belt length/index");
        if (source.contains("Controller")) result.put("Controller", context.encode(BlockPos.CODEC, pos(compound(source, "Controller"))));
        if (source.contains("Dye")) {
            DyeColor selected = null; String name = string(source, "Dye");
            for (DyeColor color : DyeColor.values()) if (color.name().equals(name)) selected = color;
            if (selected == null) throw invalid("Unknown original belt dye " + name); result.put("Dye", context.encode(DyeColor.CODEC, selected));
        }
        String casing = string(source, "Casing"); if (!Set.of("NONE", "ANDESITE", "BRASS").contains(casing)) throw invalid("Unknown original belt casing");
        bool(source, "Covered");
        if (controller) {
            var old = compound(source, "Inventory"); keys(old, "BeltInventory", "Items", "LazyItem", "PositiveOrder");
            bool(old, "PositiveOrder"); var items = new ListTag(); int row = 0;
            for (Tag value : list(old, "Items")) {
                var stack = transported(asCompound(value, "belt cargo"), "belt_items", row++, context);
                // transported() already validates the mandatory source integer; native optional0 is omitted on encode.
                float at = floating(stack, "Pos"), previous = floating(stack, "PrevPos"); int segment = stack.getIntOr("InSegment", 0);
                if (length < 2 || at < 0 || at > length || previous < 0 || previous > length || segment < 0 || segment >= length)
                    throw invalid("Original cargo lies outside its actual containing belt geometry");
                items.add(stack);
            }
            // LazyItem is a client interpolation mirror, not a second material owner.
            if (old.contains("LazyItem")) transported(compound(old, "LazyItem"), "belt_lazy_reference", 0, context);
            var inventory = new CompoundTag(); inventory.put("Items", items); result.put("Inventory", inventory);
        } else if (source.contains("Inventory")) throw invalid("Original noncontroller belt cannot own its controller inventory");
    }

    private static void arm(CompoundTag source, CompoundTag result, Context context) {
        result.put("HeldItem", context.item(compound(source, "HeldItem"), "helditem", 0));
        result.put("InteractionPoints", points(list(source, "InteractionPoints"), context));
        ArmBlockEntity.Phase.valueOf(string(source, "Phase")); bool(source, "Powered"); bool(source, "Goggles");
        if (integer(source, "TargetPointIndex") < -1) throw invalid("Invalid original arm target index");
        bounded(floating(source, "MovementProgress"), 0, 1, "arm movement progress");
    }
    private static ListTag points(ListTag source, Context context) {
        var result = new ListTag(); var positions = new HashSet<BlockPos>();
        for (Tag value : source) {
            var old = asCompound(value, "arm interaction point"); keys(old, "core arm interaction point", "Type", "Pos", "Mode");
            var data = context.decode(ArmInteractionPoint.PointData.CODEC, old);
            if (!positions.add(data.pos())) throw invalid("Duplicate original arm target location");
            if (!data.type().getNamespace().equals("create") || !CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.containsKey(data.type()))
                throw invalid("Original arm point requires an unavailable addon/native endpoint type " + data.type());
            result.add(context.encode(ArmInteractionPoint.PointData.CODEC, data));
        }
        return result;
    }

    private static void crusher(CompoundTag source, CompoundTag result, Context context) {
        var original = compound(source, "Inventory"); keys(original, "crusher processing inventory", "Size", "Items", "ProcessingTime", "RecipeTime", "AppliedRecipe");
        var ish = original.copy(); ish.remove("ProcessingTime"); ish.remove("RecipeTime"); ish.remove("AppliedRecipe");
        var items = itemHandler(ish, 32, context.version, context.registries, context.leaf("inventory", 0)); boolean applied = bool(original, "AppliedRecipe");
        float time = floating(original, "ProcessingTime"); floating(original, "RecipeTime");
        bounded(time, 0, 1000000, "crusher remaining processing time");
        var inventory = new CompoundTag(); var outputs = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            var item = (CompoundTag) items.get(slot); if (item.isEmpty()) continue;
            if (applied) outputs.add(item);
            else if (slot == 0) inventory.put("Input", item);
            else throw invalid("Original pre-recipe crusher has unexpected output stock");
        }
        inventory.put("Outputs", outputs); inventory.putFloat("ProcessingTime", time); inventory.putBoolean("AppliedRecipe", applied);
        result.put("Inventory", inventory); floating(source, "Speed");
        if (source.contains("Entity")) result.put("Entity", context.encode(UUIDUtil.CODEC, uuid(source, "Entity")));
    }

    private static void crafter(CompoundTag source, CompoundTag result, Context context) {
        var items = itemHandler(compound(source, "Inventory"), 1, context.version, context.registries, context.leaf("inventory", 0));
        result.put("Inventory", slot((CompoundTag) items.get(0), context));
        result.put("OriginalConnectedInput", connectedInput(compound(source, "ConnectedInput"), context)); result.remove("ConnectedInput");
        var originalGrid = compound(source, "GroupedItems"); keys(originalGrid, "crafter grouped items", "Grid"); var grid = new ListTag(); var cells = new HashSet<String>();
        for (Tag value : list(originalGrid, "Grid")) {
            var old = asCompound(value, "crafter grouped cell"); keys(old, "crafter grouped cell", "x", "y", "item");
            int x = integer(old, "x"), y = integer(old, "y");
            if (!cells.add(x + "/" + y)) throw invalid("Duplicate original crafter grid cell");
            var next = old.copy(); next.put("item", NeoForgeSaveCodecs.item(compound(old, "item"), context.version, context.registries, context.scope == null ? null : context.scope.item("grouped_items", 0).block(new BlockPos(x, y, 0)).item("item", 0))); grid.add(next);
        }
        result.put("GroupedItems", grid);
        if (!Set.of("IDLE", "ACCEPTING", "ASSEMBLING", "EXPORTING", "WAITING", "CRAFTING", "INSERTING").contains(string(source, "Phase")))
            throw invalid("Unknown original crafter phase");
        bounded(integer(source, "CountDown"), 0, 2000, "crafter countdown"); bool(source, "Cover");
    }
    private static CompoundTag connectedInput(CompoundTag source, Context context) {
        keys(source, "crafter ConnectedInput", "Controller", "Data"); boolean root = bool(source, "Controller");
        var positions = new ListTag(); var seen = new HashSet<BlockPos>();
        for (Tag value : list(source, "Data")) {
            var position = pos(asCompound(value, "crafter input offset"));
            if (!seen.add(position)) throw invalid("Duplicate original connected-input offset");
            positions.add(context.encode(BlockPos.CODEC, position));
        }
        if (positions.isEmpty() || root && !seen.contains(BlockPos.ZERO) || !root && (positions.size() != 1 || seen.contains(BlockPos.ZERO)))
            throw invalid("Invalid actual original crafter input owner map");
        var result = new CompoundTag(); result.putBoolean("Controller", root); result.put("Data", positions); return result;
    }

    private static void toolbox(CompoundTag source, CompoundTag result, Context context) {
        var old = compound(source, "Inventory"); keys(old, "toolbox inventory", "Size", "Items", "Compartments");
        var handler = old.copy(); handler.remove("Compartments"); var items = itemHandler(handler, 32, context.version, context.registries, context.leaf("inventory", 0));
        var originalFilters = list(old, "Compartments"); if (originalFilters.size() != 8) throw invalid("Original toolbox must have eight actual compartment templates");
        var filters = new ListTag(); for (Tag value : originalFilters) filters.add(context.item(asCompound(value, "toolbox template"), "compartment_filter", filters.size()));
        var sparse = new CompoundTag(); for (int i = 0; i < items.size(); i++) if (!((CompoundTag) items.get(i)).isEmpty()) sparse.put(Integer.toString(i), items.get(i));
        var slots = new CompoundTag(); slots.putInt("size", 32); slots.put("items", sparse);
        var inventory = new CompoundTag(); inventory.put("items", slots); inventory.put("filters", filters);
        result.put("Inventory", context.canonical(ToolboxInventory.CODEC, inventory));
        if (source.contains("UniqueId")) result.put("UniqueId", context.encode(UUIDUtil.CODEC, uuid(source, "UniqueId")));
        textName(source, result, context);
    }

    private static void copycat(CompoundTag source, CompoundTag result, Context context) {
        var item = context.item(compound(source, "Item"), "item", 0); var stack = context.stack(item);
        if (!stack.isEmpty() && stack.getCount() != 1) throw invalid("Original copycat receipt must retain exactly one original material item");
        result.put("Item", item);
        if (source.contains("Material")) result.put("Material", blockState(compound(source, "Material"), context));
        else if (!stack.isEmpty()) throw invalid("Original copycat material receipt lacks its actual appearance state");
    }
    private static Tag blockState(CompoundTag source, Context context) {
        return NeoForgeBlockStateImport.convert(source, context.version, context.registries);
    }

    private static void textName(CompoundTag source, CompoundTag result, Context context) {
        if (!source.contains("CustomName")) return;
        result.put("CustomName", NeoForgeTextComponentImport.flattened(StringTag.valueOf(string(source, "CustomName")),
            context.version, context.registries, context.leaf("custom_name", 0)));
    }
    private static CompoundTag slot(CompoundTag value, Context context) {
        var stack = context.stack(value); var result = new CompoundTag();
        result.put("variant", context.encode(ItemVariant.CODEC, ItemVariant.of(stack))); result.putLong("amount", stack.getCount()); return result;
    }

    private static CompoundTag transported(CompoundTag source, String role, int index, Context context) {
        keys(source, "TransportedItemStack", "Item", "Pos", "PrevPos", "Offset", "PrevOffset", "InSegment", "Angle", "InDirection",
            "FanProcessingType", "FanProcessingTime", "Locked", "LockedExternally");
        var result = source.copy(); var item = context.item(compound(source, "Item"), role, index);
        if (context.stack(item).isEmpty()) throw invalid("Original transported physical stock cannot become empty");
        result.put("Item", item); floats(source, "Pos", "PrevPos", "Offset", "PrevOffset"); integer(source, "InSegment"); integer(source, "Angle");
        result.put("Id", context.encode(UUIDUtil.CODEC, context.actualScope().nativeTransportIdentity(role, index)));
        int direction = integer(source, "InDirection"); bounded(direction, 0, 5, "original3D direction");
        result.put("InDirection", context.encode(Direction.CODEC, Direction.from3DDataValue(direction)));
        for (String field : List.of("Locked", "LockedExternally")) if (source.contains(field)) bool(source, field);
        if (source.contains("FanProcessingType")) {
            result.putString("FanProcessingType",FanProcessing.stableTypeName(string(source,"FanProcessingType")));integer(source,"FanProcessingTime");
        } else if (source.contains("FanProcessingTime")) throw invalid("Original fan timer lacks its real processing type");
        return asCompound(context.canonical(TransportedItemStack.CODEC, result), "native transported stack");
    }
    private static CompoundTag asCompound(Tag value, String context) {
        if (!(value instanceof CompoundTag compound)) throw invalid("Malformed original " + context + " compound"); return compound;
    }
    private static void bounded(double value, double min, double max, String context) {
        if (!Double.isFinite(value) || value < min || value > max) throw invalid("Unrepresentable original " + context);
    }
    private static void floats(CompoundTag source, String... fields) { for (String field : fields) floating(source, field); }

    private NeoForgeRemainingBlockEntityImport() { }
}
