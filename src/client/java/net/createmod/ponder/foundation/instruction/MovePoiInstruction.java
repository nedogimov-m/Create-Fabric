// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.instruction;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.phys.Vec3;

public class MovePoiInstruction extends PonderInstruction {

	private final Vec3 poi;

	public MovePoiInstruction(Vec3 poi) {
		this.poi = poi;
	}

	@Override
	public boolean isComplete() {
		return true;
	}

	@Override
	public void tick(PonderScene scene) {
		scene.setPointOfInterest(poi);
	}

}
