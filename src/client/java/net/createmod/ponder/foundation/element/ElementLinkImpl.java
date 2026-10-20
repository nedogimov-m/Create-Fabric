// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.element;

import java.util.UUID;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.PonderElement;

public class ElementLinkImpl<T extends PonderElement> implements ElementLink<T> {

	private final Class<T> elementClass;
	private final UUID id;

	public ElementLinkImpl(Class<T> elementClass) {
		this(elementClass, UUID.randomUUID());
	}

	public ElementLinkImpl(Class<T> elementClass, UUID id) {
		this.elementClass = elementClass;
		this.id = id;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public T cast(PonderElement e) {
		return elementClass.cast(e);
	}

}
