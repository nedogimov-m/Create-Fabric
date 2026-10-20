// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.instruction;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.phys.Vec3;

public class EmitParticlesInstruction extends TickingInstruction {

	private final Vec3 anchor;
	private final ParticleEmitter emitter;
	private final float runsPerTick;

	public EmitParticlesInstruction(Vec3 anchor, ParticleEmitter emitter, float runsPerTick, int ticks) {
		super(false, ticks);
		this.anchor = anchor;
		this.emitter = emitter;
		this.runsPerTick = runsPerTick;
	}

	@Override
	public void tick(PonderScene scene) {
		super.tick(scene);
		int runs = (int) runsPerTick;
		if (Ponder.RANDOM.nextFloat() < (runsPerTick - runs))
			runs++;
		for (int i = 0; i < runs; i++)
			emitter.create(scene.getWorld(), anchor.x, anchor.y, anchor.z);
	}

}
