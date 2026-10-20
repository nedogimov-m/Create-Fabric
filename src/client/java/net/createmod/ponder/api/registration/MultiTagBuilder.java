// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.registration;

import net.minecraft.resources.Identifier;

public interface MultiTagBuilder {

	interface Tag<T> {

		Tag<T> add(T component);

	}

	interface Component {

		Component add(Identifier tag);

	}

}
