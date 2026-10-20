package net.createmod.ponder.mixin.client;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ParticleEngine.class)
public interface PonderParticleEngineAccessor {
  @Accessor("resourceManager") ParticleResources ponder$resources();
}
