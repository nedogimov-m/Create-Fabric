package com.simibubi.create.foundation.utility;

/** State on one actual native region storage, never a chunk-load status or an ownership map. */
public interface NeoForgeOriginalImportFailureOwner {
    NeoForgeOriginalImportFailure create$firstOriginalImportFailure();
    boolean create$recordOriginalImportFailure(NeoForgeOriginalImportFailure failure);
}
