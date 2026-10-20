package com.simibubi.create.test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Actual native full-save teardown after each scene has confirmed native FULL chunk absence. */
final class NativeFixtureChunkUnload {
  static void finish(ClientGameTestContext context, TestSingleplayerContext world,
      BlockEntity... originals) {
    var level = world.getServer().computeOnServer(server -> world.getConnection().getServerLevel());
    finish(context, world, level, originals);
  }
  static void finish(ClientGameTestContext context, TestSingleplayerContext world,
      ServerLevel level, BlockEntity... originals) {
    if (originals.length == 0) throw new IllegalArgumentException("No original native BE references");
    for (var original : originals) Objects.requireNonNull(original, "Missing original fixture BE");
    var watch = world.getServer().computeOnServer(server ->
        NativeRedstoneUnloadWatch.capture(level, originals));
    world.getServer().runOnServer(server -> {
      if (level.getServer() != server)
        throw new AssertionError("Explicit fixture dimension belongs to another native server");
      for (var original : originals) {
        var pos = original.getBlockPos();
        if (original.getLevel() != level
            || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null)
          throw new AssertionError("Native FULL absence must precede native save/flush");
      }
      watch.sample(level, "fixture-before-flush", 0, "native FULL absence confirmed");
      if (!server.saveEverything(true, true, true))
        throw new AssertionError("Actual native fixture world save/flush was refused");
      watch.sample(level, "fixture-after-flush", 0, "public native saveEverything returned");
    });
    var removed = new AtomicBoolean();
    for (int attempt = 0; attempt < 800 && !removed.get(); attempt++) {
      int tick = attempt;
      world.getServer().runOnServer(server -> {
        boolean complete = true;
        for (var original : originals) {
          var pos = original.getBlockPos();
          complete &= original.isRemoved()
              && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null;
        }
        removed.set(complete);
        if (tick % 100 == 0 || tick == 799 || complete)
          watch.sample(level, "fixture-retirement", tick, "strict original removal AND FULL absence=" + complete);
        if (!complete && tick > 0 && tick % 100 == 0
            && java.util.Arrays.stream(originals).allMatch(original -> {
              var pos = original.getBlockPos();
              return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null;
            }) && watch.hasCompletedPendingRetirement(level)) {
          // saveAllChunks(true) drains the unload queue once, then synchronizes storage.
          // A save future may enqueue retirement after that drain. Only after observing
          // an actual completed dependency, repeat the public save API on this level.
          // The ordinary server pump, queue, original BEs and strict assertions remain native.
          watch.sample(level, "fixture-before-repeat-flush", tick, "completed pending save dependency");
          level.getChunkSource().save(true);
          watch.sample(level, "fixture-after-repeat-flush", tick, "public native source.save(true) returned");
        }
      });
      if (!removed.get()) context.waitTicks(1);
    }
    if (!removed.get())
      throw new AssertionError("Native teardown did not complete original BE removal AND continuing FULL absence; see per-original fixture-retirement diagnostics");
    context.waitFor(client -> {
      // The client has one active native ClientLevel; a dimension switch retires
      // the other dimension's active chunk cache. Server retirement above remains
      // required for every explicit real dimension, including an inactive one.
      if (!client.level.dimension().equals(level.dimension())) return true;
      for (var original : originals)
        if (client.level.isLoaded(original.getBlockPos())) return false;
      return true;
    }, 800);
  }
  private NativeFixtureChunkUnload() {}
}
