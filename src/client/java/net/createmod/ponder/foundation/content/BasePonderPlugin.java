// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.content;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;

public class BasePonderPlugin implements PonderPlugin {

	@Override
	public String getModId() {
		return Ponder.MOD_ID;
	}

	@Override
	public void registerSharedText(SharedTextRegistrationHelper helper) {
		helper.registerSharedText("sneak_and", "Sneak +");
		helper.registerSharedText("ctrl_and", "Ctrl +");
	}
}
