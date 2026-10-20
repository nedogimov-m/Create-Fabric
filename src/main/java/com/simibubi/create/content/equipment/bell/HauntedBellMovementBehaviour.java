package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.content.contraptions.behaviour.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class HauntedBellMovementBehaviour extends BellMovementBehaviour {
    public static final int DISTANCE=3;
    public int getRecharge(MovementContext context) { if(!(context.temporaryData instanceof Integer))context.temporaryData=Math.clamp(context.blockEntityData.getIntOr("Recharge",0),0,HauntedBellBlockEntity.RECHARGE_TICKS);return (Integer)context.temporaryData; }
    @Override public void tick(MovementContext context) { int value=getRecharge(context);if(value>0)context.temporaryData=value-1; }
    @Override public void visitNewPosition(MovementContext context,BlockPos pos) { if(context.world instanceof ServerLevel level&&getRecharge(context)==0){HauntedBellPulser.sendPulse(level,pos,DISTANCE,false);context.temporaryData=HauntedBellBlockEntity.RECHARGE_TICKS;playSound(context);} }
    @Override public void writeExtraData(MovementContext context) { context.blockEntityData.putInt("Recharge",getRecharge(context)); }
}
