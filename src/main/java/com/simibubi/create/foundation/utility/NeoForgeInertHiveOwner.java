package com.simibubi.create.foundation.utility;

/** The actual native hive owns this immutable capacity/timer image; no external side cache. */
public interface NeoForgeInertHiveOwner {
    NeoForgeInertHiveSlots create$inertHiveSlots();
    void create$tickInertHiveSlots();
}
