// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.element;

import net.createmod.ponder.foundation.PonderScene;

public interface PonderElement {
	default void whileSkipping(PonderScene scene) {
	}

	default void tick(PonderScene scene) {
	}

	default void reset(PonderScene scene) {
	}

	boolean isVisible();

	void setVisible(boolean visible);
}
