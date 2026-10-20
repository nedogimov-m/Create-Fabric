// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.element;

import net.minecraft.world.phys.Vec3;

public interface AnimatedSceneElement extends PonderSceneElement {
	void forceApplyFade(float fade);

	void setFade(float fade);

	void setFadeVec(Vec3 fadeVec);
}
