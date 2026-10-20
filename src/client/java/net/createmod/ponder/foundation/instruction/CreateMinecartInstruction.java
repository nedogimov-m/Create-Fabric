// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.instruction;

import net.createmod.ponder.api.element.MinecartElement;
import net.minecraft.core.Direction;

public class CreateMinecartInstruction extends FadeIntoSceneInstruction<MinecartElement> {

	public CreateMinecartInstruction(int fadeInTicks, Direction fadeInFrom, MinecartElement element) {
		super(fadeInTicks, fadeInFrom, element);
	}

	@Override
	protected Class<MinecartElement> getElementClass() {
		return MinecartElement.class;
	}

}
