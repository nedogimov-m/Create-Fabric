// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.instruction;

import net.createmod.ponder.foundation.PonderScene;

public class KeyframeInstruction extends PonderInstruction {

	public static final KeyframeInstruction IMMEDIATE = new KeyframeInstruction(false);
	public static final KeyframeInstruction DELAYED = new KeyframeInstruction(true);

	private final boolean delayed;

	private KeyframeInstruction(boolean delayed) {
		this.delayed = delayed;
	}

	@Override
	public boolean isComplete() {
		return true;
	}

	@Override
	public void tick(PonderScene scene) {
	}

	@Override
	public void onScheduled(PonderScene scene) {
		scene.markKeyframe(delayed ? 6 : 0);
	}
}
