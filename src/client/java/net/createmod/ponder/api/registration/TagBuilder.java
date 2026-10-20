// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.registration;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

public interface TagBuilder {

	TagBuilder title(String title);

	TagBuilder description(String description);

	TagBuilder addToIndex();

	TagBuilder icon(Identifier location);

	TagBuilder icon(String path);

	TagBuilder idAsIcon();

	TagBuilder item(ItemLike item, boolean useAsIcon, boolean useAsMainItem);

	default TagBuilder item(ItemLike item) {
		return item(item, true, true);
	}

	void register();

}
