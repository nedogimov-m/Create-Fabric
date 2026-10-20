package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Real moving mechanical bearing top; common code never depends on a renderer. */
public final class StabilizedBearingMovementBehaviour implements MovementBehaviour {
    @Override public ItemStack canBeDisabledVia(MovementContext context) { return null; }
    @Override public boolean disableBlockEntityRendering() { return true; }
    public static float getCounterRotationAngle(MovementContext context, Direction facing, float partial) {
        if (!context.contraption.canBeStabilized(facing, context.localPos)) return 0;
        var parent = context.contraption.entity;
        if (parent instanceof ControlledContraptionEntity controlled) return -controlled.getAngle(partial);
        if (parent instanceof OrientedContraptionEntity oriented) {
            if (facing.getAxis().isVertical()) return -oriented.getViewYRot(partial);
            if (oriented.isInitialOrientationPresent() && oriented.getInitialOrientation().getAxis() == facing.getAxis()) return -oriented.getViewXRot(partial);
        }
        return 0;
    }
}
