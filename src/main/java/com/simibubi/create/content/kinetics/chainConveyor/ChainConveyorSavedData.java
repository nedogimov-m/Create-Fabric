package com.simibubi.create.content.kinetics.chainConveyor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;

/** A closed edge waits for its unloaded endpoint to acknowledge removal. */
public final class ChainConveyorSavedData extends SavedData {
    public record PendingRemoval(UUID id,BlockPos endpoint) {
        public static final Codec<PendingRemoval> CODEC=RecordCodecBuilder.create(instance->instance.group(
                UUIDUtil.CODEC.fieldOf("Id").forGetter(PendingRemoval::id),BlockPos.CODEC.fieldOf("Endpoint").forGetter(PendingRemoval::endpoint)).apply(instance,PendingRemoval::new));
    }
    public static final Codec<ChainConveyorSavedData> CODEC=PendingRemoval.CODEC.listOf().fieldOf("ClosedChains").codec()
            .xmap(ChainConveyorSavedData::new,data->List.copyOf(data.pending.values()));
    public static final SavedDataType<ChainConveyorSavedData> TYPE=new SavedDataType<>(Create.asResource("chain_conveyors"),ChainConveyorSavedData::new,CODEC,null);
    private final Map<UUID,PendingRemoval> pending=new HashMap<>();
    public ChainConveyorSavedData(){}
    private ChainConveyorSavedData(List<PendingRemoval> values){values.forEach(value->pending.put(value.id,value));}
    public static ChainConveyorSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TYPE);}
    public boolean isClosed(UUID id){return pending.containsKey(id);}
    public int pendingCount(){return pending.size();}
    /** Returns true only for the endpoint that may issue the chain refund/drop. */
    public boolean close(UUID id,BlockPos otherEndpoint,boolean otherChunkLoaded){
        if(pending.containsKey(id))return false;
        if(!otherChunkLoaded){pending.put(id,new PendingRemoval(id,otherEndpoint.immutable()));setDirty();}
        return true;
    }
    public void acknowledge(UUID id,BlockPos endpoint){var removal=pending.get(id);if(removal!=null&&removal.endpoint.equals(endpoint)){pending.remove(id);setDirty();}}
}
