package com.simibubi.create.foundation.gui.widget;

import java.util.List;

import net.minecraft.network.chat.Component;

public class TooltipArea extends ScheduleWidget {

	public TooltipArea(int x, int y, int width, int height) {
		super(x, y, width, height);
	}


	public TooltipArea withTooltip(List<Component> tooltip) {
		this.toolTip = tooltip;
		return this;
	}

}
