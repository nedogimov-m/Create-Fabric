package com.simibubi.create.test;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.zip.CRC32;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Failure-only native SKY storage observation. No native layer/cache/queue writes. */
final class NativeSkyStorageObservation {
    static String describe(Level level,BlockPos witness){
        try{return observe(level,witness);}
        catch(ReflectiveOperationException|RuntimeException failure){return "rawStorageObservationFailed="+failure;}
    }

    private static String observe(Level level,BlockPos witness)throws ReflectiveOperationException{
        var light=level.getLightEngine();var sky=light.getLayerListener(LightLayer.SKY);
        var storage=field(sky,"storage");
        var engineSource=field(sky,"chunkSource");var storageSource=field(storage,"chunkSource");
        var queued=(Long2ObjectMap<?>)field(storage,"queuedSections");
        var visible=field(storage,"visibleSectionData");var updating=field(storage,"updatingSectionData");
        var visibleLayers=(Long2ObjectMap<?>)field(visible,"map");var updatingLayers=(Long2ObjectMap<?>)field(updating,"map");
        var states=(Long2ByteMap)field(storage,"sectionStates");
        var changed=(LongSet)field(storage,"changedSections");var affected=(LongSet)field(storage,"sectionsAffectedByLightUpdates");var removed=(LongSet)field(storage,"toRemove");
        long column=SectionPos.getZeroNode(witness.getX()>>4,witness.getZ()>>4);
        var sources=(LongSet)field(storage,"columnsWithSources");var retained=(LongSet)field(storage,"columnsToRetainQueuedDataFor");
        var visibleTop=(Long2IntMap)field(visible,"topSections");var updatingTop=(Long2IntMap)field(updating,"topSections");
        int visibleLowest=((Number)field(visible,"currentLowestY")).intValue(),updatingLowest=((Number)field(updating,"currentLowestY")).intValue();
        var out=new StringBuilder("listener=").append(sky.getClass().getName()).append(" storage=").append(storage.getClass().getName())
            .append(" engineSourceClass=").append(engineSource.getClass().getName()).append(" storageSourceClass=").append(storageSource.getClass().getName())
            .append(" engineAndStorageSourceSame=").append(engineSource==storageSource)
            .append(" engineSourceIsNativeLevelSource=").append(engineSource==level.getChunkSource())
            .append(" storageSourceIsNativeLevelSource=").append(storageSource==level.getChunkSource())
            .append(" visibleAndUpdatingMapSame=").append(visible==updating)
            .append(" hasInconsistencies=").append(field(storage,"hasInconsistencies"))
            .append(" mapSizes(q/v/u)=").append(queued.size()).append('/').append(visibleLayers.size()).append('/').append(updatingLayers.size())
            .append(" changes/affected/toRemove=").append(changed.size()).append('/').append(affected.size()).append('/').append(removed.size())
            .append(" columnSources=").append(sources.contains(column)).append(" retainQueued=").append(retained.contains(column))
            .append(" visible(top/lowest/cache)=").append(visibleTop.get(column)).append('/').append(visibleLowest).append('/').append(field(visible,"cacheEnabled"))
            .append(" updating(top/lowest/cache)=").append(updatingTop.get(column)).append('/').append(updatingLowest).append('/').append(field(updating,"cacheEnabled"))
            .append(" nativeBrightness=").append(level.getBrightness(LightLayer.SKY,witness))
            .append(" visibleLookup={").append(visibleLookup(visibleLayers,visibleTop.get(column),visibleLowest,light.getMaxLightSection(),witness)).append("} sections=[");
        int witnessSectionY=witness.getY()>>4;
        for(int y=Math.max(light.getMinLightSection(),witnessSectionY-1);y<Math.min(light.getMaxLightSection(),witnessSectionY+2);y++){
            long key=SectionPos.of(witness.getX()>>4,y,witness.getZ()>>4).asLong();
            var q=layer(queued.get(key));var v=layer(visibleLayers.get(key));var u=layer(updatingLayers.get(key));
            String qSummary=summary(q,witness),vSummary=q!=null&&q==v?"sameQ":summary(v,witness),uSummary=u!=null&&u==v?"sameV":u!=null&&u==q?"sameQ":summary(u,witness);
            out.append(" {Y=").append(y).append(" stateByte=").append(Byte.toUnsignedInt(states.get(key)))
                .append(" changed=").append(changed.contains(key)).append(" affected=").append(affected.contains(key)).append(" toRemove=").append(removed.contains(key))
                .append(" q=").append(qSummary).append(" v=").append(vSummary).append(" u=").append(uSummary).append('}');
        }
        return out.append(']').toString();
    }

    private static String visibleLookup(Long2ObjectMap<?> layers,int top,int lowest,int maxLightSection,BlockPos witness){
        int sectionY=witness.getY()>>4;
        if(top==lowest||sectionY>=top)return "nativeAboveDataFallback15";
        for(int y=sectionY;y<Math.min(top,maxLightSection);y++){
            var data=layer(layers.get(SectionPos.of(witness.getX()>>4,y,witness.getZ()>>4).asLong()));
            if(data==null)continue;
            int localY=y==sectionY?witness.getY()&15:0;
            return "sourceSectionY="+y+", sourceLocalY="+localY+", value="+data.get(witness.getX()&15,localY,witness.getZ()&15);
        }
        return top<=maxLightSection?"nativeReachedTopFallback15":"noLayerWithinNativeVerticalRange; actualTop="+top;
    }

    private static String summary(DataLayer data,BlockPos witness){
        if(data==null)return "null";
        var checksum=new CRC32();int min=15,max=0,nonzero=0;
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int value=data.get(x,y,z);checksum.update(value);min=Math.min(min,value);max=Math.max(max,value);if(value!=0)nonzero++;
        }
        return "{empty="+data.isEmpty()+", homogeneous="+data.isDefinitelyHomogenous()+", filled15="+data.isDefinitelyFilledWith(15)+", min="+min+", max="+max+", nonzero="+nonzero+", nibblesCRC32="+Long.toHexString(checksum.getValue())+", witnessY0/4/15="+data.get(witness.getX()&15,0,witness.getZ()&15)+'/'+data.get(witness.getX()&15,4,witness.getZ()&15)+'/'+data.get(witness.getX()&15,15,witness.getZ()&15)+'}';
    }

    private static DataLayer layer(Object value){return (DataLayer)value;}
    private static Object field(Object owner,String name)throws ReflectiveOperationException{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass()){
            try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
            catch(NoSuchFieldException absent){}
        }
        throw new NoSuchFieldException(owner.getClass().getName()+'.'+name);
    }
    private NativeSkyStorageObservation(){}
}
