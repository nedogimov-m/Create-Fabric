package com.simibubi.create.content.contraptions.bearing;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Legacy shared day time, adapted to native world clocks with an explicit dimension policy. */
public final class ClockworkTime {
    private static Predicate<Level> normalRateDimension = level -> !level.dimensionType().hasFixedTime();
    public static void setNormalRateDimensionPredicate(Predicate<Level> predicate) { normalRateDimension = Objects.requireNonNull(predicate); }
    public static long time(Level level) { return cycleTime(level.getOverworldClockTime(), normalRateDimension.test(level)); }
    public static long cycleTime(long sharedTicks, boolean normalRate) {
        // Reduce before multiplication to avoid long overflow for large native clock totals.
        return Math.floorMod(Math.floorMod(sharedTicks, 24000) * (normalRate ? 1 : 24), 24000);
    }
    public static float hourTarget(long time, boolean cycle24, Direction facing) {
        int hours = ((int) Math.floorMod(time, 24000) / 1000 + 6) % 24;
        return facing.getAxisDirection().getStep() * -360f / (cycle24 ? 24 : 12) * (hours % (cycle24 ? 24 : 12));
    }
    public static float minuteTarget(long time, Direction facing) {
        int minutes = (int) (Math.floorMod(time, 1000) * 60 / 1000);
        return facing.getAxisDirection().getStep() * -360f / 60 * minutes;
    }
    public static float approachSpeed(float angle, float target, float maximum) {
        float difference = net.minecraft.util.Mth.wrapDegrees(target - angle);
        return Math.clamp(difference, -Math.abs(maximum), Math.abs(maximum));
    }
    private ClockworkTime() {}
}
