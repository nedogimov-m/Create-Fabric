package com.simibubi.create.foundation.utility;

import com.mojang.serialization.Dynamic;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/** Strict pinned original NBT primitives and genuine vanilla ItemStack migration for saved owners. */
public final class NeoForgeSaveCodecs {
    public static void keys(CompoundTag source, String context, String... allowed) {
        var names = Set.of(allowed);
        for (String key : source.keySet()) if (!names.contains(key)) throw invalid("Unknown source " + context + " field/version " + key);
    }
    public static CompoundTag compound(CompoundTag source, String key) {
        if (!(source.get(key) instanceof CompoundTag value)) throw invalid("Missing/malformed source compound " + key);
        return value;
    }
    public static ListTag list(CompoundTag source, String key) {
        if (!(source.get(key) instanceof ListTag value)) throw invalid("Missing/malformed source list " + key);
        return value;
    }
    public static int integer(CompoundTag source, String key) {
        // A genuine flattened JSON text component has no NBT numeric widths. JsonOps may
        // materialize an integral stack count/slot as ByteTag or ShortTag. The actual source
        // Codec.INT accepts numeric tags; retain that domain without truncation or overflow.
        if (!(source.get(key) instanceof net.minecraft.nbt.NumericTag number)) throw invalid("Missing/malformed source integer " + key);
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw invalid("Nonintegral/out-of-range source integer " + key);
        return (int) value;
    }
    public static double number(CompoundTag source, String key) {
        if (!(source.get(key) instanceof DoubleTag)) throw invalid("Missing/malformed source double " + key);
        double value = source.getDoubleOr(key, Double.NaN);
        if (!Double.isFinite(value)) throw invalid("Nonfinite source double " + key); return value;
    }
    public static float floating(CompoundTag source, String key) {
        if (!(source.get(key) instanceof FloatTag)) throw invalid("Missing/malformed source float " + key);
        float value = source.getFloatOr(key, Float.NaN);
        if (!Float.isFinite(value)) throw invalid("Nonfinite source float " + key); return value;
    }
    public static boolean bool(CompoundTag source, String key) {
        if (!(source.get(key) instanceof ByteTag value) || value.byteValue() != 0 && value.byteValue() != 1)
            throw invalid("Missing/malformed source boolean " + key);
        return value.byteValue() == 1;
    }
    public static String string(CompoundTag source, String key) {
        if (!(source.get(key) instanceof StringTag)) throw invalid("Missing/malformed source string " + key);
        return source.getString(key).orElseThrow();
    }
    public static UUID uuid(CompoundTag source, String key) {
        if (!(source.get(key) instanceof IntArrayTag)) throw invalid("Missing/malformed source UUID " + key);
        return UUIDUtil.CODEC.parse(NbtOps.INSTANCE, source.get(key)).getOrThrow();
    }
    public static BlockPos pos(CompoundTag source) {
        keys(source, "block position", "X", "Y", "Z");
        return new BlockPos(integer(source, "X"), integer(source, "Y"), integer(source, "Z"));
    }
    public static ListTag vector(ListTag source) {
        if (source.size() != 3) throw invalid("Actual source vector must contain three doubles");
        for (Tag value : source) if (!(value instanceof DoubleTag number) || !Double.isFinite(number.doubleValue()))
            throw invalid("Actual source vector must contain only finite doubles");
        return source.copy();
    }
    public static CompoundTag item(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported original item data version " + sourceVersion);
        keys(source, "item stack", "id", "count", "components");
        if (!source.isEmpty()) {
            string(source, "id");
            if (source.contains("count") && (integer(source, "count") <= 0 || integer(source, "count") > 99))
                throw invalid("Source item count is outside the genuine original codec range1..99");
            if (source.contains("components")) compound(source, "components");
        }
        var prepared = NeoForgeItemComponentImport.prepareItemForVanillaFixer(source, sourceVersion, registries, scope);
        Tag fixed = source.isEmpty() ? prepared : DataFixers.getDataFixer().update(References.ITEM_STACK,
            new Dynamic<Tag>(NbtOps.INSTANCE, prepared), sourceVersion, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if (!(fixed instanceof CompoundTag nativeItem)) throw invalid("Actual item DFU did not produce an item compound");
        fixed = NeoForgeItemComponentImport.finishItemAfterVanillaFixer(source, nativeItem, sourceVersion, registries, scope);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var item = ItemStack.OPTIONAL_CODEC.parse(ops, fixed).getOrThrow();
        if (!source.isEmpty() && (item.isEmpty() || item.getCount() != (source.contains("count") ? integer(source, "count") : 1)))
            throw invalid("Native item codec changed the original finite item count");
        return (CompoundTag) ItemStack.OPTIONAL_CODEC.encodeStart(ops, item).getOrThrow();
    }
    /** Actual NeoForge ItemStackHandler sparse slots become a complete explicit native slot list. */
    public static ListTag itemHandler(CompoundTag source, int requiredSize, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "ItemStackHandler", "Size", "Items");
        if (integer(source, "Size") != requiredSize) throw invalid("Source/native inventory size disagrees");
        var result = new ListTag(); for (int i = 0; i < requiredSize; i++) result.add(new CompoundTag());
        var occupied = new HashSet<Integer>();
        for (Tag value : list(source, "Items")) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original ItemStackHandler entry");
            keys(entry, "ItemStackHandler entry", "Slot", "id", "count", "components");
            int slot = integer(entry, "Slot");
            if (slot < 0 || slot >= requiredSize || !occupied.add(slot)) throw invalid("Duplicate/out-of-range original inventory slot");
            var stack = entry.copy(); stack.remove("Slot");
            var converted = item(stack, sourceVersion, registries, scope == null ? null : scope.item("slot", slot));
            if (converted.isEmpty()) throw invalid("Sparse original inventory entry cannot be empty");
            result.set(slot, converted);
        }
        return result;
    }
    public static IllegalArgumentException invalid(String detail) { return new IllegalArgumentException("Cannot migrate genuine NeoForge save: " + detail); }
    private NeoForgeSaveCodecs() {}
    /** Binary-compatible unscoped entrypoints; packed values require explicit real owner scope. */
    public static CompoundTag item(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
        return item(source, sourceVersion, registries, null);
    }

    public static ListTag itemHandler(CompoundTag source, int requiredSize, int sourceVersion, HolderLookup.Provider registries) {
        return itemHandler(source, requiredSize, sourceVersion, registries, null);
    }

}
