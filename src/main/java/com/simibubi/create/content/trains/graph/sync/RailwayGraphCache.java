package com.simibubi.create.content.trains.graph.sync;

import com.simibubi.create.content.trains.graph.TrackGraph;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Side-neutral client state machine, testable on the native server. One volatile publication. */
public final class RailwayGraphCache {
  public enum Outcome {
    APPLIED,
    IGNORED,
    NEEDS_SNAPSHOT,
    REJECTED
  }

  public record View(
      UUID nonce,
      UUID epoch,
      long sequence,
      boolean ready,
      boolean retryable,
      String problem,
      Map<UUID, TrackGraph> graphs) {
    public View {
      graphs = Collections.unmodifiableMap(new LinkedHashMap<>(graphs));
    }
  }

  private record Version(int netId, long revision, CompoundTag content) {}

  private record State(
      View view, Map<UUID, Version> versions, Map<UUID, GraphSyncFrame.Removed> retired) {}

  private volatile State state =
      new State(
          new View(null, null, -1, false, false, "Disconnected", Map.of()), Map.of(), Map.of());

  public View view() {
    return state.view;
  }

  public synchronized void begin(UUID nonce) {
    state =
        new State(
            new View(
                Objects.requireNonNull(nonce),
                null,
                -1,
                false,
                true,
                "Awaiting native graph snapshot",
                Map.of()),
            Map.of(),
            Map.of());
  }

  public synchronized void disconnect() {
    state =
        new State(
            new View(null, null, -1, false, false, "Disconnected", Map.of()), Map.of(), Map.of());
  }

  public synchronized void failed(String problem, boolean retryable) {
    var current = state;
    state =
        new State(
            new View(
                current.view.nonce,
                current.view.epoch,
                current.view.sequence,
                false,
                retryable,
                Objects.requireNonNull(problem),
                current.view.graphs),
            current.versions,
            current.retired);
  }

  public synchronized Outcome accept(
      UUID nonce, GraphSyncFrame frame, HolderLookup.Provider registries) {
    var old = state;
    var previous = old.view;
    if (previous.nonce == null || !previous.nonce.equals(nonce)) return Outcome.IGNORED;
    if (previous.epoch != null && !previous.epoch.equals(frame.epoch())) {
      failed("Graph epoch changed inside an active connection", false);
      return Outcome.REJECTED;
    }
    if (frame.sequence() < previous.sequence
        || !frame.snapshot() && frame.sequence() == previous.sequence) return Outcome.IGNORED;
    if (!frame.snapshot() && (!previous.ready || frame.sequence() != previous.sequence + 1)) {
      failed("Missing native graph lifecycle frame", true);
      return Outcome.NEEDS_SNAPSHOT;
    }
    try {
      var graphs = new LinkedHashMap<UUID, TrackGraph>();
      var versions = new LinkedHashMap<UUID, Version>();
      var retired = new LinkedHashMap<>(old.retired);
      if (!frame.snapshot()) {
        graphs.putAll(previous.graphs);
        versions.putAll(old.versions);
      }
      for (var entry : frame.graphs()) {
        var graph = entry.graph();
        graph.validate();
        if (graph.getNetId() != entry.netId()
            || entry.identityMap().entrySet().stream()
                .anyMatch(
                    identity ->
                        graph.locateNode(identity.getKey()).getNetId() != identity.getValue()))
          throw new IllegalArgumentException(
              "Graph has not restored authoritative transport identity");
        var before = old.versions.get(graph.id);
        var tombstone = retired.get(graph.id);
        var content = entry.tag(registries);
        if (before != null && (entry.netId() != before.netId || graph.revision() < before.revision))
          throw new IllegalArgumentException("Regressed railway graph identity/revision");
        if (tombstone != null
            && (entry.netId() != tombstone.netId() || graph.revision() <= tombstone.revision()))
          throw new IllegalArgumentException("Retired railway graph resurrected by stale state");
        if (before != null && graph.revision() == before.revision) {
          if (!content.equals(before.content))
            throw new IllegalArgumentException("Railway graph changed without a new revision");
          graphs.put(graph.id, previous.graphs.get(graph.id));
          versions.put(graph.id, before);
        } else {
          graphs.put(graph.id, graph);
          versions.put(graph.id, new Version(entry.netId(), graph.revision(), content));
        }
        retired.remove(graph.id);
      }
      for (var removal : frame.removed()) {
        var before = versions.get(removal.graph());
        var tombstone = retired.get(removal.graph());
        if (before != null
                && (before.netId != removal.netId() || removal.revision() < before.revision)
            || tombstone != null
                && (tombstone.netId() != removal.netId()
                    || removal.revision() < tombstone.revision()))
          throw new IllegalArgumentException("Regressed railway graph deletion");
        graphs.remove(removal.graph());
        versions.remove(removal.graph());
        retired.put(removal.graph(), removal);
      }
      if (frame.snapshot())
        for (var entry : old.versions.entrySet())
          if (!graphs.containsKey(entry.getKey()))
            retired.put(
                entry.getKey(),
                new GraphSyncFrame.Removed(
                    entry.getKey(), entry.getValue().netId, entry.getValue().revision));
      var graphIds = new HashSet<Integer>();
      var nodeIds = new HashSet<Integer>();
      for (var graph : graphs.values()) {
        if (!graphIds.add(graph.getNetId()))
          throw new IllegalArgumentException("Duplicate native graph network id");
        for (var location : graph.getNodes())
          if (!nodeIds.add(graph.locateNode(location).getNetId()))
            throw new IllegalArgumentException("Duplicate native node network id");
      }
      while (retired.size() > 65536) retired.remove(retired.keySet().iterator().next());
      state =
          new State(
              new View(nonce, frame.epoch(), frame.sequence(), true, false, "", graphs),
              Collections.unmodifiableMap(versions),
              Collections.unmodifiableMap(retired));
      return Outcome.APPLIED;
    } catch (RuntimeException invalid) {
      failed("Rejected native graph publication: " + invalid.getMessage(), false);
      return Outcome.REJECTED;
    }
  }
}
