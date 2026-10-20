package com.simibubi.create.foundation.utility;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;

/** Current unconsumed motion continuation of one real stabilized child and its actual parent. */
public record StabilizedParentMotion(UUID parent,Vec3 motion) {
    public static final String KEY="StabilizedParentMotion";
    public static final Codec<StabilizedParentMotion> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Parent").forGetter(StabilizedParentMotion::parent),Vec3.CODEC.fieldOf("Motion").forGetter(StabilizedParentMotion::motion)).apply(i,StabilizedParentMotion::new));
    public StabilizedParentMotion{Objects.requireNonNull(parent);Objects.requireNonNull(motion);if(!Double.isFinite(motion.x)||!Double.isFinite(motion.y)||!Double.isFinite(motion.z))throw new IllegalArgumentException("Nonfinite actual stabilized-parent motion");}
}
