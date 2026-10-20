package com.simibubi.create.api.contraption.storage;

/** Changes are marked only after a Fabric transaction commits. Entity packet delivery is separate. */
public interface SyncedMountedStorage {
    boolean isDirty();
    void markClean();
    void afterSync(com.simibubi.create.content.contraptions.Contraption contraption, net.minecraft.core.BlockPos localPos);
}
