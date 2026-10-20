package com.simibubi.create.content.logistics;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Pure, explicitly typed original3955 logistics BE conversion. The dispatcher owns common/base
 * fields and shared Filter/Link behaviours; this helper owns the fourteen reviewed domain schemas.
 * It does not instantiate block entities, touch a world, publish an owner, or modify original tags.
 */
public final class NeoForgeLogisticsBlockEntityImport {
    private static final Set<String> TYPES = Set.of("depot", "weighted_ejector", "item_vault", "chute", "smart_chute",
        "brass_tunnel", "packager", "repackager", "package_frogport", "package_postbox", "factory_panel",
        "table_cloth", "stock_ticker", "redstone_requester");

    /** A real containing-owner scope is required if the original actually has transported rows. */
    public static Optional<CompoundTag> convert(Identifier type, CompoundTag source, int sourceVersion,
                                               HolderLookup.Provider registries) {
        return convert(type, source, sourceVersion, registries, null);
    }

    public static Optional<CompoundTag> convert(Identifier type, CompoundTag source, int sourceVersion,
                                               HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        Objects.requireNonNull(type, "sourceType");
        if (!type.getNamespace().equals("create") || !TYPES.contains(type.getPath())) return Optional.empty();
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported original logistics BE version " + sourceVersion);
        var context = new Context(sourceVersion, Objects.requireNonNull(registries, "registries"), scope);
        var result = Objects.requireNonNull(source, "source").copy();
        switch (type.getPath()) {
            case "depot" -> depot(source, result, context, false);
            case "weighted_ejector" -> { depot(source, result, context, true); ejector(source, result, context); }
            case "item_vault" -> vault(source, result, context);
            case "chute", "smart_chute" -> chute(source, result, context);
            case "brass_tunnel" -> tunnel(source, result, context);
            case "packager", "repackager" -> packager(source, result, context);
            case "package_frogport", "package_postbox" -> {
                port(source, result, context);
                if (type.getPath().equals("package_frogport")) frog(source, result, context);
            }
            case "factory_panel" -> factory(source, result, context);
            case "table_cloth" -> cloth(source, result, context);
            case "stock_ticker" -> ticker(source, result, context);
            case "redstone_requester" -> {
                bools(source, "Powered", "Success", "AllowPartial"); string(source, "EncodedAddress");
                result.put("EncodedRequest", orderWithCrafts(compound(source, "EncodedRequest"), context.child("encoded_request", 0)));
            }
            default -> throw new AssertionError(type);
        }
        return Optional.of(result);
    }

    private record Context(int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        CompoundTag item(CompoundTag original, String role, int index) { return com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.item(original, version, registries, leaf(role, index)); }
        NeoForgeMigrationScope leaf(String role, int index) { return scope == null ? null : scope.item(role, index); }
        Context child(String role, int index) { return new Context(version, registries, leaf(role, index)); }
        <T> T decode(Codec<T> codec, Tag value) { return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
        <T> Tag encode(Codec<T> codec, T value) { return codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
        <T> Tag canonical(Codec<T> codec, Tag value) { return encode(codec, decode(codec, value)); }
        ItemStack stack(Tag value) { return decode(ItemStack.OPTIONAL_CODEC, value); }
    }

    private static void depot(CompoundTag source, CompoundTag result, Context context, boolean merging) {
        var outputs = handler(compound(source, "OutputBuffer"), 8, context.child("output_buffer", 0));
        var inventory = new CompoundTag();
        CompoundTag held = null;
        if (source.contains("HeldItem")) held = transported(compound(source, "HeldItem"), "depot_held", 0, context);
        inventory.put("Input0", slot(held == null ? new CompoundTag() : compound(held, "Item"), context));
        for (int i = 0; i < outputs.size(); i++) inventory.put("Output" + i, slot((CompoundTag) outputs.get(i), context));
        result.put("Inventory", inventory); result.remove("OutputBuffer");
        if (held != null) result.put("HeldItem", held);
        if (source.contains("Incoming")) {
            if (!merging && !list(source, "Incoming").isEmpty()) throw invalid("Original plain depot cannot own a merging incoming queue");
            var incoming = new ListTag(); int index = 0;
            for (Tag value : list(source, "Incoming")) incoming.add(transported(asCompound(value, "Incoming row"), "depot_incoming", index++, context));
            result.put("Incoming", incoming);
        }
    }

    private static void ejector(CompoundTag source, CompoundTag result, Context context) {
        integer(source, "HorizontalDistance"); integer(source, "VerticalDistance"); bool(source, "Powered");
        String state = string(source, "State");
        if (!Set.of("CHARGED", "LAUNCHING", "RETRACTING").contains(state)) throw invalid("Unknown original ejector state " + state);
        var lid = compound(source, "Lid"); keys(lid, "ejector LerpedFloat", "Speed", "Target", "Value", "Force");
        floats(lid, "Speed", "Target", "Value"); if (lid.contains("Force")) bool(lid, "Force");
        var flights = new ListTag();
        for (Tag value : list(source, "LaunchedItems")) {
            var old = asCompound(value, "launched item"); keys(old, "launched item", "Location", "Item");
            if (integer(old, "Location") < 0) throw invalid("Negative original launched-item time");
            var next = old.copy(); var stack = context.item(compound(old, "Item"), "launched_item", flights.size()); requirePhysical(stack, context, false);
            next.put("Item", stack); flights.add(next);
        }
        result.put("LaunchedItems", flights);
        if (source.contains("EarlyTarget")) {
            result.put("EarlyTarget", vector(list(source, "EarlyTarget")));
            result.put("EarlyTargetPos", context.encode(BlockPos.CODEC, pos(compound(source, "EarlyTargetPos"))));
            floating(source, "EarlyTargetTime");
        } else if (source.contains("EarlyTargetPos") || source.contains("EarlyTargetTime")) throw invalid("Incomplete original ejector early-target tuple");
        if (source.contains("ForceAngle")) floating(source, "ForceAngle");
    }

    private static void vault(CompoundTag source, CompoundTag result, Context context) {
        var original = compound(source, "Inventory"); int size = integer(original, "Size");
        if (size < 1 || size > 2048) throw invalid("Original per-block vault capacity outside actual1..2048 source/native bounds");
        var stacks = handler(original, size, context.child("inventory", 0));
        int allocated = Math.min(2048, Math.max(20, ((size + 19) / 20) * 20));
        result.putInt("NativeVaultSlotCount", allocated); result.put("Inventory", new CompoundTag());
        for (int i = 0; i < allocated; i++) {
            String page = i < 20 ? "Inventory" : "InventoryPage" + (i / 20 - 1);
            if (!result.contains(page)) result.put(page, new CompoundTag());
            compound(result, page).put("Input" + (i % 20), slot(i < size ? (CompoundTag) stacks.get(i) : new CompoundTag(), context));
        }
        if (!string(source, "StorageType").equals("CombinedInv")) throw invalid("Unknown original vault storage owner type");
        for (String field : List.of("Controller", "LastKnownPos")) if (source.contains(field))
            result.put(field, context.encode(BlockPos.CODEC, pos(compound(source, field))));
        if (source.contains("Controller")) {
            if (source.contains("Size") || source.contains("Length")) throw invalid("Original vault noncontroller cannot own aggregate dimensions");
        } else {
            int width = integer(source, "Size"), length = integer(source, "Length");
            if (width < 1 || width > 3 || length < 1 || length > width * 3) throw invalid("Invalid original vault dimensions");
        }
        if (source.contains("Uninitialized")) bool(source, "Uninitialized");
    }

    private static void chute(CompoundTag source, CompoundTag result, Context context) {
        var stack = context.item(compound(source, "Item"), "item", 0); requirePhysical(stack, context, true); result.put("Item", stack);
        float at = floating(source, "ItemPosition"), bottom = floating(source, "BottomAirFlowDistance");
        floats(source, "Pull", "Push");
        if (at < -16 || at > 2 || bottom < 0 || bottom > 3) throw invalid("Original chute motion exceeds actual native representable bounds");
    }

    private static void tunnel(CompoundTag source, CompoundTag result, Context context) {
        bools(source, "SyncedOutput", "ConnectedLeft", "ConnectedRight");
        var stack = context.item(compound(source, "StackToDistribute"), "stacktodistribute", 0); requirePhysical(stack, context, true); result.put("StackToDistribute", stack);
        float progress = floating(source, "DistributionProgress");
        if (progress != (int) progress || progress < -1 || progress > 10) throw invalid("Original tunnel countdown is not a supported integral tick count");
        result.putInt("DistributionProgress", (int) progress);
        if (integer(source, "PreviousIndex") < 0 || integer(source, "DistanceLeft") < 0 || integer(source, "DistanceRight") < 0)
            throw invalid("Negative original tunnel target index/distance");
        if (source.contains("StackEnteredFrom")) result.put("StackEnteredFrom", context.encode(Direction.CODEC, sourceDirection(string(source, "StackEnteredFrom"))));
        for (String name : List.of("FilteredTargets", "Targets")) {
            var targets = new ListTag(); var seen = new HashSet<String>();
            for (Tag value : list(source, name)) {
                var old = asCompound(value, "tunnel target"); keys(old, "tunnel target", "Pos", "Face");
                var position = pos(compound(old, "Pos")); var face = sourceDirection(integer(old, "Face"));
                if (!seen.add(position.asLong() + "/" + face.name())) throw invalid("Duplicate original tunnel target");
                var next = new CompoundTag(); next.put("Pos", context.encode(BlockPos.CODEC, position)); next.put("Face", context.encode(Direction.CODEC, face)); targets.add(next);
            }
            result.put(name, targets);
        }
    }

    private static void packager(CompoundTag source, CompoundTag result, Context context) {
        bools(source, "Active", "AnimationInward", "HasComputerAddress");
        int ticks = integer(source, "AnimationTicks"); if (ticks < 0 || ticks > 20) throw invalid("Original packager animation exceeds its actual twenty-tick cycle");
        string(source, "SignAddress"); String computer = string(source, "ComputerAddress");
        if (bool(source, "HasComputerAddress") || !computer.isEmpty()) throw invalid("Original active ComputerCraft address needs its addon owner migration");
        for (String field : List.of("HeldBox", "InsertedBox")) {
            var stack = context.item(compound(source, field), field.toLowerCase(java.util.Locale.ROOT), 0); requirePackage(stack, context, true); result.put(field, stack);
        }
        var queue = bigList(list(source, "QueuedExitingPackages"), context.child("queuedexitingpackages", 0));
        for (Tag value : queue) {
            var entry = (CompoundTag) value;
            requirePackage(compound(entry, "item_stack"), context, false);
            if (integer(entry, "count") <= 0) throw invalid("Original queued package multiplicity must be positive");
        }
        result.put("QueuedExitingPackages", context.canonical(BigItemStack.CODEC.listOf(), queue));
        if (source.contains("LastSummary")) {
            var summary = bigList(list(source, "LastSummary"), context.child("lastsummary", 0));
            context.decode(InventorySummary.CODEC, summary); result.put("LastSummary", summary);
        }
    }

    private static void port(CompoundTag source, CompoundTag result, Context context) {
        var items = handler(compound(source, "Inventory"), 18, context.child("inventory", 0));
        for (Tag value : items) requirePackage((CompoundTag) value, context, true);
        context.decode(ItemStack.OPTIONAL_CODEC.listOf(), items); result.put("Inventory", items);
        string(source, "AddressFilter"); bool(source, "AcceptsPackages");
        if (source.contains("Target")) result.put("Target", context.canonical(PackagePortTarget.CODEC, compound(source, "Target")));
    }

    private static void frog(CompoundTag source, CompoundTag result, Context context) {
        floating(source, "PlacedYaw");
        for (String marker : List.of("FailedLastExport", "Goggles", "Anticipate")) if (source.contains(marker)) bool(source, marker);
        if (source.contains("Anticipate")) result.putInt("AnticipateTicks", bool(source, "Anticipate") ? 10 : 0);
        if (!source.contains("AnimatedPackage")) {
            if (source.contains("Deposit")) throw invalid("Original frog animation direction has no package owner");
            return;
        }
        var stack = context.item(compound(source, "AnimatedPackage"), "animatedpackage", 0); requirePackage(stack, context, false);
        boolean deposit = bool(source, "Deposit");
        if (deposit && source.contains("Target") && context.decode(PackagePortTarget.CODEC, compound(source, "Target")) instanceof PackagePortTarget.TrainStationFrogportTarget)
            throw invalid("Original station target cannot export a depositing frog package; stock ownership is ambiguous");
        result.put("AnimatedPackage", stack); result.put("AnimationDisplay", stack.copy());
        result.putBoolean("Animating", true); result.putInt("AnimationTicks", 0);
    }

    private static void cloth(CompoundTag source, CompoundTag result, Context context) {
        var items = itemList(list(source, "Items"), context.child("items", 0));
        if (items.size() > 4) throw invalid("Original table cloth cannot lose display items beyond its four native owners");
        for (Tag value : items) {
            var stack = context.stack(value);
            if (stack.isEmpty() || stack.getCount() != 1) throw invalid("Original table cloth display items must retain their actual one-item ownership");
        }
        result.put("Items", items);
        var request = compound(source, "RequestData");
        keys(request, "AutoRequestData", "encoded_request", "encoded_target_address", "target_offset", "target_dim", "is_valid");
        var next = request.copy(); next.put("encoded_request", orderWithCrafts(compound(request, "encoded_request"), context.child("request_data", 0).child("encoded_request", 0)));
        string(request, "encoded_target_address"); string(request, "target_dim"); bool(request, "is_valid");
        context.decode(BlockPos.CODEC, Objects.requireNonNull(request.get("target_offset")));
        result.put("RequestData", context.canonical(AutoRequestData.CODEC, next));
        int facing = integer(source, "Facing"); if (facing < 0 || facing > 3) throw invalid("Original cloth facing is outside horizontal source ordinals");
        if (source.contains("OwnerUUID")) uuid(source, "OwnerUUID");
    }

    private static void ticker(CompoundTag source, CompoundTag result, Context context) {
        string(source, "PreviousAddress");
        result.put("ReceivedPayments", inventory(handler(compound(source, "ReceivedPayments"), 27, context.child("received_payments", 0)), 27, 0, context));
        var categories = itemList(list(source, "Categories"), context.child("categories", 0));
        for (Tag value : categories) {
            var stack = context.stack(value);
            if (!stack.isEmpty() && (!StockTickerBlockEntity.isCategory(stack) || stack.getCount() != 1))
                throw invalid("Original category is not a supported actual one-item filter owner");
        }
        result.put("Categories", categories);
        var players = new HashSet<java.util.UUID>(); var hidden = new ListTag();
        for (Tag value : list(source, "HiddenCategories")) {
            var row = asCompound(value, "hidden category row"); keys(row, "hidden category row", "Id", "Indices");
            if (!players.add(uuid(row, "Id"))) throw invalid("Duplicate original hidden-category player owner");
            if (!(row.get("Indices") instanceof IntArrayTag indices)) throw invalid("Malformed original hidden-category indices");
            var seen = new HashSet<Integer>();
            for (int index : indices.getAsIntArray()) if (index < -1 || index >= categories.size() || !seen.add(index))
                throw invalid("Original hidden-category index would be dropped by native reader");
            hidden.add(row.copy());
        }
        result.put("HiddenCategories", hidden);
    }

    private static void factory(CompoundTag source, CompoundTag result, Context context) {
        boolean restocker = bool(source, "Restocker");
        for (String quadrant : List.of("top_left", "top_right", "bottom_left", "bottom_right")) {
            if (!source.contains(quadrant)) continue;
            var old = compound(source, quadrant);
            keys(old, "factory panel quadrant", "Filter", "FilterAmount", "UpTo", "Freq", "Timer", "LastLevel", "LastPromised",
                "LastUnloadedLinks", "Satisfied", "PromisedSatisfied", "Waiting", "RedstonePowered", "Targeting", "TargetedBy",
                "TargetedByLinks", "RecipeAddress", "RecipeOutput", "PromiseClearingInterval", "Craft", "Promises");
            var next = old.copy(); var filter = context.child(quadrant, 0).item(compound(old, "Filter"), "filter", 0); var actual = context.stack(filter);
            if (!actual.isEmpty() && (FilterItemStack.of(actual).isFilterItem() || actual.getCount() != 1)) throw invalid("Original factory filter must be a one-item ordinary reference");
            next.put("Filter", filter);
            int count = integer(old, "FilterAmount"), output = integer(old, "RecipeOutput"), interval = integer(old, "PromiseClearingInterval");
            if (count < 0 || count > 100 || output < 1 || output > 64 || interval < -1 || interval > 30) throw invalid("Original panel values exceed native unmodified representability");
            bools(old, "UpTo", "Satisfied", "PromisedSatisfied", "Waiting", "RedstonePowered");
            integers(old, "Timer", "LastLevel", "LastPromised", "LastUnloadedLinks"); uuid(old, "Freq"); string(old, "RecipeAddress");
            var targeting = context.decode(FactoryPanelPosition.CODEC.listOf(), list(old, "Targeting"));
            if (new HashSet<>(targeting).size() != targeting.size()) throw invalid("Duplicate original factory target");
            var sources = new HashSet<FactoryPanelPosition>();
            for (var connection : context.decode(FactoryPanelConnection.CODEC.listOf(), list(old, "TargetedBy")))
                if (!sources.add(connection.from)) throw invalid("Duplicate original factory source panel");
            var links = new HashSet<BlockPos>();
            for (var connection : context.decode(FactoryPanelConnection.CODEC.listOf(), list(old, "TargetedByLinks")))
                if (!links.add(connection.from.pos())) throw invalid("Duplicate original factory source link");
            var craft = itemList(list(old, "Craft"), context.child(quadrant, 0).child("craft", 0)); next.put("Craft", craft);
            if (restocker) next.put("Promises", promises(compound(old, "Promises"), context.child(quadrant, 0).child("promises", 0)));
            else if (old.contains("Promises")) throw invalid("Original ordinary panel has an unowned restocker promise queue");
            result.put(quadrant, next);
        }
    }

    private static Tag promises(CompoundTag source, Context context) {
        keys(source, "RequestPromiseQueue", "List"); var rows = new ListTag();
        for (Tag value : list(source, "List")) {
            var old = asCompound(value, "request promise"); keys(old, "request promise", "ticks_existed", "promised_stack");
            if (integer(old, "ticks_existed") < 0) throw invalid("Negative original promise age");
            var next = old.copy(); next.put("promised_stack", big(compound(old, "promised_stack"), context.child("promise", rows.size()).child("promised_stack", 0))); rows.add(next);
        }
        context.decode(RequestPromise.CODEC.listOf(), rows);
        return context.canonical(RequestPromiseQueue.CODEC, rows);
    }

    private static CompoundTag order(CompoundTag source, Context context) {
        keys(source, "PackageOrder", "entries"); var result = new CompoundTag(); result.put("entries", bigList(list(source, "entries"), context.child("entries", 0)));
        return asCompound(context.canonical(PackageOrder.CODEC, result), "native package order");
    }
    private static Tag orderWithCrafts(CompoundTag source, Context context) {
        var result = source.copy();
        if (source.contains("entries")) {
            keys(source, "legacy PackageOrderWithCrafts alternative", "entries"); result.put("entries", bigList(list(source, "entries"), context.child("entries", 0)));
        } else {
            keys(source, "PackageOrderWithCrafts", "ordered_stacks", "ordered_crafts");
            result.put("ordered_stacks", order(compound(source, "ordered_stacks"), context.child("ordered_stacks", 0))); var crafts = new ListTag();
            for (Tag value : list(source, "ordered_crafts")) {
                var old = asCompound(value, "crafting entry"); keys(old, "crafting entry", "pattern", "count"); integer(old, "count");
                var next = old.copy(); next.put("pattern", order(compound(old, "pattern"), context.child("ordered_craft", crafts.size()).child("pattern", 0))); crafts.add(next);
            }
            result.put("ordered_crafts", crafts);
        }
        return context.canonical(PackageOrderWithCrafts.CODEC, result);
    }
    private static CompoundTag big(CompoundTag source, Context context) {
        keys(source, "BigItemStack", "item_stack", "count");
        if (integer(source, "count") < 0) throw invalid("Negative original BigItemStack multiplicity");
        var result = source.copy(); result.put("item_stack", context.item(compound(source, "item_stack"), "item_stack", 0));
        context.decode(BigItemStack.CODEC, result); return result;
    }
    private static ListTag bigList(ListTag source, Context context) {
        var result = new ListTag(); for (Tag value : source) result.add(big(asCompound(value, "BigItemStack row"), context.child("entry", result.size()))); return result;
    }
    private static ListTag itemList(ListTag source, Context context) {
        var result = new ListTag(); for (Tag value : source) result.add(context.item(asCompound(value, "actual item-list entry"), "entry", result.size())); return result;
    }
    private static ListTag handler(CompoundTag source, int requiredSize, Context context) {
        var result = itemHandler(source, requiredSize, context.version, context.registries, context.scope);
        for (Tag value : result) requirePhysical((CompoundTag) value, context, true);
        return result;
    }
    private static CompoundTag inventory(ListTag stacks, int inputs, int outputs, Context context) {
        if (stacks.size() != inputs + outputs) throw invalid("Original/native physical inventory shape disagrees");
        var result = new CompoundTag();
        for (int i = 0; i < stacks.size(); i++) result.put(i < inputs ? "Input" + i : "Output" + (i - inputs), slot((CompoundTag) stacks.get(i), context));
        return result;
    }
    private static CompoundTag slot(CompoundTag item, Context context) {
        var stack = context.stack(item); requirePhysical(item, context, true);
        var result = new CompoundTag(); result.put("variant", context.encode(ItemVariant.CODEC, ItemVariant.of(stack))); result.putLong("amount", stack.getCount()); return result;
    }
    private static CompoundTag transported(CompoundTag source, String role, int index, Context context) {
        keys(source, "TransportedItemStack", "Item", "Pos", "PrevPos", "Offset", "PrevOffset", "InSegment", "Angle", "InDirection",
            "FanProcessingType", "FanProcessingTime", "Locked", "LockedExternally");
        if (context.scope == null) throw invalid("An actual containing source-owner scope is required for new native transported identities");
        var result = source.copy(); var stack = context.item(compound(source, "Item"), role, index); requirePhysical(stack, context, false); result.put("Item", stack);
        floats(source, "Pos", "PrevPos", "Offset", "PrevOffset"); integers(source, "InSegment", "Angle");
        result.put("Id", context.encode(UUIDUtil.CODEC, context.scope.nativeTransportIdentity(role, index)));
        result.put("InDirection", context.encode(Direction.CODEC, sourceDirection(integer(source, "InDirection"))));
        for (String field : List.of("Locked", "LockedExternally")) if (source.contains(field)) bool(source, field);
        if (source.contains("FanProcessingType")) {
            result.putString("FanProcessingType",FanProcessing.stableTypeName(string(source,"FanProcessingType")));
            integer(source, "FanProcessingTime");
        } else if (source.contains("FanProcessingTime")) throw invalid("Original fan timer has no processing type");
        return asCompound(context.canonical(TransportedItemStack.CODEC, result), "native transported item");
    }
    private static void requirePhysical(CompoundTag value, Context context, boolean emptyAllowed) {
        var stack = context.stack(value);
        if (stack.isEmpty()) { if (!emptyAllowed) throw invalid("Original physical owner cannot become empty"); return; }
        // Genuine recipe/setStack owners may contain source overstacked cargo. Native slot load/setStack
        // deliberately preserves that exact amount, so an item's usual insertion limit is not a save cap.
    }
    private static void requirePackage(CompoundTag value, Context context, boolean emptyAllowed) {
        requirePhysical(value, context, emptyAllowed); var stack = context.stack(value);
        if (!stack.isEmpty() && (!PackageItem.isPackage(stack) || stack.getCount() != 1)) throw invalid("Original package owner must retain one genuine package");
    }
    private static Direction sourceDirection(int sourceOrdinal) {
        if (sourceOrdinal < 0 || sourceOrdinal > 5) throw invalid("Invalid original3D direction ordinal");
        return Direction.from3DDataValue(sourceOrdinal);
    }
    private static Direction sourceDirection(String name) {
        for (Direction direction : Direction.values()) if (direction.name().equals(name)) return direction;
        throw invalid("Unknown original direction enum " + name);
    }
    private static CompoundTag asCompound(Tag value, String owner) {
        if (!(value instanceof CompoundTag compound)) throw invalid("Malformed original " + owner + " compound"); return compound;
    }
    private static void integers(CompoundTag source, String... fields) { for (String field : fields) integer(source, field); }
    private static void floats(CompoundTag source, String... fields) { for (String field : fields) floating(source, field); }
    private static void bools(CompoundTag source, String... fields) { for (String field : fields) bool(source, field); }
    private NeoForgeLogisticsBlockEntityImport() {}
}
