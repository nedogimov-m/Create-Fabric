package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.*;

public final class CurrentFloorDisplaySource extends SingleLineDisplaySource {
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){return context.getSourceBlockEntity()instanceof ElevatorContactBlockEntity contact?Component.literal(contact.lastReportedCurrentFloor):EMPTY_LINE;}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return false;}
    @Override protected String getTranslationKey(){return "current_floor";}
}
