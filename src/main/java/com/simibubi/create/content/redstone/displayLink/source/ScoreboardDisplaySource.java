package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class ScoreboardDisplaySource extends ValueListDisplaySource {
    @Override protected Stream<Entry> provideEntries(DisplayLinkContext context,int maxRows){return context.level()instanceof ServerLevel server?showScoreboard(server,context.sourceConfig().getStringOr("Objective",""),maxRows):Stream.empty();}
    protected Stream<Entry> showScoreboard(ServerLevel level,String name,int maxRows){var objective=level.getScoreboard().getObjective(name);if(objective==null)return Stream.of(new Entry(404,DisplayFormatting.translate("display_source.scoreboard.objective_not_found",name)));return level.getScoreboard().listPlayerScores(objective).stream().map(score->new Entry(score.value(),Component.literal(score.owner()))).sorted(Comparator.comparingInt(Entry::value).reversed()).limit(maxRows);}
    @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context){return List.of(DisplaySourceConfiguration.text("Objective",128),DisplaySourceConfiguration.choice("Format","create.display_source.value_list.shortened","create.display_source.value_list.full_number"));}
    @Override protected boolean valueFirst(){return false;}
    @Override protected String getTranslationKey(){return "scoreboard";}
}
