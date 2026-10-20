package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Native actual ticking readiness; called by real front ticks and global carriage management. */
public final class CarriageEntityHandler {
  public static void validateCarriageEntity(CarriageContraptionEntity entity) {
    if (!entity.level().isClientSide()
        && entity.isAlive()
        && !isActiveChunk(entity.level(), entity.blockPosition())) entity.leftTickingChunks = true;
  }

  public static boolean isActiveChunk(Level level, BlockPos pos) {
    return level instanceof ServerLevel server
        && LoadedEntityAccess.isReady(server, pos)
        && server.isPositionEntityTicking(pos);
  }

  private CarriageEntityHandler() {}
}
