package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** Actual delegated source BaseSpawner/BaseCommandBlock and falling BE leaves. */
final class NeoForgeEntityPayloadImport {
    private static final Identifier SPAWNER = Identifier.parse("minecraft:mob_spawner");
    private static final Set<String> SPAWNER_FIELDS = Set.of("Delay", "MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "MaxNearbyEntities", "RequiredPlayerRange", "SpawnRange", "SpawnData", "SpawnPotentials");
    static void validate(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        String type = string(source, "id");
        if (source.contains("TileEntityData") && !type.equals("minecraft:falling_block")) throw invalid("Original TileEntityData has no actual falling-block owner");
        if ((source.contains("SpawnData") || source.contains("SpawnPotentials")) && !type.equals("minecraft:spawner_minecart")) throw invalid("Original spawner fields have no actual BaseSpawner cart owner");
        switch (type) {
            case "minecraft:falling_block" -> {
                if (source.contains("TileEntityData")) NeoForgeEmbeddedBlockEntityImport.prepare(fallingType(source, version, registries), compound(source, "TileEntityData"), version, registries, child(scope, "tile_entity_data", 0));
                if (source.contains("BlockState")) NeoForgeBlockStateImport.convert(compound(source, "BlockState"), version, registries);
                for (String flag : Set.of("DropItem", "HurtEntities", "CancelDrop")) if (source.contains(flag)) bool(source, flag);
                for (String number : Set.of("Time", "FallHurtMax")) if (source.contains(number) && !(source.get(number) instanceof IntTag)) throw invalid("Original falling scalar lost its saved int");
                if (source.contains("FallHurtAmount")) floating(source, "FallHurtAmount");
            }
            case "minecraft:spawner_minecart" -> NeoForgeVanillaBlockEntityImport.prepareForVanillaFixer(SPAWNER, spawnerFields(source), version, registries, child(scope, "spawner", 0));
            case "minecraft:command_block_minecart" -> {
                if (source.contains("Command")) string(source, "Command");
                if (source.contains("SuccessCount") && !(source.get("SuccessCount") instanceof IntTag)) throw invalid("Original command success count lost its saved int");
                for (String flag : Set.of("TrackOutput", "UpdateLastExecution")) if (source.contains(flag)) bool(source, flag);
                if (source.contains("LastOutput")) { string(source, "LastOutput"); if (source.contains("TrackOutput") && !bool(source, "TrackOutput")) throw invalid("Original disabled command tracking cannot own a saved output"); }
                if (source.contains("LastExecution") && (!(source.get("LastExecution") instanceof LongTag value) || value.longValue() <= 0 || source.contains("UpdateLastExecution") && !bool(source, "UpdateLastExecution"))) throw invalid("Original command execution history lost its actual enabled positive long");
            }
            default -> {}
        }
    }
    static void prepare(CompoundTag original, CompoundTag target, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        switch (string(original, "id")) {
            case "minecraft:falling_block" -> { if (original.contains("TileEntityData")) target.put("TileEntityData", NeoForgeEmbeddedBlockEntityImport.prepare(fallingType(original, version, registries), compound(original, "TileEntityData"), version, registries, child(scope, "tile_entity_data", 0))); }
            case "minecraft:spawner_minecart" -> {
                var prepared = NeoForgeVanillaBlockEntityImport.prepareForVanillaFixer(SPAWNER, spawnerFields(original), version, registries, child(scope, "spawner", 0));
                for (String key : prepared.keySet()) target.put(key, prepared.get(key).copy());
            }
            default -> {}
        }
    }
    static void finish(CompoundTag original, CompoundTag target, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        switch (string(original, "id")) {
            case "minecraft:falling_block" -> {
                if (original.contains("BlockState")) {
                    var ops = registries.createSerializationContext(NbtOps.INSTANCE); var expected = NeoForgeBlockStateImport.convert(compound(original, "BlockState"), version, registries);
                    if (!BlockState.CODEC.parse(ops, expected).getOrThrow().equals(BlockState.CODEC.parse(ops, target.get("BlockState")).getOrThrow())) throw invalid("Containing fixer changed genuine falling BlockState identity/properties");
                    target.put("BlockState", expected);
                }
                if (original.contains("TileEntityData")) target.put("TileEntityData", NeoForgeEmbeddedBlockEntityImport.finish(fallingType(original, version, registries), compound(original, "TileEntityData"), compound(target, "TileEntityData"), version, registries, child(scope, "tile_entity_data", 0)));
            }
            case "minecraft:spawner_minecart" -> {
                var view = spawnerFields(target); view.putString("id", SPAWNER.toString()); // Current schema selector only; never a block/world owner.
                var fixed = NeoForgeVanillaBlockEntityImport.finishAfterVanillaFixer(SPAWNER, spawnerFields(original), view, version, registries, child(scope, "spawner", 0));
                for (String key : SPAWNER_FIELDS) {
                    if (original.contains(key) && !Set.of("SpawnData", "SpawnPotentials").contains(key) && !original.get(key).equals(fixed.get(key))) throw invalid("Containing fixer changed genuine original BaseSpawner scalar");
                    if (fixed.contains(key)) target.put(key, fixed.get(key).copy());
                }
            }
            case "minecraft:command_block_minecart" -> {
                for (String scalar : Set.of("Command", "SuccessCount", "TrackOutput", "UpdateLastExecution", "LastExecution"))
                    if (original.contains(scalar) && !original.get(scalar).equals(target.get(scalar))) throw invalid("Containing fixer changed genuine original command scalar " + scalar);
                if (original.contains("LastOutput")) target.put("LastOutput", NeoForgeTextComponentImport.flattened((StringTag) original.get("LastOutput"), version, registries, child(scope, "command_output", 0)));
            }
            default -> {}
        }
    }
    private static Identifier fallingType(CompoundTag source, int version, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE); var actual = BlockState.CODEC.parse(ops, NeoForgeBlockStateImport.convert(compound(source, "BlockState"), version, registries)).getOrThrow();
        if (!actual.hasBlockEntity()) throw invalid("Original falling payload has no genuine BE owner in its saved BlockState");
        return NeoForgeBlockEntityImport.originalTypeFor(actual);
    }
    private static CompoundTag spawnerFields(CompoundTag source) { var result = new CompoundTag(); for (String key : SPAWNER_FIELDS) if (source.contains(key)) result.put(key, source.get(key).copy()); return result; }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) { return scope == null ? null : scope.item(role, index); }
    private NeoForgeEntityPayloadImport() {}
}
