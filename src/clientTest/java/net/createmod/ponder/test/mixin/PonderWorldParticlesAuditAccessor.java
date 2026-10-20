package net.createmod.ponder.test.mixin;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderWorldParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PonderLevel.class, remap = false)
public interface PonderWorldParticlesAuditAccessor {
    @Accessor("particles") PonderWorldParticles create$actualLocalParticles();
}
