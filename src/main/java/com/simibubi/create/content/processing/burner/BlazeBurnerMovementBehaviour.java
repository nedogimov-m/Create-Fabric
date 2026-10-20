package com.simibubi.create.content.processing.burner;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class BlazeBurnerMovementBehaviour implements MovementBehaviour {
    public static Consumer<MovementContext> CLIENT_TICK = context -> {};
    @Override public ItemStack canBeDisabledVia(MovementContext context) { return null; }
    @Override public boolean disableBlockEntityRendering() { return true; }
    @Override public void tick(MovementContext context) { if (context.world.isClientSide()) CLIENT_TICK.accept(context); }
    public static boolean shouldRender(MovementContext context) {
        return context.state.getOptionalValue(BlazeBurnerBlock.HEAT_LEVEL).orElse(BlazeBurnerBlock.HeatLevel.NONE) != BlazeBurnerBlock.HeatLevel.NONE;
    }
    public static HeadAngle headAngle(MovementContext context, float target) {
        if (!(context.temporaryData instanceof HeadAngle)) context.temporaryData = new HeadAngle(target);
        return (HeadAngle) context.temporaryData;
    }
    /** Exponential angular chase with a continuous representation across the +/-180 seam. */
    public static final class HeadAngle {
        private float previous, value;
        public HeadAngle(float value) { this.previous = this.value = value; }
        public void tick(float target) { previous = value; value += Math.clamp(Mth.wrapDegrees(target - value) * .5f, -5, 5); }
        public float getValue(float partial) { return previous + (value - previous) * partial; }
    }
}
