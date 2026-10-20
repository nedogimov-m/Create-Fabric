package com.simibubi.create.content.kinetics.fan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public interface IAirCurrentSource {
    AirCurrent getAirCurrent();
    Level getAirCurrentWorld();
    BlockPos getAirCurrentPos();
    float getSpeed();
    Direction getAirflowOriginSide();
    Direction getAirFlowDirection();
    /** Pinned interpolation uses the signed source speed to choose push/pull limits. */
    default float getMaxDistance() { var config=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics;float target=getSpeed()>0?config.fanPushDistance.get():config.fanPullDistance.get();return 3+Math.min(Math.abs(getSpeed())/config.fanRotationArgmax.get(),1)*(target-3); }
    boolean isSourceRemoved();
}
