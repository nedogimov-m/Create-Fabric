// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.registration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import net.createmod.catnip.data.Couple;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.LangRegistryAccess;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

public class PonderLocalization implements LangRegistryAccess {

	public static final String LANG_PREFIX = "ponder.";
	public static final String UI_PREFIX = "ui.";

	public final Map<Identifier, String> shared = new HashMap<>();
	public final Map<Identifier, Couple<String>> tag = new HashMap<>();
	public final Map<Identifier, Map<String, String>> specific = new HashMap<>();

	//

	public void clearAll() {
		shared.clear();
		tag.clear();
		specific.clear();
	}

	public void clearShared() {
		shared.clear();
	}

	public void registerShared(Identifier key, String enUS) {
		shared.put(key, enUS);
	}

	public void registerTag(Identifier key, String title, String description) {
		tag.put(key, Couple.create(title, description));
	}

	public void registerSpecific(Identifier sceneId, String key, String enUS) {
		specific.computeIfAbsent(sceneId, $ -> new HashMap<>())
			.put(key, enUS);
	}

	//

	protected static String langKeyForShared(Identifier k) {
		return k.getNamespace() + "." + LANG_PREFIX + "shared." + k.getPath();
	}

	protected static String langKeyForTag(Identifier k) {
		return k.getNamespace() + "." + LANG_PREFIX + "tag." + k.getPath();
	}

	protected static String langKeyForTagDescription(Identifier k) {
		return k.getNamespace() + "." + LANG_PREFIX + "tag." + k.getPath() + ".description";
	}

	protected static String langKeyForSpecific(Identifier sceneId, String k) {
		return sceneId.getNamespace() + "." + LANG_PREFIX + sceneId.getPath() + "." + k;
	}

	//

	@Override
	public String getShared(Identifier key) {
		if (PonderIndex.editingModeActive())
			return shared.containsKey(key) ? shared.get(key) : ("unregistered shared entry: " + key);
		return I18n.get(langKeyForShared(key));
	}

	@Override
	public String getShared(Identifier key, Object... params) {
		if (PonderIndex.editingModeActive())
			return shared.containsKey(key) ? String.format(shared.get(key), params) : ("unregistered shared entry: " + key);
		return I18n.get(langKeyForShared(key), params);
	}

	@Override
	public String getTagName(Identifier key) {
		if (PonderIndex.editingModeActive())
			return tag.containsKey(key) ? tag.get(key)
				.getFirst() : ("unregistered tag entry: " + key);
		return I18n.get(langKeyForTag(key));
	}

	@Override
	public String getTagDescription(Identifier key) {
		if (PonderIndex.editingModeActive())
			return tag.containsKey(key) ? tag.get(key)
				.getSecond() : ("unregistered tag entry: " + key);
		return I18n.get(langKeyForTagDescription(key));
	}

	@Override
	public String getSpecific(Identifier sceneId, String k) {
		if (PonderIndex.editingModeActive())
			try {
				return specific.get(sceneId).get(k);
			} catch (Exception e) {
				return "MISSING_SPECIFIC";
			}
		return I18n.get(langKeyForSpecific(sceneId, k));
	}

	@Override
	public String getSpecific(Identifier sceneId, String k, Object... params) {
		if (PonderIndex.editingModeActive())
			try {
				return String.format(specific.get(sceneId).get(k), params);
			} catch (Exception e) {
				return "MISSING_SPECIFIC";
			}
		return I18n.get(langKeyForSpecific(sceneId, k), params);
	}

	//

	private void recordGeneral(BiConsumer<String, String> consumer) {
		addGeneral(consumer, "ui.hold_to_ponder", "Hold [%1$s] to Ponder");
		addGeneral(consumer, "ui.subject", "Subject of this scene");
		addGeneral(consumer, "ui.pondering", "Pondering about...");
		addGeneral(consumer, "ui.pondering_tag", "Pondering about...");
		addGeneral(consumer, "ui.identify_mode", "Identify mode active.\nUnpause with [%1$s]");
		addGeneral(consumer, "ui.associated", "Associated Entries");

		addGeneral(consumer, "ui.close", "Close");
		addGeneral(consumer, "ui.identify", "Identify");
		addGeneral(consumer, "ui.next", "Next Scene");
		addGeneral(consumer, "ui.next_up", "Up Next:");
		addGeneral(consumer, "ui.previous", "Previous Scene");
		addGeneral(consumer, "ui.replay", "Replay");
		addGeneral(consumer, "ui.think_back", "Think Back");
		addGeneral(consumer, "ui.slow_text", "Comfy Reading");

		addGeneral(consumer, "ui.exit", "Exit");
		addGeneral(consumer, "ui.welcome", "Welcome to Ponder");
		addGeneral(consumer, "ui.categories", "Available Categories for %1$s");
		addGeneral(consumer, "ui.index_description", "Click one of the icons below to learn about its associated Items and Blocks");
		addGeneral(consumer, "ui.index_title", "Ponder Index");
	}

	private void addGeneral(BiConsumer<String, String> consumer, String key, String enUS) {
		consumer.accept(Ponder.MOD_ID + "." + key, enUS);
	}

	public void generateSceneLang() {
		PonderIndex.getSceneAccess()
			.getRegisteredEntries()
			.forEach(entry -> PonderSceneRegistry.compileScene(this, entry.getValue(), null));
	}

	@Override
	public void provideLang(String modId, BiConsumer<String, String> consumer) {
		PonderIndex.registerAll();
		PonderIndex.gatherSharedText();

		generateSceneLang();

		if (modId.equals(Ponder.MOD_ID))
			recordGeneral(consumer);

		shared.forEach((k, v) -> {
			if (k.getNamespace().equals(modId)) {
				consumer.accept(langKeyForShared(k), v);
			}
		});

		tag.forEach((k, v) -> {
			if (k.getNamespace().equals(modId)) {
				consumer.accept(langKeyForTag(k), v.getFirst());
				consumer.accept(langKeyForTagDescription(k), v.getSecond());
			}
		});

		specific.entrySet()
			.stream()
			.filter(entry -> entry.getKey().getNamespace().equals(modId))
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> {
				entry.getValue()
					.entrySet()
					.stream()
					.sorted(Map.Entry.comparingByKey())
					.forEach(subEntry -> consumer.accept(
						langKeyForSpecific(entry.getKey(), subEntry.getKey()), subEntry.getValue()));
			});
	}
}
