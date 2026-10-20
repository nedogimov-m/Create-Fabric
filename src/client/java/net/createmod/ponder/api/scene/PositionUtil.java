// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.scene;

import net.minecraft.core.BlockPos;

public interface PositionUtil {
	BlockPos at(int x, int y, int z);

	BlockPos zero();
}
