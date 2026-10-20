package com.simibubi.create.foundation.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Source hot cubes resume native collision movement while billowing along a ceiling. */
@Mixin(Particle.class)
public interface CarriageParticleCollisionAccessor {
  @Accessor("stoppedByCollision")
  void create$setStoppedByCollision(boolean stopped);
}
