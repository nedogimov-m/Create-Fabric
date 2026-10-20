// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.element;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.world.item.ItemStack;

public interface InputElementBuilder {

	InputElementBuilder withItem(ItemStack stack);

	InputElementBuilder leftClick();

	InputElementBuilder rightClick();

	InputElementBuilder scroll();

	InputElementBuilder showing(ScreenElement icon);

	InputElementBuilder whileSneaking();

	InputElementBuilder whileCTRL();
}
