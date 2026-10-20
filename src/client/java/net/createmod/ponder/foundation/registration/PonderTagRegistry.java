// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.TagRegistryAccess;
import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public class PonderTagRegistry implements TagRegistryAccess {

	private final PonderLocalization localization;
	private final Multimap<Identifier, Identifier> componentTagMap;
	private final Map<Identifier, PonderTag> registeredTags;
	private final List<PonderTag> listedTags;

	private PonderTag missing;

	private boolean allowRegistration = true;

	public PonderTagRegistry(PonderLocalization localization) {
		this.localization = localization;
		componentTagMap = LinkedHashMultimap.create();
		registeredTags = new HashMap<>();
		listedTags = new ArrayList<>();
	}

	public void clearRegistry() {
		componentTagMap.clear();
		registeredTags.clear();
		listedTags.clear();
		missing = null;
		allowRegistration = true;
	}

	//

	public void registerTag(PonderTag tag) {
		if (!allowRegistration)
			throw new IllegalStateException("Registration Phase has already ended!");

		registeredTags.put(tag.getId(), tag);
	}

	public void listTag(PonderTag tag) {
		if (!allowRegistration)
			throw new IllegalStateException("Registration Phase has already ended!");

		listedTags.add(tag);
	}

	public void addTagToComponent(Identifier tag, Identifier item) {
		if (!allowRegistration)
			throw new IllegalStateException("Registration Phase has already ended!");

		synchronized (componentTagMap) {
			componentTagMap.put(item, tag);
		}
	}

	//

	@Override
	public PonderTag getRegisteredTag(Identifier tagLocation) {
		PonderTag registered = registeredTags.get(tagLocation);
		return registered != null ? registered : missingTag();
	}

	private synchronized PonderTag missingTag() {
		// Registry construction also runs during early client init, before item components bind.
		// Build the original barrier icons only when the initialized UI requests a missing tag.
		if (missing == null)
			missing = new PonderTag(Ponder.asResource("not_registered"), null,
				Items.BARRIER.getDefaultInstance(), Items.BARRIER.getDefaultInstance());
		return missing;
	}

	@Override
	public List<PonderTag> getListedTags() {
		return listedTags;
	}

	@Override
	public Set<PonderTag> getTags(Identifier item) {
		return componentTagMap.get(item).stream().map(this::getRegisteredTag).collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public Set<Identifier> getItems(Identifier tag) {
		return componentTagMap.entries()
			.stream()
			.filter(e -> e.getValue().equals(tag))
			.map(Map.Entry::getKey)
			.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public Set<Identifier> getItems(PonderTag tag) {
		return getItems(tag.getId());
	}

}
