package com.simibubi.create.foundation.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Native widget/tooltip extraction used by the real source schedule configuration controls. */
public class ScheduleWidget extends AbstractWidget {
    protected static final int HEADER_RGB = 0xFBDC7D;
    protected static final int HINT_RGB = 0xAFAFAF;
    protected List<Component> toolTip = new ArrayList<>();
    public int lockedTooltipX = Integer.MIN_VALUE, lockedTooltipY = Integer.MIN_VALUE;

    protected ScheduleWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partial) {
        if (!toolTip.isEmpty() && isHoveredOrFocused()) {
            int x = lockedTooltipX == Integer.MIN_VALUE ? mouseX : getX() + lockedTooltipX;
            int y = lockedTooltipY == Integer.MIN_VALUE ? mouseY : getY() + lockedTooltipY;
            gui.setTooltipForNextFrame(Minecraft.getInstance().font, toolTip, Optional.empty(), x, y);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        if (toolTip.isEmpty()) output.add(NarratedElementType.TITLE, getMessage());
        else output.add(NarratedElementType.TITLE, toolTip.toArray(Component[]::new));
    }
}
