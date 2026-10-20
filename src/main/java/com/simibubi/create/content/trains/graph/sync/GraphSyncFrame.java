package com.simibubi.create.content.trains.graph.sync;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/**
 * One whole server mutation batch or login snapshot. Decode completely before client publication.
 */
public record GraphSyncFrame(
    UUID epoch,
    long sequence,
    boolean snapshot,
    List<GraphSyncEntry> graphs,
    List<Removed> removed) {
  private static final Codec<Long> NONNEGATIVE_LONG =
      Codec.LONG.validate(
          value ->
              value >= 0
                  ? com.mojang.serialization.DataResult.success(value)
                  : com.mojang.serialization.DataResult.error(
                      () -> "Negative railway lifecycle value"));

  public record Removed(UUID graph, int netId, long revision) {
    public static final Codec<Removed> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        UUIDUtil.CODEC.fieldOf("Graph").forGetter(Removed::graph),
                        Codec.intRange(1, Integer.MAX_VALUE)
                            .fieldOf("NetId")
                            .forGetter(Removed::netId),
                        NONNEGATIVE_LONG.fieldOf("Revision").forGetter(Removed::revision))
                    .apply(i, Removed::new));

    public Removed {
      Objects.requireNonNull(graph);
      if (netId <= 0 || revision < 0)
        throw new IllegalArgumentException("Invalid railway deletion identity");
    }
  }

  public static final Codec<GraphSyncFrame> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      UUIDUtil.CODEC.fieldOf("Epoch").forGetter(GraphSyncFrame::epoch),
                      NONNEGATIVE_LONG.fieldOf("Sequence").forGetter(GraphSyncFrame::sequence),
                      Codec.BOOL.fieldOf("Snapshot").forGetter(GraphSyncFrame::snapshot),
                      GraphSyncEntry.CODEC
                          .listOf()
                          .fieldOf("Graphs")
                          .forGetter(GraphSyncFrame::graphs),
                      Removed.CODEC.listOf().fieldOf("Removed").forGetter(GraphSyncFrame::removed))
                  .apply(i, GraphSyncFrame::new));

  public GraphSyncFrame {
    Objects.requireNonNull(epoch);
    graphs = List.copyOf(graphs);
    removed = List.copyOf(removed);
    if (sequence < 0 || snapshot && !removed.isEmpty())
      throw new IllegalArgumentException("Invalid railway frame sequence/snapshot");
    var ids = new HashSet<UUID>();
    for (var entry : graphs)
      if (!ids.add(entry.graph().id))
        throw new IllegalArgumentException("Duplicate graph in railway frame");
    for (var deletion : removed)
      if (!ids.add(deletion.graph))
        throw new IllegalArgumentException("Graph both changed and removed in one railway frame");
  }

  public byte[] encode(HolderLookup.Provider registries) {
    try {
      var tag =
          (CompoundTag)
              CODEC
                  .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                  .getOrThrow();
      var output = new BoundedOutput(GraphSyncPart.MAX_BYTES);
      try (var data = new DataOutputStream(output)) {
        NbtIo.write(tag, data);
      }
      return output.toByteArray();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  public static GraphSyncFrame decode(byte[] bytes, HolderLookup.Provider registries) {
    if (bytes.length < 1 || bytes.length > GraphSyncPart.MAX_BYTES)
      throw new IllegalArgumentException("Railway frame exceeds wire budget");
    try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
      var tag = NbtIo.read(input, NbtAccounter.create(GraphSyncPart.MAX_NBT_HEAP));
      if (input.available() != 0)
        throw new IllegalArgumentException("Trailing railway frame bytes");
      return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static final class BoundedOutput extends ByteArrayOutputStream {
    private final int maximum;

    BoundedOutput(int maximum) {
      this.maximum = maximum;
    }

    @Override
    public synchronized void write(int value) {
      check(1);
      super.write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      check(length);
      super.write(bytes, offset, length);
    }

    private void check(int count) {
      if (count < 0 || count > maximum - this.count)
        throw new IllegalArgumentException("Railway frame exceeds wire budget");
    }
  }
}
