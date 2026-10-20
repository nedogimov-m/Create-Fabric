package com.simibubi.create.content.trains.schedule;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;

import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import com.simibubi.create.foundation.mixin.client.ScheduleSuggestionsAccessor;
import com.simibubi.create.foundation.mixin.client.ScheduleSuggestionsListInvoker;

public class DestinationSuggestions extends CommandSuggestions {

	private EditBox textBox;
	private List<IntAttached<String>> viableStations;
	private String previous = "<>";
	private Font font;
	private boolean active;
	private boolean updatePending;

	List<Suggestion> currentSuggestions;
	private int yOffset;

	public DestinationSuggestions(Minecraft pMinecraft, Screen pScreen, EditBox pInput, Font pFont,
		List<IntAttached<String>> viableStations, boolean anchorToBottom, int yOffset) {
		super(pMinecraft, pScreen, pInput, pFont, true, true, 0, 7, anchorToBottom, 0xee_303030);
		this.textBox = pInput;
		this.font = pFont;
		this.viableStations = viableStations;
		this.yOffset = yOffset;
		currentSuggestions = new ArrayList<>();
		active = false;
	}

	/** Native EditBox may notify before deletion has settled its selection bounds. */
	public void requestUpdate() {
		updatePending = true;
	}

	public void tick() {
		if (((ScheduleSuggestionsAccessor) this).create$getScheduleSuggestions() == null)
			textBox.setSuggestion("");
		if (active == textBox.isFocused() && !updatePending)
			return;
		active = textBox.isFocused();
		updatePending = false;
		updateCommandInfo();
	}

	@Override
	public void updateCommandInfo() {
		if (textBox.getValue().length() < textBox.getCursorPosition())
			return;
		
		String trimmed = textBox.getValue()
			.substring(0, textBox.getCursorPosition());

		if (!textBox.getHighlighted()
			.isBlank())
			trimmed = trimmed.replace(textBox.getHighlighted(), "");
		
		final String value = trimmed;
		
		if (value.equals(previous))
			return;
		if (!active) {
			((ScheduleSuggestionsAccessor) this).create$setScheduleSuggestions(null);
			return;
		}

		previous = value;
		currentSuggestions = viableStations.stream()
			.filter(ia -> !ia.getValue()
				.equals(value) && ia.getValue()
					.toLowerCase(java.util.Locale.ROOT)
					.startsWith(value.toLowerCase(java.util.Locale.ROOT)))
			.sorted((ia1, ia2) -> Integer.compare(ia1.getFirst(), ia2.getFirst()))
			.map(IntAttached::getValue)
			.map(s -> new Suggestion(new StringRange(0, 1000), s))
			.toList();

		showSuggestions(false);
	}

	public void showSuggestions(boolean pNarrateFirstSuggestion) {
		if (currentSuggestions.isEmpty()) {
			((ScheduleSuggestionsAccessor) this).create$setScheduleSuggestions(null);
			return;
		}

		int width = 0;
		for (Suggestion suggestion : currentSuggestions)
			width = Math.max(width, this.font.width(suggestion.getText()));
		int x = Mth.clamp(textBox.getScreenX(0), 0, textBox.getScreenX(0) + textBox.getInnerWidth() - width);
		((ScheduleSuggestionsAccessor) this).create$setScheduleSuggestions(ScheduleSuggestionsListInvoker.create$scheduleList(this, x, 72 + yOffset, width, currentSuggestions, false));
	}

	public boolean isEmpty() {
		return viableStations.isEmpty();
	}

}
