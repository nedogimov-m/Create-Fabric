package com.simibubi.create.foundation.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Real MapItemSavedData owns persistence, tracked count, native packet dirtiness and source updates. */
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin implements StationMapData {
    @Unique private static final String CREATE_STATIONS="create:stations";
    @Shadow @Final @Mutable public static Codec<MapItemSavedData> CODEC;
    @Shadow @Final public int centerX,centerZ;
    @Shadow @Final public byte scale;
    @Shadow @Final private Map<String,MapDecoration> decorations;
    @Shadow private int trackedDecorationCount;
    @Shadow private void removeDecoration(String id){throw new AssertionError();}
    @Shadow private void setDecorationsDirty(){throw new AssertionError();}
    @Shadow public abstract boolean isTrackedCountOverLimit(int count);
    @Unique private final Map<String,StationMarker> create$stations=new LinkedHashMap<>();
    @Inject(method="<clinit>",at=@At("RETURN"))
    private static void create$nativeCodec(CallbackInfo ci){
        var nativeCodec=CODEC;
        CODEC=Codec.pair(nativeCodec,StationMarker.CODEC.listOf().optionalFieldOf(CREATE_STATIONS,List.of()).codec()).xmap(pair->{var owner=(StationMapData)pair.getFirst();for(var marker:pair.getSecond())owner.addStationMarker(marker);return pair.getFirst();},owner->Pair.of(owner,((StationMapData)owner).create$stationMarkers()));
    }
    @Override public List<StationMarker> create$stationMarkers(){return List.copyOf(create$stations.values());}
    @Override public void addStationMarker(StationMarker marker){
        create$stations.put(marker.getId(),marker);((MapItemSavedData)(Object)this).setDirty();
        int multiplier=1<<scale;float localX=(marker.getTarget().getX()-centerX)/(float)multiplier,localZ=(marker.getTarget().getZ()-centerZ)/(float)multiplier;
        if(localX < -63||localX>63||localZ < -63||localZ>63){removeDecoration(marker.getId());return;}
        var next=StationMarker.createStationDecoration((byte)(int)(localX*2+.5f),(byte)(int)(localZ*2+.5f),Optional.of(marker.getName()));
        var previous=decorations.put(marker.getId(),next);
        if(!next.equals(previous)){if(previous!=null&&previous.type().value().trackCount())trackedDecorationCount--;if(next.type().value().trackCount())trackedDecorationCount++;setDecorationsDirty();}
    }
    @Override public boolean toggleStation(LevelAccessor level,BlockPos pos,StationBlockEntity station){
        double multiplier=1<<scale,x=(pos.getX()+.5-centerX)/multiplier,z=(pos.getZ()+.5-centerZ)/multiplier;
        if(x < -63||x>63||z < -63||z>63)return false;var marker=StationMarker.fromWorld(level,pos);if(marker==null)return false;
        if(create$stations.remove(marker.getId(),marker)){removeDecoration(marker.getId());((MapItemSavedData)(Object)this).setDirty();return true;}
        // Source1.21.1 rejects at >=256; native26.3's same-named method now tests strict >.
        if(trackedDecorationCount>=256)return false;addStationMarker(marker);return true;
    }
    @Inject(method="locked",at=@At("RETURN"))
    private void create$copyLockedMarkers(CallbackInfoReturnable<MapItemSavedData> ci){for(var marker:create$stations.values())((StationMapData)ci.getReturnValue()).addStationMarker(marker);}
    @Inject(method="checkBanners",at=@At("RETURN"))
    private void create$checkStations(BlockGetter level,int x,int z,CallbackInfo ci){
        if(!(level instanceof Level actualLevel))return;
        var refreshed=new ArrayList<StationMarker>();var iterator=create$stations.values().iterator();
        while(iterator.hasNext()){
            var marker=iterator.next();if(marker.getTarget().getX()!=x||marker.getTarget().getZ()!=z||!LoadedLevelAccess.isLoaded(actualLevel,marker.getSource()))continue;
            var actual=StationMarker.fromWorld(level,marker.getSource());if(marker.equals(actual))continue;
            iterator.remove();removeDecoration(marker.getId());((MapItemSavedData)(Object)this).setDirty();
            if(actual!=null&&marker.getTarget().equals(actual.getTarget()))refreshed.add(actual);
        }
        refreshed.forEach(this::addStationMarker);
    }
}
