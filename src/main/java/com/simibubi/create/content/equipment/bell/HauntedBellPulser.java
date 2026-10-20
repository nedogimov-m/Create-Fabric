package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Source held-item cadence, with no retained player/level references. */
public final class HauntedBellPulser {
    public static final int DISTANCE=3,RECHARGE_TICKS=8,WARMUP_TICKS=10;
    private static final Map<UUID,Warmup> warmups=new HashMap<>();
    public static final class Warmup {
        private int remaining=WARMUP_TICKS;
        private long lastSeen;
        public boolean tick(long now,long gameTime) { if(lastSeen!=0&&now-lastSeen>=250_000_000L)remaining=WARMUP_TICKS;lastSeen=now;boolean first=remaining==1;if(remaining>Integer.MIN_VALUE)remaining--;return remaining<=0&&(first||gameTime%RECHARGE_TICKS!=0); }
        boolean expired(long now) { return now-lastSeen>=250_000_000L; }
    }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SoulPulseEffectPacket.TYPE,SoulPulseEffectPacket.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(level->{long now=System.nanoTime();warmups.values().removeIf(value->value.expired(now));for(var player:level.players())if(!player.isSpectator()&&player.isHolding(stack->stack.is(AllBlocks.HAUNTED_BELL.asItem())))if(warmups.computeIfAbsent(player.getUUID(),key->new Warmup()).tick(now,level.getGameTime()))sendPulse(level,player.blockPosition(),DISTANCE,false);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->warmups.clear());
    }
    public static void sendPulse(ServerLevel level,BlockPos pos,int distance,boolean overlap) {
        if(!LoadedLevelAccess.isLoaded(level,pos))return;
        var packet=new SoulPulseEffectPacket(pos,distance,overlap);
        for(var player:PlayerLookup.tracking(level,pos))ServerPlayNetworking.send(player,packet);
    }
    private HauntedBellPulser() {}
}
