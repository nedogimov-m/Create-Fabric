// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.instruction;

import net.createmod.ponder.api.element.ParrotElement;
import net.minecraft.core.Direction;

public class CreateParrotInstruction extends FadeIntoSceneInstruction<ParrotElement> {

	public CreateParrotInstruction(int fadeInTicks, Direction fadeInFrom, ParrotElement element) {
		super(fadeInTicks, fadeInFrom, element);
	}

	@Override
	protected Class<ParrotElement> getElementClass() {
		return ParrotElement.class;
	}

}
