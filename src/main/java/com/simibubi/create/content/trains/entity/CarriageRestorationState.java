package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;

/** Durable restoration intent, becoming a cargo-free receipt only after native world ownership. */
final class CarriageRestorationState {
  final UUID body;
  final ResourceKey<Level> dimension;
  final BlockPos anchor;
  final int yaw;
  final Set<BlockPos> cells;
  final Set<BlockPos> journal = new LinkedHashSet<>();
  boolean restored;

  CarriageRestorationState(
      UUID body,
      ResourceKey<Level> dimension,
      StructureTransform transform,
      Collection<BlockPos> cells) {
    this.body = Objects.requireNonNull(body);
    this.dimension = Objects.requireNonNull(dimension);
    if (transform.rotationAxis != null
            && transform.rotationAxis != net.minecraft.core.Direction.Axis.Y
        || transform.mirror != null && transform.mirror != Mirror.NONE
        || transform.angle % 90 != 0)
      throw new IllegalArgumentException("Actual horizontal carriage restoration required");
    anchor = transform.offset.immutable();
    yaw = Math.floorMod(transform.angle, 360);
    var actual = new LinkedHashSet<BlockPos>();
    for (var cell : cells)
      if (!actual.add(cell.immutable()))
        throw new IllegalArgumentException("Duplicate actual carriage restoration cell");
    if (actual.isEmpty() || actual.size() > 100000)
      throw new IllegalArgumentException("Invalid actual carriage restoration extent");
    this.cells = Collections.unmodifiableSet(actual);
  }

  StructureTransform transform() {
    return new StructureTransform(anchor, 0, yaw, 0);
  }

  CompoundTag write(DimensionPalette dimensions) {
    var tag = new CompoundTag();
    tag.putInt("Version", 1);
    tag.store("Body", UUIDUtil.CODEC, body);
    tag.putInt("Dimension", dimensions.encode(dimension));
    tag.store("Anchor", BlockPos.CODEC, anchor);
    tag.putInt("Yaw", yaw);
    tag.putBoolean("WorldRestored", restored);
    tag.store(
        restored ? "WrittenCells" : "TargetCells", BlockPos.CODEC.listOf(), List.copyOf(cells));
    if (!journal.isEmpty())
      tag.store("WorldNotificationJournal", BlockPos.CODEC.listOf(), List.copyOf(journal));
    return tag;
  }

  static CarriageRestorationState read(CompoundTag tag, DimensionPalette dimensions) {
    if (tag.getInt("Version").orElseThrow() != 1)
      throw new IllegalArgumentException("Unknown actual carriage restoration format");
    int yaw = tag.getInt("Yaw").orElseThrow();
    if (yaw < 0 || yaw >= 360 || yaw % 90 != 0)
      throw new IllegalArgumentException("Invalid actual carriage restoration yaw");
    boolean restored = tag.getBoolean("WorldRestored").orElseThrow();
    String field = restored ? "WrittenCells" : "TargetCells";
    if (tag.contains(restored ? "TargetCells" : "WrittenCells"))
      throw new IllegalArgumentException("Ambiguous actual carriage restoration custody");
    var state =
        new CarriageRestorationState(
            tag.read("Body", UUIDUtil.CODEC).orElseThrow(),
            dimensions.decode(tag.getInt("Dimension").orElseThrow()),
            new StructureTransform(tag.read("Anchor", BlockPos.CODEC).orElseThrow(), 0, yaw, 0),
            tag.read(field, BlockPos.CODEC.listOf()).orElseThrow());
    state.restored = restored;
    var journal = tag.read("WorldNotificationJournal", BlockPos.CODEC.listOf()).orElse(List.of());
    if (journal.size() > 1000000)
      throw new IllegalArgumentException("Actual carriage restoration journal too large");
    for (var pos : journal)
      if (!state.journal.add(pos.immutable()))
        throw new IllegalArgumentException("Duplicate actual world notification journal cell");
    return state;
  }
}
