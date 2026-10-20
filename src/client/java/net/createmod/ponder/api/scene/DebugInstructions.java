// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.scene;

import java.util.function.Consumer;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;

public interface DebugInstructions {
	void debugSchematic();

	void addInstructionInstance(PonderInstruction instruction);

	void enqueueCallback(Consumer<PonderScene> callback);
}
