package com.simibubi.create.test;

import com.simibubi.create.api.event.TrackGraphMergeEvent;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;

/** Scoped observers on the real public Fabric event; no graph/world backend is substituted. */
public final class TrackGraphMergeTestFixtures implements ModInitializer {
  private static final ThreadLocal<Consumer<TrackGraphMergeEvent>> ACTIVE = new ThreadLocal<>();

  @Override public void onInitialize() {
    TrackGraphMergeEvent.EVENT.register(event -> {
      var observer = ACTIVE.get();
      if (observer != null) observer.accept(event);
    });
  }

  public static AutoCloseable observe(Consumer<TrackGraphMergeEvent> observer) {
    var previous = ACTIVE.get();
    ACTIVE.set(observer);
    return () -> { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); };
  }

  public static final class ScopedFault extends Error {}
}
