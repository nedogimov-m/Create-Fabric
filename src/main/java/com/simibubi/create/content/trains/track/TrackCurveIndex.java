package com.simibubi.create.content.trains.track;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Only genuine currently loaded primary endpoint BEs; scoped to actual native level identity. */
public final class TrackCurveIndex {
    private static final Map<Level,Map<BlockPos,WeakReference<TrackBlockEntity>>> LEVELS=new WeakHashMap<>();
    public static synchronized void track(TrackBlockEntity track){if(track.getLevel()!=null)LEVELS.computeIfAbsent(track.getLevel(),ignored->new HashMap<>()).put(track.getBlockPos(),new WeakReference<>(track));}
    public static synchronized void untrack(TrackBlockEntity track){var entries=LEVELS.get(track.getLevel());if(entries==null)return;var value=entries.get(track.getBlockPos());if(value!=null&&value.get()==track)entries.remove(track.getBlockPos());}
    public static synchronized List<TrackBlockEntity> loaded(Level level){
        var entries=LEVELS.get(level);if(entries==null)return List.of();var result=new ArrayList<TrackBlockEntity>();
        entries.entrySet().removeIf(entry->{
            var be=entry.getValue().get();
            if(be==null||be.isRemoved()||be.getLevel()!=level)return true;
            // An unavailable native chunk says nothing about whether its real endpoint still exists.
            // Keep the weak binding for its return, but never expose unavailable coverage to a caller.
            if(!LoadedLevelAccess.isLoaded(level,entry.getKey()))return false;
            if(LoadedLevelAccess.blockEntity(level,entry.getKey())!=be)return true;
            if(be.hasInteractableConnections()&&be.getBlockState().getOptionalValue(TrackBlock.HAS_BE).orElse(false))result.add(be);
            return false;
        });return List.copyOf(result);
    }
    private TrackCurveIndex(){}
}
