package com.simibubi.create.content.trains.entity;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Native carriage-level cargo with explicit migration of source dimensional copies. */
final class CarriageCargoPersistence {
  private static final List<String> FIELDS = List.of("items", "fluids", "TicksSinceLastExchange");

  static CompoundTag read(CompoundTag carriage, CompoundTag entity) {
    var body = entity.getCompound("Contraption").orElseThrow();
    if (body.contains("items") || body.contains("fluids") || body.contains("NativeStandaloneCargo"))
      throw new IllegalArgumentException("A carriage body cannot also own global carriage cargo");
    if (body.getBooleanOr("Disassembled", false)
        || !body.getStringOr("AssemblyPhase", "REMOVED").equals("REMOVED"))
      throw new IllegalArgumentException("Saved carriage cargo requires an actual removed body");
    var positions = bodyPositions(body);
    CompoundTag actual;
    if (carriage.contains("CargoVersion")) {
      if (carriage.getInt("CargoVersion").orElseThrow() != 1)
        throw new IllegalArgumentException("Unknown actual carriage cargo format");
      actual = normalized(carriage.getCompound("Cargo").orElseThrow());
      for (var value : carriage.getList("EntityPositioning").orElseThrow()) {
        if (!(value instanceof CompoundTag dimensional))
          throw new IllegalArgumentException("Malformed actual dimensional positioning");
        for (var field : FIELDS)
          if (dimensional.contains(field))
            throw new IllegalArgumentException(
                "Native carriage cargo cannot also have source dimensional copies");
      }
    } else {
      if (carriage.contains("Cargo"))
        throw new IllegalArgumentException("Global carriage cargo is missing its format marker");
      actual = null;
      for (var value : carriage.getList("EntityPositioning").orElseThrow()) {
        if (!(value instanceof CompoundTag dimensional))
          throw new IllegalArgumentException("Malformed source dimensional positioning");
        var source = normalized(dimensional);
        if (actual == null) actual = source;
        else if (!actual.equals(source))
          throw new IllegalArgumentException(
              "Source dimensional copies disagree about one real carriage cargo");
      }
      if (actual == null) actual = normalized(new CompoundTag());
    }
    for (var kind : List.of("items", "fluids")) {
      var seen = new HashSet<BlockPos>();
      for (var value : actual.getList(kind).orElseThrow()) {
        if (!(value instanceof CompoundTag entry))
          throw new IllegalArgumentException("Invalid actual carriage cargo entry");
        var position = position(entry, "pos");
        if (!positions.contains(position) || !seen.add(position))
          throw new IllegalArgumentException(
              "Duplicate or outside-body actual carriage cargo position");
        entry.getCompound("storage").orElseThrow();
      }
    }
    return actual;
  }

  private static CompoundTag normalized(CompoundTag data) {
    var normalized = new CompoundTag();
    int idle = data.getIntOr("TicksSinceLastExchange", 0);
    if (idle < 0) throw new IllegalArgumentException("Negative actual carriage cargo idle tracker");
    normalized.putInt("TicksSinceLastExchange", idle);
    for (var kind : List.of("items", "fluids")) {
      var value = data.contains(kind) ? data.getList(kind).orElseThrow() : new ListTag();
      var entries = new TreeMap<Long, CompoundTag>();
      for (var tag : value) {
        if (!(tag instanceof CompoundTag entry))
          throw new IllegalArgumentException("Invalid actual source carriage cargo entry");
        var pos = position(entry, "pos");
        var canonical = new CompoundTag();
        var coordinates = new CompoundTag();
        coordinates.putInt("X", pos.getX());
        coordinates.putInt("Y", pos.getY());
        coordinates.putInt("Z", pos.getZ());
        canonical.put("pos", coordinates);
        canonical.put("storage", entry.getCompound("storage").orElseThrow().copy());
        if (entries.putIfAbsent(pos.asLong(), canonical) != null)
          throw new IllegalArgumentException("Duplicate actual source carriage cargo position");
      }
      var list = new ListTag();
      entries.values().forEach(list::add);
      normalized.put(kind, list);
    }
    return normalized;
  }

  static Set<BlockPos> bodyPositions(CompoundTag body) {
    var result = new HashSet<BlockPos>();
    var blocks = body.get("Blocks");
    if (blocks instanceof CompoundTag packed) {
      var palette = packed.getList("Palette").orElseThrow();
      for (var value : packed.getList("BlockList").orElseThrow()) {
        if (!(value instanceof CompoundTag entry))
          throw new IllegalArgumentException("Invalid actual carriage body block");
        int state = entry.getInt("State").orElseThrow();
        var pos = BlockPos.of(entry.getLong("Pos").orElseThrow());
        if (state < 0 || state >= palette.size() || !result.add(pos))
          throw new IllegalArgumentException("Invalid or duplicate actual carriage body block");
      }
    } else if (blocks instanceof ListTag legacy) {
      for (var value : legacy) {
        if (!(value instanceof CompoundTag entry) || !result.add(position(entry, "Pos")))
          throw new IllegalArgumentException("Invalid or duplicate source carriage body block");
      }
    } else throw new IllegalArgumentException("Actual carriage geometry is missing");
    if (result.isEmpty())
      throw new IllegalArgumentException("Empty geometry cannot own actual carriage cargo");
    return Set.copyOf(result);
  }

  private static BlockPos position(CompoundTag data, String key) {
    if (data.get(key) instanceof CompoundTag coordinates)
      return new BlockPos(
          coordinates.getInt("X").orElseThrow(),
          coordinates.getInt("Y").orElseThrow(),
          coordinates.getInt("Z").orElseThrow());
    return data.read(key, BlockPos.CODEC).orElseThrow();
  }

  private CarriageCargoPersistence() {}
}
