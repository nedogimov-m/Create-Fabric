package com.simibubi.create.content.contraptions.minecart.capability;

import com.mojang.serialization.Codec;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.storage.ValueInput;

/** The genuine pinned NeoForge attachment serializer, read once into the actual native entity owner. */
public final class NeoForgeMinecartImport {
    private static final String SOURCE_ROOT = "neoforge:attachments";
    private static final String SOURCE_ID = "create:minecart_controller";
    private static final Set<String> FIELDS = Set.of("Type", "InternalStallData", "StallData", "MainCoupling", "ConnectedCoupling");
    private static final Set<String> STALL = Set.of("Pos", "Motion", "Yaw", "Pitch");
    private static final Set<String> LINK = Set.of("Main", "Connected", "Length", "Contraption");

    public static void migrate(AbstractMinecart cart, ValueInput input) {
        if (cart.level().isClientSide()) return;
        var target = (AttachmentTarget) cart;
        if (target.getAttached(MinecartController.ATTACHMENT) != null) return;
        var nativeRoot = input.read("fabric:attachments", Codec.PASSTHROUGH);
        if (nativeRoot.isPresent()) {
            Tag savedNative = nativeRoot.get().convert(NbtOps.INSTANCE).getValue();
            if (!(savedNative instanceof CompoundTag nativeAttachments))
                throw invalid("Present native attachment root could not be restored");
            if (nativeAttachments.contains(SOURCE_ID))
                throw invalid("Present native controller could not be restored; refusing a legacy replacement");
        }
        // PASSTHROUGH distinguishes an absent old owner from a present malformed owner. A typed
        // optional CompoundTag read could silently turn a malformed old attachment into absence.
        var root = input.read(SOURCE_ROOT, Codec.PASSTHROUGH);
        if (root.isEmpty()) return;
        Tag original = root.get().convert(NbtOps.INSTANCE).getValue();
        if (!(original instanceof CompoundTag attachments)) throw invalid("Old attachment root is not a compound");
        Tag value = attachments.get(SOURCE_ID);
        if (value == null) return;
        if (!(value instanceof CompoundTag source)) throw invalid("Old controller is not a compound");
        var candidate = decode(cart, source);
        // Native attachment identity remains authoritative, even if another real load hook installed it.
        if (target.getAttached(MinecartController.ATTACHMENT) == null)
            target.setAttached(MinecartController.ATTACHMENT, candidate);
    }

    private static MinecartController decode(AbstractMinecart cart, CompoundTag source) {
        var converted = convertSource(source,cart.getUUID(),cart.registryAccess());
        var candidate = MinecartController.CODEC.parse(cart.registryAccess().createSerializationContext(NbtOps.INSTANCE),converted).getOrThrow();
        candidate.bind(cart);return candidate;
    }
    /** Pure validation before original chunk factory lookup, anchored to its actual original saved cart UUID. */
    public static CompoundTag convertSource(CompoundTag source,java.util.UUID actualOriginalCart,
            net.minecraft.core.HolderLookup.Provider registries) {
        java.util.Objects.requireNonNull(actualOriginalCart);
        keys(source, FIELDS, "controller");
        String type = source.getString("Type").orElseThrow(() -> invalid("Missing actual source Type"));
        if (!type.equals("NORMAL") && !type.equals("EMPTY")) throw invalid("Unknown actual source Type " + type);
        if (type.equals("EMPTY") && source.size() != 1) throw invalid("Source EMPTY has unexpected owned values");
        var converted = source.copy();
        converted.remove("Type");
        if (type.equals("EMPTY")) converted.putBoolean("LegacyEmpty", true);
        for (String key : Set.of("InternalStallData", "StallData")) {
            if (!source.contains(key)) continue;
            if (!(source.get(key) instanceof CompoundTag stall)) throw invalid(key + " must be a compound");
            keys(stall, STALL, key);
            vector(stall, "Pos"); vector(stall, "Motion");
            require(stall, "Yaw", FloatTag.class); require(stall, "Pitch", FloatTag.class);
        }
        for (String key : Set.of("MainCoupling", "ConnectedCoupling")) {
            if (!source.contains(key)) continue;
            if (!(source.get(key) instanceof CompoundTag link)) throw invalid(key + " must be a compound");
            keys(link, LINK, key);
            require(link, "Main", IntArrayTag.class); require(link, "Connected", IntArrayTag.class);
            require(link, "Length", FloatTag.class);
            Tag flag = require(link, "Contraption", ByteTag.class);
            byte raw = ((ByteTag) flag).byteValue();
            if (raw != 0 && raw != 1) throw invalid("Actual source Contraption is not a boolean byte");
            // The original format never recorded a paid ItemStack or creative custody. Retain
            // its topology explicitly; this marker cannot manufacture a coupling refund.
            var migrated = link.copy(); migrated.putBoolean("LegacyUnreceipted", true); converted.put(key, migrated);
        }
        for (String key : Set.of("MainCoupling", "ConnectedCoupling")) if(source.contains(key)) {
            var endpoint=key.equals("MainCoupling")?"Main":"Connected";
            var actualEndpoint=net.minecraft.core.UUIDUtil.CODEC.parse(NbtOps.INSTANCE,source.getCompoundOrEmpty(key).get(endpoint)).getOrThrow();
            if(!actualOriginalCart.equals(actualEndpoint))throw invalid("Original saved coupling endpoint names a different genuine cart UUID");
        }
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        var candidate=MinecartController.CODEC.parse(ops,converted).getOrThrow();
        return (CompoundTag)MinecartController.CODEC.encodeStart(ops,candidate).getOrThrow();
    }

    private static void vector(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() != 3)
            throw invalid("Actual source " + key + " must contain three doubles");
        for (Tag value : list) if (!(value instanceof DoubleTag)) throw invalid("Actual source " + key + " must contain only doubles");
    }
    private static Tag require(CompoundTag tag, String key, Class<? extends Tag> type) {
        Tag value = tag.get(key);
        if (!type.isInstance(value)) throw invalid("Actual source " + key + " has unknown/missing type");
        return value;
    }
    private static void keys(CompoundTag tag, Set<String> allowed, String context) {
        for (String key : tag.keySet()) if (!allowed.contains(key))
            throw invalid("Unknown source " + context + " field/version " + key);
    }
    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Cannot migrate genuine NeoForge minecart attachment: " + detail);
    }
    private NeoForgeMinecartImport() {}
}
