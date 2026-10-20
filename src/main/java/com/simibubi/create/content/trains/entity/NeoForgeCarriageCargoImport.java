package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.contraptions.NeoForgeMountedStorageImport;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.RailCodecs;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.HashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** One original global cargo owner reconstructed from its genuine identical dimensional copies. */
final class NeoForgeCarriageCargoImport {
    record Converted(CompoundTag cargo, ListTag positioning) {}

    static Converted convert(CompoundTag source, DimensionPalette dimensions, int sourceVersion,
            HolderLookup.Provider registries) {
        return convert(source, dimensions, sourceVersion, registries, null);
    }

    static Converted convert(CompoundTag source, DimensionPalette dimensions, int sourceVersion,
            HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Unknown original carriage cargo data version");
        if (source.contains("Cargo") || source.contains("CargoVersion") || source.contains("Restoration"))
            throw invalid("Original carriage cannot already claim a different native cargo/world receipt schema");
        var originalFrames = list(source, "EntityPositioning");
        if (originalFrames.isEmpty())
            throw invalid("No actual original dimensional cargo copy establishes this carriage's contents");
        var seen = new HashSet<Integer>();
        var frames = new ListTag();
        for (Tag value : originalFrames) {
            if (!(value instanceof CompoundTag original)) throw invalid("Malformed original dimensional carriage frame");
            keys(original, "dimensional carriage frame", "Dim", "Cutoff", "DiscardTicks", "Pivot", "PositionAnchor", "RotationAnchors",
                "items", "fluids", "TicksSinceLastExchange");
            int dimensionIndex = integer(original, "Dim");
            var dimension = dimensions.decode(dimensionIndex);
            if (!seen.add(dimensionIndex)) throw invalid("Duplicate original dimensional carriage/cargo owner");
            float cutoff = floating(original, "Cutoff");
            if (cutoff < -1 || cutoff > 1 || integer(original, "DiscardTicks") < 0)
                throw invalid("Invalid original portal cutoff/discard state");
            // Source TrainCargoManager.write always emits its actual idle timer, even for empty cargo.
            if (integer(original, "TicksSinceLastExchange") < 0) throw invalid("Negative original cargo idle owner");
            var next = original.copy();
            for (String field : new String[] {"items", "fluids", "TicksSinceLastExchange"}) next.remove(field);
            if (original.contains("PositionAnchor")) next.put("PositionAnchor", vector(list(original, "PositionAnchor")));
            if (original.contains("RotationAnchors")) {
                var pair = list(original, "RotationAnchors");
                if (pair.size() != 2) throw invalid("Original carriage rotation requires exactly two actual anchors");
                var anchors = new ListTag();
                for (Tag anchor : pair) {
                    if (!(anchor instanceof CompoundTag data)) throw invalid("Malformed original rotation anchor");
                    keys(data, "rotation anchor", "V");
                    var vector = vector(list(data, "V"));
                    anchors.add(RailCodecs.vector(RailCodecs.VECTOR.parse(net.minecraft.nbt.NbtOps.INSTANCE, vector).getOrThrow()));
                }
                next.put("RotationAnchors", anchors);
            }
            if (original.contains("Pivot")) {
                var pivot = compound(original, "Pivot"); keys(pivot, "dimensional carriage pivot", "Pos", "YO");
                pos(compound(pivot, "Pos"));
                if (pivot.contains("YO")) { int pixels = integer(pivot, "YO"); if (pixels < -32768 || pixels > 32767) throw invalid("Original pivot smoothing exceeds actual native bounds"); }
                // Source findPivot selects a node in this actual dimensional row. Its write(null)
                // deliberately omitted the palette entry, so this is the source context, not an
                // Overworld/default guess or a newly invented dimension.
                var nativePivot = TrackNodeLocation.read(pivot, null).in(dimension);
                next.put("Pivot", nativePivot.write(null));
            }
            frames.add(next);
        }
        var originalEntity = compound(source, "Entity");
        // Reuse the complete actual native normalizer: source copies must agree in all stock,
        // components and idle state; cargo positions must be unique and inside real body geometry.
        var completeSourceCargo = CarriageCargoPersistence.read(source, originalEntity);
        var cargo = NeoForgeMountedStorageImport.cargo(completeSourceCargo, sourceVersion, registries, scope);
        return new Converted(cargo, frames);
    }
    private NeoForgeCarriageCargoImport() {}
    

}
