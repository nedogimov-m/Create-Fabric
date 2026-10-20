// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api;

import net.createmod.ponder.api.level.PonderLevel;

@FunctionalInterface
public interface ParticleEmitter {
	void create(PonderLevel world, double x, double y, double z);
}
