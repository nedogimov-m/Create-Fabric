// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.registration;

import java.util.function.BiConsumer;

import net.minecraft.resources.Identifier;

public interface LangRegistryAccess {

	/**
	 * Generate all Lang-entries with their enUS defaults that were declared in code and have them consumed by the passed BiConsumer
	 *
	 * @param modId the ModId (or namespace) that you want to collect the lang entries for
	 */
	void provideLang(String modId, BiConsumer<String, String> consumer);

	String getShared(Identifier key);

	String getShared(Identifier key, Object... params);

	String getTagName(Identifier key);

	String getTagDescription(Identifier key);

	String getSpecific(Identifier sceneId, String k);

	String getSpecific(Identifier sceneId, String k, Object... params);

}
