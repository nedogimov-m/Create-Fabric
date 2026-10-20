// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.registration;

import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.Identifier;

public class GenericMultiTagBuilder<T> implements MultiTagBuilder {

	private PonderTagRegistrationHelper<T> helper;

	public class Tag implements MultiTagBuilder.Tag<T> {

		Iterable<Identifier> tags;

		public Tag(PonderTagRegistrationHelper<T> helper, Iterable<Identifier> tags) {
			GenericMultiTagBuilder.this.helper = helper;
			this.tags = tags;
		}

		@Override
		public Tag add(T component) {
			tags.forEach(tag -> helper.addTagToComponent(component, tag));
			return this;
		}
	}

	public class Component implements MultiTagBuilder.Component {

		Iterable<T> components;

		public Component(PonderTagRegistrationHelper<T> helper, Iterable<T> components) {
			GenericMultiTagBuilder.this.helper = helper;
			this.components = components;
		}

		@Override
		public Component add(Identifier tag) {
			components.forEach(component -> helper.addTagToComponent(component, tag));
			return this;
		}
	}

}
