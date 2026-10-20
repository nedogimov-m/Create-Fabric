package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;

/** Transient pressed inputs never become saved power sources. */
public final class LinkedControllerServerHandler {
    public static final int TIMEOUT=30;
    private static final Map<ServerLevel,Map<UUID,Map<Couple<RedstoneLinkNetworkHandler.Frequency>,ManualFrequencyEntry>>> INPUTS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,GlobalPos>> SESSIONS=new IdentityHashMap<>();
    public static void receivePressed(LevelAccessor world,BlockPos pos,UUID player,List<Couple<RedstoneLinkNetworkHandler.Frequency>> frequencies,boolean pressed){
        if(!(world instanceof ServerLevel level)||frequencies.size()>6)return;
        var players=INPUTS.computeIfAbsent(level,ignored->new HashMap<>());
        var entries=players.computeIfAbsent(player,ignored->new LinkedHashMap<>());
        for(var frequency:new LinkedHashSet<>(frequencies)){
            var existing=entries.get(frequency);
            if(existing!=null){if(pressed)existing.renew(pos);else existing.remaining=0;continue;}
            if(!pressed)continue;
            var key=Couple.create(frequency.getFirst(),frequency.getSecond());
            var entry=new ManualFrequencyEntry(pos.immutable(),key);entries.put(key,entry);
            RedstoneLinkNetworkHandler.INSTANCE.addToNetwork(level,entry);
            var actual=level.getPlayerByUUID(player);
            if(actual instanceof ServerPlayer&&!(actual instanceof FakePlayer))
                for(var linkable:RedstoneLinkNetworkHandler.INSTANCE.getNetworkOf(level,entry))
                    if(linkable instanceof LinkBehaviour link&&link.isListening())
                        AllAdvancements.LINKED_CONTROLLER.awardTo(actual);
        }
        if(entries.isEmpty())players.remove(player);if(players.isEmpty())INPUTS.remove(level);
    }
    public static void tick(ServerLevel level){
        var players=INPUTS.get(level);if(players==null)return;
        for(var playerIterator=players.entrySet().iterator();playerIterator.hasNext();){
            var entries=playerIterator.next().getValue();
            for(var iterator=entries.values().iterator();iterator.hasNext();){var entry=iterator.next();
                if(--entry.remaining<=0){RedstoneLinkNetworkHandler.INSTANCE.removeFromNetwork(level,entry);iterator.remove();}}
            if(entries.isEmpty())playerIterator.remove();
        }if(players.isEmpty())INPUTS.remove(level);
    }
    public static void releasePlayer(MinecraftServer server,UUID player){
        for(var level:List.copyOf(INPUTS.keySet()))if(level.getServer()==server){var entries=INPUTS.get(level).remove(player);
            if(entries!=null)for(var entry:entries.values()){entry.remaining=0;RedstoneLinkNetworkHandler.INSTANCE.removeFromNetwork(level,entry);}
            if(INPUTS.get(level).isEmpty())INPUTS.remove(level);}
        var sessions=SESSIONS.get(server);if(sessions!=null){sessions.remove(player);if(sessions.isEmpty())SESSIONS.remove(server);}
    }
    public static boolean claimSession(ServerLevel level,UUID player,BlockPos pos){
        var sessions=SESSIONS.computeIfAbsent(level.getServer(),ignored->new HashMap<>());
        return sessions.putIfAbsent(player,GlobalPos.of(level.dimension(),pos.immutable()))==null;
    }
    public static boolean ownsSession(ServerLevel level,UUID player,BlockPos pos){
        var sessions=SESSIONS.get(level.getServer());return sessions!=null&&GlobalPos.of(level.dimension(),pos).equals(sessions.get(player));
    }
    public static void releaseSession(ServerLevel level,UUID player,BlockPos pos){
        var sessions=SESSIONS.get(level.getServer());if(sessions==null)return;
        sessions.remove(player,GlobalPos.of(level.dimension(),pos));if(sessions.isEmpty())SESSIONS.remove(level.getServer());
    }
    public static void unload(ServerLevel level){
        var players=INPUTS.remove(level);if(players!=null)for(var entries:players.values())for(var entry:entries.values()){
            entry.remaining=0;RedstoneLinkNetworkHandler.INSTANCE.removeFromNetwork(level,entry);}
        var sessions=SESSIONS.get(level.getServer());if(sessions!=null){sessions.values().removeIf(pos->pos.dimension()==level.dimension());if(sessions.isEmpty())SESSIONS.remove(level.getServer());}
    }
    public static void unload(MinecraftServer server){for(var level:List.copyOf(INPUTS.keySet()))if(level.getServer()==server)unload(level);SESSIONS.remove(server);}
    private static final class ManualFrequencyEntry implements IRedstoneLinkable{
        private BlockPos pos;private final Couple<RedstoneLinkNetworkHandler.Frequency> frequency;private int remaining=TIMEOUT;
        ManualFrequencyEntry(BlockPos pos,Couple<RedstoneLinkNetworkHandler.Frequency> frequency){this.pos=pos;this.frequency=frequency;}
        void renew(BlockPos pos){this.pos=pos.immutable();remaining=TIMEOUT;}
        @Override public int getTransmittedStrength(){return isAlive()?15:0;}
        @Override public void setReceivedStrength(int strength){}
        @Override public boolean isListening(){return false;}
        @Override public boolean isAlive(){return remaining>0;}
        @Override public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){return Couple.create(frequency.getFirst(),frequency.getSecond());}
        @Override public BlockPos getLocation(){return pos;}
    }
    private LinkedControllerServerHandler(){}
}
