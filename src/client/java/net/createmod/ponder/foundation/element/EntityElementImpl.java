// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.element;

import net.createmod.ponder.api.element.EntityElement;
import net.minecraft.world.entity.Entity;

public class EntityElementImpl extends TrackedElementBase<Entity> implements EntityElement {

	public EntityElementImpl(Entity wrapped) {
		super(wrapped);
	}

	@Override
	public boolean isStillValid(Entity element) {
		return element.isAlive();
	}

}
