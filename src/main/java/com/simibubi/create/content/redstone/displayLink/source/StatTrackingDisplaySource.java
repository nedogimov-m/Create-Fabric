package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public abstract class StatTrackingDisplaySource extends ScoreboardDisplaySource {
    protected abstract String getObjectiveName();
    protected abstract Component getObjectiveDisplayName();
    protected abstract int updatedScoreOf(ServerPlayer player);
    @Override protected Stream<Entry> provideEntries(DisplayLinkContext context,int maxRows){if(!(context.level()instanceof ServerLevel level))return Stream.empty();var name="create_auto_"+getObjectiveName();var scoreboard=level.getScoreboard();var objective=scoreboard.getObjective(name);if(objective==null)objective=scoreboard.addObjective(name,ObjectiveCriteria.DUMMY,getObjectiveDisplayName(),ObjectiveCriteria.RenderType.INTEGER,false,null);for(var player:level.getServer().getPlayerList().getPlayers())scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()),objective).set(updatedScoreOf(player));return showScoreboard(level,name,maxRows);}
    @Override protected boolean shortenNumbers(DisplayLinkContext context){return false;}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){return List.of();}
}
