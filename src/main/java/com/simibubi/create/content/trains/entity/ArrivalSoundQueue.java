package com.simibubi.create.content.trains.entity;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.WhistleSize;
import java.util.*;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jspecify.annotations.Nullable;

/**
 * Source carriage arrival sequence on an actual native contraption owner. No train is synthesized.
 */
public final class ArrivalSoundQueue {
  public int offset;
  private int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
  private final Map<Integer, List<BlockPos>> sources = new LinkedHashMap<>();

  public @Nullable Integer firstTick() {
    return sources.isEmpty() ? null : Math.addExact(min, offset);
  }

  public @Nullable Integer lastTick() {
    return sources.isEmpty() ? null : Math.addExact(max, offset);
  }

  public boolean tick(AbstractContraptionEntity entity, int tick, boolean backwards) {
    int localTick = Math.subtractExact(tick, offset);
    var entries = sources.get(localTick);
    if (entries != null)
      for (var pos : entries) play(entity, entity.getContraption().getBlocks().get(pos));
    return backwards ? localTick > min : localTick < max;
  }

  public @Nullable Pair<Boolean, Integer> getFirstWhistle(AbstractContraptionEntity entity) {
    for (var entry : sources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
      for (var pos : entry.getValue()) {
        var info = entity.getContraption().getBlocks().get(pos);
        if (info == null
            || !(info.state().getBlock() instanceof WhistleBlock)
            || info.nbt() == null) continue;
        int pitch = info.nbt().getIntOr("Pitch", 0);
        var size = info.state().getValue(WhistleBlock.SIZE);
        return Pair.of(size == WhistleSize.LARGE, (size == WhistleSize.SMALL ? 12 : 0) - pitch);
      }
    }
    return null;
  }

  public void add(int tick, BlockPos localPos) {
    sources.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(localPos.immutable());
    min = Math.min(min, tick);
    max = Math.max(max, tick);
  }

  public Map<Integer, List<BlockPos>> sources() {
    var copied = new LinkedHashMap<Integer, List<BlockPos>>();
    sources.forEach((tick, list) -> copied.put(tick, List.copyOf(list)));
    return Collections.unmodifiableMap(copied);
  }

  public void serialize(CompoundTag parent) {
    var data = new CompoundTag();
    data.putInt("Offset", offset);
    var entries = new ListTag();
    sources.forEach(
        (tick, list) ->
            list.forEach(
                pos -> {
                  var entry = new CompoundTag();
                  entry.putInt("Tick", tick);
                  entry.store("Pos", BlockPos.CODEC, pos);
                  entries.add(entry);
                }));
    data.put("Sources", entries);
    parent.put("SoundQueue", data);
  }

  public void deserialize(CompoundTag parent) {
    var data = parent.getCompoundOrEmpty("SoundQueue");
    var decoded = new LinkedHashMap<Integer, List<BlockPos>>();
    int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
    for (var value : data.getListOrEmpty("Sources")) {
      if (!(value instanceof CompoundTag entry) || !entry.contains("Tick"))
        throw new IllegalArgumentException("Invalid actual arrival sound source");
      int tick = entry.getIntOr("Tick", 0);
      var pos = readPosition(entry);
      decoded.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(pos);
      first = Math.min(first, tick);
      last = Math.max(last, tick);
    }
    int shift = data.getIntOr("Offset", 0);
    if (!decoded.isEmpty()) {
      Math.addExact(first, shift);
      Math.addExact(last, shift);
    }
    sources.clear();
    sources.putAll(decoded);
    min = first;
    max = last;
    offset = shift;
  }

  private static BlockPos readPosition(CompoundTag entry) {
    if (entry.get("Pos") instanceof CompoundTag legacy) {
      if (!legacy.contains("X") || !legacy.contains("Y") || !legacy.contains("Z"))
        throw new IllegalArgumentException("Incomplete source arrival position");
      return new BlockPos(
          legacy.getIntOr("X", 0), legacy.getIntOr("Y", 0), legacy.getIntOr("Z", 0));
    }
    return entry.read("Pos", BlockPos.CODEC).orElseThrow();
  }

  public static boolean isPlayable(BlockState state) {
    return state.getBlock() instanceof BellBlock
        || state.getBlock() instanceof NoteBlock
        || state.getBlock() instanceof WhistleBlock;
  }

  public static void play(AbstractContraptionEntity entity, @Nullable StructureBlockInfo info) {
    if (info == null) return;
    var state = info.state();
    if (state.getBlock() instanceof BellBlock)
      playSimple(
          entity,
          state.is(AllBlocks.HAUNTED_BELL)
              ? AllSoundEvents.HAUNTED_BELL_USE
              : SoundEvents.BELL_BLOCK,
          1,
          1);
    if (state.getBlock() instanceof NoteBlock) {
      float pitch = (float) Math.pow(2, (state.getValue(NoteBlock.NOTE) - 12) / 12d);
      playSimple(entity, state.getValue(NoteBlock.INSTRUMENT).getSoundEvent().value(), 1, pitch);
    }
    if (state.getBlock() instanceof WhistleBlock && info.nbt() != null) {
      var size = state.getValue(WhistleBlock.SIZE);
      int pitch = info.nbt().getIntOr("Pitch", 0);
      // These genuine source SoundEvents must be registered by the carriage integration patch.
      var id = Create.asResource(size == WhistleSize.LARGE ? "whistle_train_low" : "whistle_train");
      if (!BuiltInRegistries.SOUND_EVENT.containsKey(id))
        throw new IllegalStateException("Actual source carriage whistle is not registered: " + id);
      var sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
      playSimple(
          entity,
          sound,
          1,
          (float) Math.pow(2, ((size == WhistleSize.SMALL ? 12 : 0) - pitch) / 12d));
    }
  }

  private static void playSimple(
      AbstractContraptionEntity entity, SoundEvent sound, float volume, float pitch) {
    entity
        .level()
        .playSound(
            null,
            entity.getX(),
            entity.getY(),
            entity.getZ(),
            sound,
            SoundSource.NEUTRAL,
            5 * volume,
            pitch);
  }
}
