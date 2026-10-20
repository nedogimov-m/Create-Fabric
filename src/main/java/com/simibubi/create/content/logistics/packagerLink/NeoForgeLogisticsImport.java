package com.simibubi.create.content.logistics.packagerLink;

import com.mojang.serialization.Dynamic;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Converts actual pinned network/promise/link schemas through real registries and vanilla ItemStack DFU. */
public final class NeoForgeLogisticsImport {
    public static final String VERSION = "create-ac0c444-logistics-v1";
    public static CompoundTag convert(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported original Minecraft data version " + sourceVersion);
        keys(source, Set.of("LogisticsNetworks"), "scope");
        ListTag values = list(source, "LogisticsNetworks");
        var migrated = new ListTag(); var identities = new HashSet<UUID>();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (Tag value : values) {
            if (!(value instanceof CompoundTag network)) throw invalid("Network is not a compound");
            keys(network, Set.of("Id", "Promises", "Links", "Owner", "Locked"), "network");
            UUID id = uuid(network, "Id");
            if (!identities.add(id)) throw invalid("Duplicate source network UUID " + id);
            var result = network.copy();
            if (network.contains("Owner")) uuid(network, "Owner");
            if (!(network.get("Locked") instanceof ByteTag locked) || locked.byteValue() != 0 && locked.byteValue() != 1)
                throw invalid("Source Locked is not a boolean byte");
            if (!(network.get("Promises") instanceof CompoundTag promises)) throw invalid("Source Promises is not its actual wrapper");
            keys(promises, Set.of("List"), "promises wrapper");
            var migratedPromises = new ListTag();
            for (Tag entry : list(promises, "List")) {
                if (!(entry instanceof CompoundTag promise)) throw invalid("Source promise is not a compound");
                keys(promise, Set.of("ticks_existed", "promised_stack"), "promise");
                requireInt(promise, "ticks_existed");
                if (!(promise.get("promised_stack") instanceof CompoundTag big)) throw invalid("Missing source promised_stack");
                keys(big, Set.of("item_stack", "count"), "promised stack");
                if (requireInt(big, "count") < 0) throw invalid("Negative original promised count");
                if (!(big.get("item_stack") instanceof CompoundTag item)) throw invalid("Missing original actual item_stack");
                keys(item, Set.of("id", "count", "components"), "source item stack");
                Tag fixed = com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.item(item, sourceVersion, registries,
                    com.simibubi.create.foundation.utility.NeoForgeMigrationScope.logisticsNetwork(id).item("promise", migratedPromises.size()).item("item_stack", 0));
                var stack = ItemStack.OPTIONAL_CODEC.parse(ops, fixed).getOrThrow();
                var next = promise.copy(); var nextBig = big.copy();
                nextBig.put("item_stack", ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow());
                next.put("promised_stack", nextBig);
                // Validate the complete actual native promise instead of falling back to an empty queue.
                RequestPromise.CODEC.parse(ops, next).getOrThrow();
                migratedPromises.add(next);
            }
            result.put("Promises", migratedPromises);
            var links = new ListTag(); var positions = new HashSet<GlobalPos>();
            for (Tag entry : list(network, "Links")) {
                if (!(entry instanceof CompoundTag link)) throw invalid("Source link is not a compound");
                keys(link, Set.of("Pos", "Dim"), "link");
                if (!(link.get("Pos") instanceof CompoundTag pos)) throw invalid("Missing source block position");
                keys(pos, Set.of("X", "Y", "Z"), "block position");
                var dimension = Level.OVERWORLD;
                if (link.contains("Dim")) dimension = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(link.getString("Dim").orElseThrow(() -> invalid("Source dimension must be its actual identifier string"))));
                var global = GlobalPos.of(dimension, new BlockPos(requireInt(pos, "X"), requireInt(pos, "Y"), requireInt(pos, "Z")));
                if (!positions.add(global)) throw invalid("Duplicate source link endpoint " + global);
                links.add(GlobalPos.CODEC.encodeStart(ops, global).getOrThrow());
            }
            result.put("Links", links);
            LogisticsNetwork.CODEC.parse(ops, result).getOrThrow();
            migrated.add(result);
        }
        var result = new CompoundTag(); result.put("LogisticsNetworks", migrated); return result;
    }
    private static UUID uuid(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof IntArrayTag)) throw invalid("Source " + key + " is not the actual UUID int array");
        return UUIDUtil.CODEC.parse(NbtOps.INSTANCE, tag.get(key)).getOrThrow();
    }
    private static int requireInt(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof IntTag)) throw invalid("Source " + key + " is not an actual int");
        return tag.getIntOr(key, 0);
    }
    private static ListTag list(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag result)) throw invalid("Source " + key + " is not a list");
        return result;
    }
    private static void keys(CompoundTag tag, Set<String> allowed, String context) {
        for (String key : tag.keySet()) if (!allowed.contains(key)) throw invalid("Unknown source " + context + " field/version " + key);
    }
    private static IllegalArgumentException invalid(String detail) { return new IllegalArgumentException("Cannot migrate genuine NeoForge logistics: " + detail); }
    private NeoForgeLogisticsImport() {}
}
