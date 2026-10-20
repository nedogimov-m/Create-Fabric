// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.registration;

import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.Identifier;

public class DefaultSharedTextRegistrationHelper implements SharedTextRegistrationHelper {

	private final String namespace;
	private final PonderLocalization localization;

	public DefaultSharedTextRegistrationHelper(String namespace, PonderLocalization localization) {
		this.namespace = namespace;
		this.localization = localization;
	}

	@Override
	public void registerSharedText(String key, String en_us) {
		localization.registerShared(Identifier.fromNamespaceAndPath(namespace, key), en_us);
	}
}
