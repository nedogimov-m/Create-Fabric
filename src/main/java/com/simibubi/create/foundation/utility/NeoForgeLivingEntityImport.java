package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.Identifier;

/** Present original living values only; a partial template never acquires a saved owner. */
final class NeoForgeLivingEntityImport {
    static void validate(CompoundTag source, HolderLookup.Provider registries) {
        for (String key : Set.of("Health", "AbsorptionAmount")) if (source.contains(key)) floating(source, key);
        for (String key : Set.of("HurtTime", "DeathTime")) if (source.contains(key) && !(source.get(key) instanceof ShortTag))
            throw invalid("Original living timer must retain its actual source short");
        if (source.contains("HurtByTimestamp")) integer(source, "HurtByTimestamp");
        if (source.contains("FallFlying")) bool(source, "FallFlying");
        if (source.contains("attributes")) NeoForgePlayerLivingValues.attributes(list(source, "attributes"), registries);
        if (source.contains("active_effects")) NeoForgePlayerLivingValues.effects(source, registries);
        int sleeping = 0;
        for (String key : Set.of("SleepingX", "SleepingY", "SleepingZ")) if (source.contains(key)) { integer(source, key); sleeping++; }
        if (sleeping != 0 && sleeping != 3) throw invalid("Original living sleeping position lost an actual coordinate");
    }

    static void finish(CompoundTag source, CompoundTag target, HolderLookup.Provider registries, boolean actualCompleteSavedTree) {
        validate(source, registries);
        if (source.contains("attributes")) target.put("attributes", NeoForgePlayerLivingValues.attributes(list(source, "attributes"), registries));
        if (source.contains("active_effects")) target.put("active_effects", NeoForgePlayerLivingValues.effects(source, registries));
        if (source.contains("SleepingX")) {
            target.store("sleeping_pos", BlockPos.CODEC, new BlockPos(integer(source, "SleepingX"), integer(source, "SleepingY"), integer(source, "SleepingZ")));
            for (String key : Set.of("SleepingX", "SleepingY", "SleepingZ")) target.remove(key);
        }
        if (target.contains("HurtByTimestamp") && (!source.contains("HurtByTimestamp")
                || integer(target, "HurtByTimestamp") != integer(source, "HurtByTimestamp")))
            throw invalid("Vanilla ENTITY_TREE fixer changed an unowned historical attacker timestamp");
        target.remove("HurtByTimestamp");
        for (String key : Set.of("last_hurt_by_player", "last_hurt_by_player_memory_time", "last_hurt_by_mob", "ticks_since_last_hurt_by_mob"))
            if (target.contains(key)) throw invalid("Original3955 living state has no saved actual attacker identity for native attacker fields");
        if (target.contains(NeoForgeLivingHistory.KEY)) throw invalid("ENTITY_TREE fixer invented original living history");
        var values = new CompoundTag();
        for (String key : Set.of("attributes", "active_effects", "HurtByTimestamp")) if (source.contains(key)) values.put(key, source.get(key).copy());
        if (!values.isEmpty()) {
            var history = new NeoForgeLivingHistory(3955, Identifier.parse(string(source, "id")),
                actualCompleteSavedTree ? NeoForgeLivingHistory.Boundary.SAVED : NeoForgeLivingHistory.Boundary.TEMPLATE,
                actualCompleteSavedTree ? Optional.of(uuid(source, "UUID")) : Optional.empty(), values);
            target.put(NeoForgeLivingHistory.KEY, history.write());
        }
    }

    private NeoForgeLivingEntityImport() {}
}
