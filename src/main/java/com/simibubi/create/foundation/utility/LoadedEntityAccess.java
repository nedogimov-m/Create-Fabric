package com.simibubi.create.foundation.utility;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.mixin.accessor.EntitySectionVisibilityAccessor;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Native entity data and query visibility, without tickets or waits on chunk futures. */
public final class LoadedEntityAccess {
    public static boolean isReady(Level level,BlockPos pos) { return isReady(level,pos.getX()>>4,pos.getZ()>>4); }
    public static boolean isReady(Level level,int chunkX,int chunkZ) {
        if(level.getChunkSource().getChunkNow(chunkX,chunkZ)==null)return false;
        if(!(level instanceof ServerLevel server))return true;
        long key=ChunkPos.pack(chunkX,chunkZ);
        if(!server.areEntitiesLoaded(key))return false;
        var manager=((ServerLevelEntityManagerAccessor)server).create$getEntityManager();
        var visibility=((EntitySectionVisibilityAccessor)manager).create$getChunkVisibility().get(key);
        return visibility!=null&&visibility.isAccessible();
    }
    public static void requireReady(Level level,BlockPos pos)throws AssemblyException {
        if(!isReady(level,pos))throw AssemblyException.entitiesNotReady(pos);
    }
    /** Saved glue volumes are at most 64 blocks wide; their centres may be 32 blocks away. */
    public static void requireGlueQueriesReady(Level level,BlockPos pos,Set<Long> checked)throws AssemblyException {
        for(int x=(pos.getX()-32)>>4;x<=(pos.getX()+32)>>4;x++)for(int z=(pos.getZ()-32)>>4;z<=(pos.getZ()+32)>>4;z++){
            long key=ChunkPos.pack(x,z);
            if(checked.contains(key))continue;
            var chunk=level.getChunkSource().getChunkNow(x,z);
            // Cached terrain outside FULL visibility is not part of native entity queries.
            // Actual source/target cells are validated separately, even at that boundary.
            if(chunk==null||chunk.getFullStatus()==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL))continue;
            if(!isReady(level,x,z))throw AssemblyException.entitiesNotReady(new BlockPos(x<<4,pos.getY(),z<<4));
            checked.add(key);
        }
    }
    private LoadedEntityAccess(){}
}
