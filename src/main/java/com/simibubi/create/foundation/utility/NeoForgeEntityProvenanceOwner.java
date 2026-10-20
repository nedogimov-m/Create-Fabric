package com.simibubi.create.foundation.utility;

import java.util.Optional;

/** Read access to metadata retained by the actual native Entity save/load owner. */
public interface NeoForgeEntityProvenanceOwner {
    Optional<NeoForgeEntityProvenance.SpawnOrigin> create$neoForgeSpawnOrigin();
}
