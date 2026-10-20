package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

/** Only the client fluid chaser runs while mounted; boilers and connectivity remain world logic. */
public final class FluidTankMovementBehavior implements MovementBehaviour {
    @Override public boolean mustTickWhileDisabled() { return true; }
    @Override public void tick(MovementContext context) {
        if (context.world.isClientSide() && context.contraption.getBlockEntityClientSide(context.localPos) instanceof FluidTankBlockEntity tank)
            tank.tickFluidLevel();
    }
}
