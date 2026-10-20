// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.registration;

import java.util.List;
import java.util.Set;

import net.createmod.ponder.foundation.PonderTag;
import net.minecraft.resources.Identifier;

public interface TagRegistryAccess {

	PonderTag getRegisteredTag(Identifier tagLocation);

	List<PonderTag> getListedTags();

	Set<PonderTag> getTags(Identifier item);

	Set<Identifier> getItems(Identifier tag);

	Set<Identifier> getItems(PonderTag tag);

}
