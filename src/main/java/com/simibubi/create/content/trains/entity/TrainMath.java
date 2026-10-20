package com.simibubi.create.content.trains.entity;

import net.minecraft.world.phys.Vec3;

/** Actual pinned Catnip line/sphere intersection, with native Vec3 and no world/renderer access. */
final class TrainMath {
    static Vec3 intersectSphere(Vec3 origin,Vec3 lineDirection,Vec3 sphereCenter,double radius){if(lineDirection.equals(Vec3.ZERO))return null;if(lineDirection.lengthSqr()!=1)lineDirection=lineDirection.normalize();var diff=origin.subtract(sphereCenter);double lineDotDiff=lineDirection.dot(diff);double delta=lineDotDiff*lineDotDiff-(diff.lengthSqr()-radius*radius);if(delta<0)return null;double t=-lineDotDiff+Math.sqrt(delta);return origin.add(lineDirection.scale(t));}
    private TrainMath(){}
}
