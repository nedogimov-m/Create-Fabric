package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;

/** Durable reciprocal removal, scoped to exact source geometry and material; does not load terrain. */
public final class TrackConnectionCleanup extends SavedData {
    public static final Codec<TrackConnectionCleanup> CODEC=BezierConnection.CODEC.listOf().fieldOf("Pending").codec().xmap(TrackConnectionCleanup::new,data->List.copyOf(data.pending));
    public static final SavedDataType<TrackConnectionCleanup> TYPE=new SavedDataType<>(Create.asResource("track_connection_cleanup"),TrackConnectionCleanup::new,CODEC,null);
    private final List<BezierConnection> pending=new ArrayList<>();
    private TrackConnectionCleanup(){}
    private TrackConnectionCleanup(List<BezierConnection> pending){this.pending.addAll(pending);}
    public static TrackConnectionCleanup get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TYPE);}
    public static void removeOrQueue(ServerLevel level,BezierConnection reciprocal){
        var data=get(level);data.pending.add(reciprocal);data.setDirty();data.process(level);
    }
    public static void tick(ServerLevel level){get(level).process(level);}
    private void process(ServerLevel level){
        for(var expected:List.copyOf(pending)){
            var pos=expected.bePositions.getFirst();if(!LoadedLevelAccess.isLoaded(level,pos))continue;
            if(LoadedLevelAccess.blockEntity(level,pos) instanceof TrackBlockEntity target){
                var actual=target.getConnections().get(expected.getKey());
                if(actual!=null&&actual.primary==expected.primary&&actual.getMaterial()==expected.getMaterial()&&actual.equalsSansMaterial(expected))target.removeConnection(expected.getKey());
            }
            pending.remove(expected);setDirty();
        }
    }
    public static boolean isPending(ServerLevel level,BezierConnection curve){return get(level).pending.stream().anyMatch(expected->expected.primary==curve.primary&&expected.getMaterial()==curve.getMaterial()&&expected.equalsSansMaterial(curve));}
    public static boolean isPendingEndpoint(ServerLevel level,net.minecraft.core.BlockPos pos){return get(level).pending.stream().anyMatch(curve->curve.bePositions.getFirst().equals(pos)||curve.bePositions.getSecond().equals(pos));}
    public int pendingCount(){return pending.size();}
}
