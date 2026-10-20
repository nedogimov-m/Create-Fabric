// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.element;

import net.createmod.ponder.api.PonderPalette;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public interface TextElementBuilder {

	TextElementBuilder colored(PonderPalette color);

	TextElementBuilder pointAt(Vec3 vec);

	TextElementBuilder independent(int y);

	default TextElementBuilder independent() {
		return independent(0);
	}

	TextElementBuilder text(String defaultText);

	TextElementBuilder text(String defaultText, Object... params);

	TextElementBuilder sharedText(Identifier key);

	TextElementBuilder sharedText(Identifier key, Object... params);

	TextElementBuilder sharedText(String key);

	TextElementBuilder sharedText(String key, Object... params);

	TextElementBuilder placeNearTarget();

	TextElementBuilder attachKeyFrame();
}
