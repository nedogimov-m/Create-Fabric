package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.minecraft.network.chat.*;
import net.minecraft.world.phys.AABB;

public final class EntityNameDisplaySource extends SingleLineDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&LoadedEntityAccess.isReady(context.level(),context.getSourcePos());}
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){if(!LoadedEntityAccess.isReady(context.level(),context.getSourcePos()))return EMPTY_LINE;var seats=context.level().getEntitiesOfClass(SeatEntity.class,new AABB(context.getSourcePos()));if(seats.isEmpty()||seats.getFirst().getPassengers().isEmpty())return EMPTY_LINE;return Component.literal(seats.getFirst().getPassengers().getFirst().getDisplayName().getString());}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "entity_name";}
}
