// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.registration;

import java.util.function.Consumer;

import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.resources.Identifier;

public interface MultiSceneBuilder {
	MultiSceneBuilder addStoryBoard(Identifier schematicLocation,
									PonderStoryBoard storyBoard);

	MultiSceneBuilder addStoryBoard(Identifier schematicLocation,
									PonderStoryBoard storyBoard, Identifier... tags);

	MultiSceneBuilder addStoryBoard(Identifier schematicLocation,
									PonderStoryBoard storyBoard,
									Consumer<StoryBoardEntry> extras);

	MultiSceneBuilder addStoryBoard(String schematicPath, PonderStoryBoard storyBoard);

	MultiSceneBuilder addStoryBoard(String schematicPath, PonderStoryBoard storyBoard,
									Identifier... tags);

	MultiSceneBuilder addStoryBoard(String schematicPath, PonderStoryBoard storyBoard,
									Consumer<StoryBoardEntry> extras);
}
