package com.simibubi.create.content.trains.display;

import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.entity.Train;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.createmod.catnip.data.Glob;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/** Source departure predictions, scoped to the actual native server that owns their trains. */
public final class GlobalTrainDisplayData {
    private static final Map<MinecraftServer, GlobalTrainDisplayData> SERVERS = new WeakHashMap<>();
    private final Map<String, Collection<TrainDeparturePrediction>> statusByDestination = new HashMap<>();
    private long lastRefresh = Long.MIN_VALUE;

    private GlobalTrainDisplayData() {}

    public static GlobalTrainDisplayData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Departure data belongs to the actual native server thread");
        return SERVERS.computeIfAbsent(server, ignored -> new GlobalTrainDisplayData());
    }

    public void refresh(MinecraftServer server) {
        if (get(server) != this) throw new IllegalArgumentException("Foreign native departure owner");
        statusByDestination.clear();
        for (Train train : TrainManager.get(server).trains().values()) {
            if (train.runtime.paused || train.runtime.getSchedule() == null) continue;
            if (train.derailed || train.isRestoring() || train.graph == null) continue;
            for (TrainDeparturePrediction prediction : train.runtime.submitPredictions())
                statusByDestination.computeIfAbsent(prediction.destination, ignored -> new ArrayList<>()).add(prediction);
        }
        lastRefresh = server.overworld().getGameTime();
    }

    public List<TrainDeparturePrediction> prepare(MinecraftServer server, String filter, int maxLines) {
        if (get(server) != this) throw new IllegalArgumentException("Foreign native departure owner");
        long now = server.overworld().getGameTime();
        if (lastRefresh == Long.MIN_VALUE || now < lastRefresh || now - lastRefresh >= 100) refresh(server);
        String regex = Glob.toRegexPattern(filter, "");
        return statusByDestination.entrySet().stream().filter(entry -> entry.getKey().matches(regex))
                .flatMap(entry -> entry.getValue().stream()).sorted().limit(Math.max(0, maxLines)).toList();
    }

    public static void unload(MinecraftServer server) {
        SERVERS.remove(server);
    }

    public static class TrainDeparturePrediction implements Comparable<TrainDeparturePrediction> {
        public Train train;
        public int ticks;
        public MutableComponent scheduleTitle;
        public String destination;

        public TrainDeparturePrediction(Train train, int ticks, MutableComponent scheduleTitle, String destination) {
            this.scheduleTitle = scheduleTitle;
            this.destination = destination;
            this.train = train;
            this.ticks = ticks;
        }

        private int getCompareTicks() {
            if (ticks == -1) return Integer.MAX_VALUE;
            if (ticks < 200) return 0;
            return ticks;
        }

        @Override
        public int compareTo(TrainDeparturePrediction other) {
            int compare = Integer.compare(getCompareTicks(), other.getCompareTicks());
            return compare == 0 ? train.name.getString().compareTo(other.train.name.getString()) : compare;
        }
    }
}
