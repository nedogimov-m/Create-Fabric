package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Typed original evidence only. Loading this record never replays attributes, cures or attacker timing. */
public record NeoForgeLivingHistory(int sourceDataVersion, Identifier originalType, Boundary boundary,
        Optional<UUID> originalEntity, CompoundTag originalValues) {
    public enum Boundary { SAVED, TEMPLATE }
    public interface Holder { Optional<NeoForgeLivingHistory> create$originalLivingHistory(); }
    public static final String KEY = "CreateOriginalLivingHistory";
    public static final Codec<NeoForgeLivingHistory> CODEC = CompoundTag.CODEC.xmap(NeoForgeLivingHistory::read, NeoForgeLivingHistory::write);

    public NeoForgeLivingHistory {
        if (sourceDataVersion != 3955) throw invalid("Unknown original living history version");
        Objects.requireNonNull(originalType); Objects.requireNonNull(boundary); Objects.requireNonNull(originalEntity);
        if (!NeoForgeEntityItemImport.isLivingSourceType(originalType)) throw invalid("Original living history has no reviewed source living type");
        if ((boundary == Boundary.SAVED) != originalEntity.isPresent()) throw invalid("Original living saved boundary requires its actual source UUID; a template has no saved owner");
        originalValues = Objects.requireNonNull(originalValues).copy();
        keys(originalValues, "original living values", "attributes", "active_effects", "HurtByTimestamp");
        if (originalValues.isEmpty()) throw invalid("Empty original living history has no source evidence");
        if (originalValues.contains("attributes")) NeoForgePlayerLivingValues.validateHistoricalAttributes(list(originalValues, "attributes"));
        if (originalValues.contains("active_effects")) NeoForgePlayerLivingValues.validateHistoricalEffects(list(originalValues, "active_effects"));
        if (originalValues.contains("HurtByTimestamp")) integer(originalValues, "HurtByTimestamp");
    }

    @Override public CompoundTag originalValues() { return originalValues.copy(); }
    public static NeoForgeLivingHistory read(CompoundTag data) {
        keys(data, "original living history", "SourceDataVersion", "Type", "Boundary", "Entity", "Values");
        final Boundary boundary;
        try { boundary = Boundary.valueOf(string(data, "Boundary")); }
        catch (IllegalArgumentException failure) { throw invalid("Unknown original living boundary"); }
        return new NeoForgeLivingHistory(integer(data, "SourceDataVersion"), Identifier.parse(string(data, "Type")), boundary,
            data.contains("Entity") ? Optional.of(uuid(data, "Entity")) : Optional.empty(), compound(data, "Values"));
    }
    public CompoundTag write() {
        var result = new CompoundTag(); result.putInt("SourceDataVersion", sourceDataVersion);
        result.putString("Type", originalType.toString()); result.putString("Boundary", boundary.name());
        originalEntity.ifPresent(id -> result.store("Entity", UUIDUtil.CODEC, id));
        result.put("Values", originalValues()); return result;
    }
}
