package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;

/** Durable cleanup of an actual removed portal pair. Identity is not an item/refund/debt record. */
public final class TrackPortalCleanup extends SavedData {
    public record Removal(UUID binding,GlobalPos removed,GlobalPos counterpart,TrackMaterial material){
        public static final Codec<Removal> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Binding").forGetter(Removal::binding),GlobalPos.CODEC.fieldOf("Removed").forGetter(Removal::removed),GlobalPos.CODEC.fieldOf("Counterpart").forGetter(Removal::counterpart),TrackMaterial.CODEC.fieldOf("Material").forGetter(Removal::material)).apply(i,Removal::new));
    }
    public static final Codec<TrackPortalCleanup> CODEC=Removal.CODEC.listOf().fieldOf("Pending").codec().xmap(TrackPortalCleanup::new,data->List.copyOf(data.pending));
    public static final SavedDataType<TrackPortalCleanup> TYPE=new SavedDataType<>(Create.asResource("track_portal_cleanup"),TrackPortalCleanup::new,CODEC,null);
    private final Set<Removal> pending=new LinkedHashSet<>();
    private boolean processing;
    public TrackPortalCleanup(){}
    private TrackPortalCleanup(List<Removal> values){pending.addAll(values);}
    public static TrackPortalCleanup get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TYPE);}
    public static void register(){ServerTickEvents.END_SERVER_TICK.register(server->get(server).process(server));}
    public static void removed(TrackBlockEntity owner){
        if(owner.portalCounterpartRemoval||owner.boundLocation==null||owner.getPortalBinding()==null||!(owner.getLevel() instanceof ServerLevel level))return;
        if(!(owner.getBlockState().getBlock() instanceof ITrackBlock track))throw new IllegalStateException("Removed portal owner has no genuine track material");
        var data=get(level.getServer());var removal=new Removal(owner.getPortalBinding(),GlobalPos.of(level.dimension(),owner.getBlockPos()),GlobalPos.of(owner.boundLocation.getFirst(),owner.boundLocation.getSecond()),track.getMaterial());
        if(data.pending.add(removal))data.setDirty();
    }
    public boolean isCounterpartPending(GlobalPos position,UUID binding){return binding!=null&&pending.stream().anyMatch(record->record.binding().equals(binding)&&record.counterpart().equals(position));}
    public boolean isRetired(UUID binding){return binding!=null&&pending.stream().anyMatch(record->record.binding().equals(binding));}
    public int pendingCount(){return pending.size();}
    public void process(MinecraftServer server){
        if(processing)return;processing=true;
        try{for(var removal:List.copyOf(pending)){
            var level=server.getLevel(removal.counterpart().dimension());if(level==null)continue;var pos=removal.counterpart().pos();
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;
            var state=TrackPortalAccess.state(level,pos);
            if(!(state.getBlock() instanceof TrackBlock track)||track.getMaterial()!=removal.material()||!state.getValue(TrackBlock.SHAPE).isPortal()){complete(removal);continue;}
            var existing=TrackBlockEntityAccess.existing(level,pos);
            if(!(existing instanceof TrackBlockEntity owner))continue; // Conditional native data absence remains unknown.
            if(!removal.binding().equals(owner.getPortalBinding())||owner.boundLocation==null||!owner.boundLocation.getFirst().equals(removal.removed().dimension())||!owner.boundLocation.getSecond().equals(removal.removed().pos())){complete(removal);continue;}
            var changes=new LinkedHashSet<BlockPos>();changes.add(pos);for(var curve:owner.getConnections().values())changes.add(curve.getKey());
            try{TrackPropagator.preflightChanges(level,changes);}catch(UnloadedTrackException missing){continue;}
            if(TrackPortalAccess.state(level,pos)!=state||TrackBlockEntityAccess.existing(level,pos)!=owner||!removal.binding().equals(owner.getPortalBinding()))throw new IllegalStateException("Actual portal counterpart changed after cleanup preflight");
            owner.portalCounterpartRemoval=true;
            try{if(!level.destroyBlock(pos,false))continue;}finally{owner.portalCounterpartRemoval=false;}
            complete(removal);
        }}finally{processing=false;}
    }
    private void complete(Removal removal){if(pending.remove(removal))setDirty();}
}
