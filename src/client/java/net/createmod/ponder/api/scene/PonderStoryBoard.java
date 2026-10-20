// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.scene;

@FunctionalInterface
public interface PonderStoryBoard {
	void program(SceneBuilder scene, SceneBuildingUtil util);
}
