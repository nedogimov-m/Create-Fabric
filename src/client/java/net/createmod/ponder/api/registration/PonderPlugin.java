// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.registration;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.resources.Identifier;

public interface PonderPlugin {

	/**
	 * @return the modID of the mod that added this plugin
	 */
	String getModId();

	/**
	 * Register all the Ponder Scenes added by your Mod
	 */
	default void registerScenes(PonderSceneRegistrationHelper<Identifier> helper) {
	}

	/**
	 * Register all the Ponder Tags added by your Mod
	 */
	default void registerTags(PonderTagRegistrationHelper<Identifier> helper) {
	}

	default void registerSharedText(SharedTextRegistrationHelper helper) {
	}

	default void onPonderLevelRestore(PonderLevel ponderLevel) {
	}

	default void indexExclusions(IndexExclusionHelper helper) {
	}

}
