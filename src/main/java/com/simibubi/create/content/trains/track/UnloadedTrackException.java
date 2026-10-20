package com.simibubi.create.content.trains.track;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** An incomplete native discovery frontier, never an instruction to load missing world chunks. */
public final class UnloadedTrackException extends RuntimeException {
  private final Set<GlobalPos> frontier;

  public UnloadedTrackException(Collection<GlobalPos> positions) {
    super("Native track discovery pending at " + positions);
    var copy = new LinkedHashSet<GlobalPos>();
    for (var position : positions)
      copy.add(GlobalPos.of(position.dimension(), position.pos().immutable()));
    if (copy.isEmpty()) throw new IllegalArgumentException("Empty track frontier");
    frontier = Collections.unmodifiableSet(copy);
  }

  public UnloadedTrackException(ResourceKey<Level> dimension, Collection<BlockPos> positions) {
    this(
        positions.stream()
            .map(pos -> GlobalPos.of(Objects.requireNonNull(dimension), pos))
            .toList());
  }

  public UnloadedTrackException(Level level, BlockPos pos) {
    this(level.dimension(), List.of(pos));
  }

  /**
   * Compatibility view for single-dimension physical discovery. frontiers() retains all dimensions.
   */
  public ResourceKey<Level> dimension() {
    return frontier.iterator().next().dimension();
  }

  public Set<BlockPos> positions() {
    var dimension = dimension();
    var result = new LinkedHashSet<BlockPos>();
    for (var position : frontier)
      if (position.dimension().equals(dimension)) result.add(position.pos());
    return Set.copyOf(result);
  }

  public Set<GlobalPos> frontiers() {
    return frontier;
  }
}
