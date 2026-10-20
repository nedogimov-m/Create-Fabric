// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.instruction;

public class DelayInstruction extends TickingInstruction {

	public DelayInstruction(int ticks) {
		super(true, ticks);
	}

}
