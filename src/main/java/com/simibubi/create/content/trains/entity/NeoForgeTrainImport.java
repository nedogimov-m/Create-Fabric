package com.simibubi.create.content.trains.entity;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Complete original Train proposal validated against genuine detached native graph/model codecs. */
public final class NeoForgeTrainImport {
    public record Converted(Train train, CompoundTag tag, Set<UUID> entityIdentities) {}

    public static Converted convert(CompoundTag source, Map<UUID, TrackGraph> graphs, DimensionPalette dimensions,
            int sourceVersion, HolderLookup.Provider registries) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original Train data version");
        var header = NeoForgeTrainStateImport.header(source, graphs, dimensions, sourceVersion, registries);
        var identities = new HashSet<UUID>(); var carriages = new ListTag(); int index = 0;
        for (Tag value : list(source, "Carriages")) {
            if (!(value instanceof CompoundTag carriage)) throw invalid("Malformed original Train carriage");
            var converted = NeoForgeCarriageImport.convert(carriage, header.id(), index++, header.graph(), dimensions, sourceVersion, registries);
            for (var identity : converted.identities())
                if (!identities.add(identity)) throw invalid("Original Train has shared carriage/body/passenger UUID custody");
            carriages.add(converted.tag());
        }
        var result = header.withoutCarriages(); result.put("Carriages", carriages);
        var train = Train.read(result, registries, graphs, dimensions);
        if (!train.id.equals(header.id()) || train.graph != header.graph() || train.trafficManager() != null
                || train.carriages.size() != carriages.size())
            throw invalid("Actual native Train decoding changed original identity/graph/formation custody");
        var actualIdentities = new HashSet<UUID>();
        for (var carriage : train.carriages) {
            if (carriage.train != train || !carriage.storage.ownsContents())
                throw invalid("Decoded original carriage has no single actual native Train/cargo owner");
            var custody = carriage.custodyIdentities();
            if (custody.bodies().size() != 1) throw invalid("Decoded original carriage lacks its one actual body UUID");
            for (var identity : custody.bodies()) if (!actualIdentities.add(identity)) throw invalid("Decoded original body UUID custody is shared");
            for (var identity : custody.passengers()) if (!actualIdentities.add(identity)) throw invalid("Decoded original passenger UUID custody is shared");
        }
        if (!actualIdentities.equals(identities)) throw invalid("Native Train decoder lost an actual original entity owner");
        int wheelCount = train.carriages.stream().mapToInt(carriage -> carriage.isOnTwoBogeys() ? 4 : 2).sum();
        int migrations = list(result, "MigratingPoints").size();
        if (header.graph() == null && migrations != wheelCount || header.graph() != null && migrations != 0)
            throw invalid("Original graph/migration state does not retain exactly one migration point for every actual wheel");
        train.validateMotion();
        // Persist an actual native Train model's canonical save, after complete original data and
        // cargo/passenger validation. No graph/traffic manager is constructed or bound to a world.
        return new Converted(train, train.write(dimensions, registries), Set.copyOf(identities));
    }
    private NeoForgeTrainImport() {}
}
