package net.createmod.catnip.lang;

import java.util.Locale;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;

/** Native client adapter for the common Ponder/Catnip language builders. */
public final class LangClient implements ClientModInitializer, LangClientHooks.ClientAdapter {

	@Override
	public void onInitializeClient() {
		LangClientHooks.register(this);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
			.register(client -> LangNumberFormat.numberFormat.update());
		var resources = net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
		var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("create", "number_format");
		resources.registerReloadListener(id, (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
			manager -> LangNumberFormat.numberFormat.update());
		resources.addListenerOrdering(net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys.AFTER_VANILLA, id);
	}

	@Override
	public Locale getCurrentLocale() {
		var client = Minecraft.getInstance();
		var languages = client == null ? null : client.getLanguageManager();
		return languages == null ? Locale.ROOT : Locale.forLanguageTag(languages.getSelected().replace('_', '-'));
	}

	@Override
	public int getGoggleIndents(int defaultIndents) {
		return getIndents(Minecraft.getInstance().font, defaultIndents);
	}

	static int getIndents(Font font, int defaultIndents) {
		if (font == null) return defaultIndents;
		int spaceWidth = font.width(" ");
		if (LangBuilder.DEFAULT_SPACE_WIDTH == spaceWidth)
			return defaultIndents;
		return Mth.ceil(LangBuilder.DEFAULT_SPACE_WIDTH * defaultIndents / spaceWidth);
	}
}
