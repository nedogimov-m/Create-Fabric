package com.simibubi.create.content.contraptions;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

/** One actual orientation convention shared by native rendering, collision and actor transforms. */
public final class ContraptionOrientation {
    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    public static Quaternionf source(float yaw, float pitch, float initialYaw) {
        finite(yaw); finite(pitch); finite(initialYaw);
        return new Quaternionf().rotateY(radians(-yaw)).rotateZ(radians(pitch)).rotateY(radians(initialYaw)).normalize();
    }
    public static Quaternionf controlled(float angle, Direction.Axis axis) {
        finite(angle);
        return new Quaternionf().rotationAxis(radians(angle), axis == Direction.Axis.X ? 1 : 0, axis == Direction.Axis.Y ? 1 : 0, axis == Direction.Axis.Z ? 1 : 0);
    }
    public static Vec3 apply(Quaternionfc rotation, Vec3 vector) {
        var result = rotation.transform(new Vector3d(vector.x, vector.y, vector.z));
        return new Vec3(result.x, result.y, result.z);
    }
    public static Vec3 inverse(Quaternionfc rotation, Vec3 vector) { return apply(new Quaternionf(rotation).conjugate(), vector); }
    public static float lerp(float previous, float value, float partial) { return previous + Mth.wrapDegrees(value - previous) * partial; }
    public static Quaternionf validated(Quaternionfc value) {
        if (!Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z()) || !Float.isFinite(value.w()) || value.lengthSquared() < 1e-12f) throw new IllegalArgumentException("Invalid contraption quaternion");
        return new Quaternionf(value).normalize();
    }
    private static void finite(float value) { if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite contraption angle"); }
    private ContraptionOrientation() {}
}
