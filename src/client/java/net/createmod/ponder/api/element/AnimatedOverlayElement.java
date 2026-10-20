// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.element;

public interface AnimatedOverlayElement extends PonderOverlayElement {

	void setFade(float fade);

	float getFade(float partialTicks);

}
