// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.api.registration;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.resources.Identifier;

public interface SceneRegistryAccess {

	boolean doScenesExistForId(Identifier id);

	Collection<Map.Entry<Identifier, StoryBoardEntry>> getRegisteredEntries();

	List<PonderScene> compile(Identifier id);

	List<PonderScene> compile(Collection<StoryBoardEntry> entries);

}
