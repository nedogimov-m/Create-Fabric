package net.createmod.catnip.lang;

import java.util.Locale;
import java.util.Objects;

/** Client layout and locale access without client classes in the common language API. */
public final class LangClientHooks {

	private static volatile ClientAdapter client;

	public interface ClientAdapter {
		Locale getCurrentLocale();

		int getGoggleIndents(int defaultIndents);
	}

	public static void register(ClientAdapter adapter) {
		client = Objects.requireNonNull(adapter, "adapter");
	}

	static Locale getCurrentLocale() {
		ClientAdapter adapter = client;
		// Dedicated servers have no selected client language. Keep numbers deterministic.
		return adapter == null ? Locale.ROOT : adapter.getCurrentLocale();
	}

	static int getGoggleIndents(int defaultIndents) {
		ClientAdapter adapter = client;
		if (adapter == null)
			throw new IllegalStateException("Goggle tooltip layout requires the registered client font adapter");
		return adapter.getGoggleIndents(defaultIndents);
	}

	private LangClientHooks() {
	}
}
