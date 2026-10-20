package net.createmod.ponder.mixin.client;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Particle.class)
public interface PonderParticleAccessor {
  @Accessor("level") ClientLevel ponder$world();
}
