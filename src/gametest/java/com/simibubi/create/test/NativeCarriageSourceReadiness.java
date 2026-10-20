package com.simibubi.create.test;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Collection;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/** Pure actual source/query preparation. No entity state, tickets or production guards change. */
public final class NativeCarriageSourceReadiness {
  public static void require(GameTestHelper helper, Collection<BlockPos> relativeSourceCells) {
    var level = helper.getLevel();
    var checked = new HashSet<Long>();
    try {
      for (var relative : relativeSourceCells) {
        var source = helper.absolutePos(relative);
        LoadedEntityAccess.requireReady(level, source);
        // Use the actual production native-query union, including each currently FULL column
        // up to32blocks from every source cell, rather than only the bogey/wheel chunk.
        LoadedEntityAccess.requireGlueQueriesReady(level, source, checked);
        for (var direction : Direction.values()) {
          var neighbor = source.relative(direction);
          helper.assertTrue(
              LoadedLevelAccess.isLoaded(level, neighbor),
              "Actual source neighbor terrain is naturally available at " + neighbor);
          var state = LoadedLevelAccess.blockState(level, neighbor);
          if (BlockMovementChecks.isMovementNecessary(state, level, neighbor))
            LoadedEntityAccess.requireReady(level, neighbor);
        }
      }
    } catch (AssemblyException preparation) {
      // These two production require methods report actual native metadata/query visibility
      // only. The GameTest preparation sequence retries this ordinary assertion next tick.
      helper.fail("Actual native source/glue query preparation is pending: "
          + preparation.component.getString());
    }
  }

  private NativeCarriageSourceReadiness() {}
}
