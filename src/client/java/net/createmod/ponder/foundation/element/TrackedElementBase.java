// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.element;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import net.createmod.ponder.api.element.TrackedElement;
import net.createmod.ponder.api.level.PonderLevel;

public abstract class TrackedElementBase<T> extends PonderElementBase implements TrackedElement<T> {

	private final WeakReference<T> reference;

	public TrackedElementBase(T wrapped) {
		this.reference = new WeakReference<>(wrapped);
	}

	@Override
	public void ifPresent(Consumer<T> func) {
		T resolved = reference.get();
		if (resolved == null)
			return;
		func.accept(resolved);
	}

}
