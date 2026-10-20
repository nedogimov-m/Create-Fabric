// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.scene;

import net.createmod.ponder.api.ParticleEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;

public interface EffectInstructions {
	void emitParticles(Vec3 location, ParticleEmitter emitter, float amountPerCycle, int cycles);

	<T extends ParticleOptions> ParticleEmitter simpleParticleEmitter(T data, Vec3 motion);

	<T extends ParticleOptions> ParticleEmitter particleEmitterWithinBlockSpace(T data, Vec3 motion);

	void indicateRedstone(BlockPos pos);

	void indicateSuccess(BlockPos pos);

	void createRedstoneParticles(BlockPos pos, int color, int amount);
}
