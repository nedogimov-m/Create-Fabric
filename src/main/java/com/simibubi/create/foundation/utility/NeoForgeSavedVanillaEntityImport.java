package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import java.util.ArrayDeque;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/** Actual complete saved entities have stronger source-presence requirements than entity-data templates. */
public final class NeoForgeSavedVanillaEntityImport {
    private record Visit(CompoundTag tag, int depth) {}
    public static CompoundTag convert(CompoundTag source, int version, HolderLookup.Provider registries) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported complete saved entity source version");
        var pending = new ArrayDeque<Visit>(); pending.add(new Visit(source, 0)); int count = 0;
        while (!pending.isEmpty()) {
            var visit = pending.removeFirst();
            if (visit.depth > 64 || ++count > 1024) throw invalid("Original complete saved entity tree exceeds supported bounds");
            var original = visit.tag; var type = Identifier.parse(string(original, "id"));
            if (!NeoForgeEntityItemImport.isSupportedSourceType(type)) throw invalid("Complete custom/player entity needs its actual source owner bridge");
            uuid(original, "UUID");
            // Actual original Entity.saveWithoutId always emits this base state.
            // Missing saved coordinates are not a template-default world origin.
            for (String key : Set.of("Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown"))
                if (!original.contains(key)) throw invalid("Complete original saved entity lost base state " + key);
            NeoForgeVanillaEntityImport.validateBase(original);
            if (NeoForgeEntityItemImport.isLivingSourceType(type)) {
                // Actual original Mob and ArmorStand serializers always emit all
                // four armor and both hand slots, including explicit empty rows.
                list(original, "ArmorItems"); list(original, "HandItems");
            }
            switch (type.getPath()) {
                case "allay", "piglin", "pillager", "villager", "wandering_trader" -> list(original, "Inventory");
                case "chest_boat", "chest_minecart", "hopper_minecart" -> {
                    if (!original.contains("Items") && !original.contains("LootTable")) throw invalid("Complete original container lost both stock and deferred loot owner");
                    if (original.contains("LootTable")) Identifier.parse(string(original, "LootTable"));
                }
                case "donkey", "llama", "mule", "trader_llama" -> {
                    if (bool(original, "ChestedHorse")) list(original, "Items");
                    if (type.getPath().equals("llama") || type.getPath().equals("trader_llama")) {
                        int strength = integer(original, "Strength");
                        if (strength < 1 || strength > 5) throw invalid("Complete original llama lost its actual saved inventory strength");
                    }
                }
                case "item" -> integer(original, "Lifespan");
                case "item_display" -> compound(original, "item");
                case "falling_block" -> { compound(original,"BlockState"); for(String field:Set.of("Time","DropItem","HurtEntities","FallHurtAmount","FallHurtMax","CancelDrop"))if(!original.contains(field))throw invalid("Complete original falling entity lost actual saved field "+field); }
                case "spawner_minecart" -> { for(String field:Set.of("Delay","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange","SpawnPotentials"))if(!original.contains(field))throw invalid("Complete original spawner cart lost actual delegated BaseSpawner field "+field); }
                case "command_block_minecart" -> { for(String field:Set.of("Command","SuccessCount","TrackOutput","UpdateLastExecution"))if(!original.contains(field))throw invalid("Complete original command cart lost actual delegated BaseCommandBlock field "+field); }
                default -> {}
            }
            if (original.contains("Passengers")) for (Tag value : list(original, "Passengers")) {
                if (!(value instanceof CompoundTag child)) throw invalid("Malformed complete saved passenger row");
                pending.addLast(new Visit(child, visit.depth + 1));
            }
        }
        return NeoForgeVanillaEntityImport.convertSaved(source, version, registries);
    }
    private NeoForgeSavedVanillaEntityImport() {}
}
