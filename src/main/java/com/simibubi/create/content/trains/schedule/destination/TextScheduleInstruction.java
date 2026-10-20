package com.simibubi.create.content.trains.schedule.destination;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.trains.schedule.ScheduleText;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public abstract class TextScheduleInstruction extends ScheduleInstruction {

	protected String getLabelText() {
		return textData("Text");
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(ScheduleText.translate("schedule." + type + "." + getId().getPath() + ".summary")
			.withStyle(ChatFormatting.GOLD), ScheduleText.translate("generic.in_quotes", Component.literal(getLabelText())));
	}

	

	

}
