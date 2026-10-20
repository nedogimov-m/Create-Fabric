// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.registration;

import java.util.function.Function;

import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.resources.Identifier;

public interface PonderSceneRegistrationHelper<T> {

	<S> PonderSceneRegistrationHelper<S> withKeyFunction(Function<S, T> keyGen);

	StoryBoardEntry addStoryBoard(T component, Identifier schematicLocation, PonderStoryBoard storyBoard,
								  Identifier... tags);

	StoryBoardEntry addStoryBoard(T component, String schematicPath, PonderStoryBoard storyBoard,
								  Identifier... tags);

	MultiSceneBuilder forComponents(T... components);

	MultiSceneBuilder forComponents(Iterable<? extends T> components);

	Identifier asLocation(String path);
}
