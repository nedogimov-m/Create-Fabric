package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/** Exact historical source metadata, never a new native spawn event or reason. */
public final class NeoForgeEntityProvenance {
    public static final String KEY = "CreateNeoForgeEntityProvenance";
    public static final String SOURCE_KEY = "neoforge:spawn_type";
    private static final Set<String> SOURCE_TYPES = Set.of("NATURAL", "CHUNK_GENERATION", "SPAWNER", "STRUCTURE", "BREEDING", "MOB_SUMMONED", "JOCKEY", "EVENT", "CONVERSION", "REINFORCEMENT", "TRIGGERED", "BUCKET", "SPAWN_EGG", "COMMAND", "DISPENSER", "PATROL", "TRIAL_SPAWNER");
    public record SpawnOrigin(int sourceDataVersion, String sourceSpawnType) {
        public SpawnOrigin {
            if (sourceDataVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION || !SOURCE_TYPES.contains(sourceSpawnType))
                throw invalid("Unknown genuine source spawn provenance schema/value");
        }
    }
    public static final Codec<SpawnOrigin> CODEC = CompoundTag.CODEC.xmap(NeoForgeEntityProvenance::read, NeoForgeEntityProvenance::write);
    public static void validateSource(CompoundTag source) {
        if (source.contains(KEY)) throw invalid("Original3955 entity cannot claim the new native provenance schema");
        if (source.contains(SOURCE_KEY)) new SpawnOrigin(3955, string(source, SOURCE_KEY));
    }
    public static void prepareSource(CompoundTag original, CompoundTag sourceCopy) {
        validateSource(original);
        if (!original.contains(SOURCE_KEY)) return;
        sourceCopy.put(KEY, write(new SpawnOrigin(3955, string(original, SOURCE_KEY)))); sourceCopy.remove(SOURCE_KEY);
    }
    public static SpawnOrigin read(CompoundTag source) {
        keys(source, "native historical entity provenance", "SchemaVersion", "SourceDataVersion", "SourceSpawnType");
        if (integer(source, "SchemaVersion") != 1) throw invalid("Unknown native historical provenance version");
        return new SpawnOrigin(integer(source, "SourceDataVersion"), string(source, "SourceSpawnType"));
    }
    public static CompoundTag write(SpawnOrigin source) {
        var result = new CompoundTag(); result.putInt("SchemaVersion", 1); result.putInt("SourceDataVersion", source.sourceDataVersion()); result.putString("SourceSpawnType", source.sourceSpawnType()); return result;
    }
    private NeoForgeEntityProvenance() {}
}
