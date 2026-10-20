package com.simibubi.create.content.trains.graph;

import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

/**
 * Source curve/intersection math, separated from client rendering and legacy Catnip world helpers.
 */
public final class RailMath {
  public static Vec3 lerp(double t, Vec3 from, Vec3 to) {
    return from.add(to.subtract(from).scale(t));
  }

  public static Vec3 bezier(Vec3 p1, Vec3 p2, Vec3 q1, Vec3 q2, float t) {
    var a = lerp(t, p1, q1);
    var b = lerp(t, q1, q2);
    var c = lerp(t, q2, p2);
    return lerp(t, lerp(t, a, b), lerp(t, b, c));
  }

  public static Vec3 bezierDerivative(Vec3 p1, Vec3 p2, Vec3 q1, Vec3 q2, float t) {
    return p1.scale(-3 * t * t + 6 * t - 3)
        .add(q1.scale(9 * t * t - 12 * t + 3))
        .add(q2.scale(-9 * t * t + 6 * t))
        .add(p2.scale(3 * t * t));
  }

  public static Vec3 slerp(float t, Vec3 from, Vec3 to) {
    double dot = Math.clamp(from.normalize().dot(to.normalize()), -1, 1);
    if (dot > 1 - 1e-8) return lerp(t, from, to).normalize();
    if (dot < -1 + 1e-8) {
      var axis =
          from.cross(Math.abs(from.x) < .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
      return from.normalize().scale(Math.cos(Math.PI * t)).add(axis.scale(Math.sin(Math.PI * t)));
    }
    double theta = Math.acos(dot), sin = Math.sin(theta);
    return from.scale(Math.sin((1 - t) * theta) / sin).add(to.scale(Math.sin(t * theta) / sin));
  }

  public static double[] intersect(Vec3 p1, Vec3 p2, Vec3 r, Vec3 s, Axis plane) {
    if (plane == Axis.X) {
      p1 = new Vec3(p1.y, 0, p1.z);
      p2 = new Vec3(p2.y, 0, p2.z);
      r = new Vec3(r.y, 0, r.z);
      s = new Vec3(s.y, 0, s.z);
    } else if (plane == Axis.Z) {
      p1 = new Vec3(p1.x, 0, p1.y);
      p2 = new Vec3(p2.x, 0, p2.y);
      r = new Vec3(r.x, 0, r.y);
      s = new Vec3(s.x, 0, s.y);
    }
    var delta = p2.subtract(p1);
    double cross = r.x * s.z - r.z * s.x;
    if (Math.abs(cross) < 1e-8) return null;
    return new double[] {
      (delta.x * s.z - delta.z * s.x) / cross, (delta.x * r.z - delta.z * r.x) / cross
    };
  }

  public static double[] intersectRanged(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2, Axis plane) {
    var p = p2.subtract(p1);
    var q = q2.subtract(q1);
    var intersection = intersect(p1, q1, p.normalize(), q.normalize(), plane);
    return intersection == null
            || intersection[0] < 0
            || intersection[1] < 0
            || intersection[0] * intersection[0] > p.lengthSqr()
            || intersection[1] * intersection[1] > q.lengthSqr()
        ? null
        : intersection;
  }

  private RailMath() {}
}
