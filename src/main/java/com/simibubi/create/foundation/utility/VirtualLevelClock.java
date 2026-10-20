package com.simibubi.create.foundation.utility;

/** Actual isolated world time for marked virtual owners; ordinary native levels keep their time. */
public interface VirtualLevelClock {
    long virtualGameTime();
}
