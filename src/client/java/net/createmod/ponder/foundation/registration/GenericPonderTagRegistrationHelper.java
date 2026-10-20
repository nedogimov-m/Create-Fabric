// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.registration;

import java.util.List;
import java.util.function.Function;

import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.TagBuilder;
import net.minecraft.resources.Identifier;

public class GenericPonderTagRegistrationHelper<T> implements PonderTagRegistrationHelper<T> {

	private final PonderTagRegistrationHelper<Identifier> helperDelegate;
	private final Function<T, Identifier> keyGen;

	public GenericPonderTagRegistrationHelper(PonderTagRegistrationHelper<Identifier> helperDelegate,
											  Function<T, Identifier> keyGen) {
		this.helperDelegate = helperDelegate;
		this.keyGen = keyGen;
	}

	@Override
	public <S> PonderTagRegistrationHelper<S> withKeyFunction(Function<S, T> keyGen) {
		return new GenericPonderTagRegistrationHelper<>(helperDelegate, keyGen.andThen(this.keyGen));
	}

	@Override
	public TagBuilder registerTag(Identifier location) {
		return helperDelegate.registerTag(location);
	}

	@Override
	public TagBuilder registerTag(String id) {
		return helperDelegate.registerTag(id);
	}

	@Override
	public void addTagToComponent(T component, Identifier tag) {
		helperDelegate.addTagToComponent(keyGen.apply(component), tag);
	}

	@Override
	public MultiTagBuilder.Tag<T> addToTag(Identifier tag) {
		return new GenericMultiTagBuilder<T>().new Tag(this, List.of(tag));
	}

	@Override
	public MultiTagBuilder.Tag<T> addToTag(Identifier... tags) {
		return new GenericMultiTagBuilder<T>().new Tag(this, List.of(tags));
	}

	@Override
	public MultiTagBuilder.Component addToComponent(T component) {
		return new GenericMultiTagBuilder<T>().new Component(this, List.of(component));
	}

	@Override
	public MultiTagBuilder.Component addToComponent(T... components) {
		return new GenericMultiTagBuilder<T>().new Component(this, List.of(components));
	}
}
