package com.simibubi.create.content.legacy;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Exact pinned Catnip offsetRandomly float sampling, including draw order. */
final class LegacyRandom {
 static Vec3 offset(Vec3 value,RandomSource random,float radius){return new Vec3(value.x+(random.nextFloat()-.5f)*2*radius,value.y+(random.nextFloat()-.5f)*2*radius,value.z+(random.nextFloat()-.5f)*2*radius);}
 private LegacyRandom(){}
}
