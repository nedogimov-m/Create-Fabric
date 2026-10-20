package com.simibubi.create.content.contraptions.pulley;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;

/** Durable completion for unloaded mirror controllers; only cells, scalar length and old owner UUID. */
public final class PulleyMirrorRestoration extends SavedData {
    private record Work(BlockPos parent,BlockPos mirror,UUID owner,int offset,boolean completed){
        Work{parent=parent.immutable();mirror=mirror.immutable();if(offset<0)throw new IllegalArgumentException("Negative mirror rope length");}
        static final Codec<Work> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("Parent").forGetter(Work::parent),BlockPos.CODEC.fieldOf("Mirror").forGetter(Work::mirror),UUIDUtil.CODEC.fieldOf("Owner").forGetter(Work::owner),Codec.intRange(0,Integer.MAX_VALUE).fieldOf("Offset").forGetter(Work::offset),Codec.BOOL.optionalFieldOf("Completed",false).forGetter(Work::completed)).apply(i,Work::new));
    }
    public static final Codec<PulleyMirrorRestoration> CODEC=Work.CODEC.listOf().fieldOf("Pending").codec().xmap(PulleyMirrorRestoration::new,data->List.copyOf(data.work));
    public static final SavedDataType<PulleyMirrorRestoration> TYPE=new SavedDataType<>(Create.asResource("pulley_mirror_restoration"),PulleyMirrorRestoration::new,CODEC,null);
    private final List<Work> work=new ArrayList<>();private boolean processing;
    public PulleyMirrorRestoration(){}
    private PulleyMirrorRestoration(List<Work> entries){work.addAll(entries);}
    private static PulleyMirrorRestoration get(ServerLevel server){return server.getDataStorage().computeIfAbsent(TYPE);}
    public static void finishOrQueue(ServerLevel server,BlockPos parent,BlockPos mirror,UUID owner,int offset){finishOrQueue(server,parent,mirror,owner,offset,true);}
    static void finishOrQueue(ServerLevel server,BlockPos parent,BlockPos mirror,UUID owner,int offset,boolean completed){var data=get(server);var task=new Work(parent,mirror,owner,offset,completed);if(!data.work.contains(task)){data.work.add(task);data.setDirty();}tryFinish(server,mirror);}
    private static boolean apply(ServerLevel server,Work task){
        if(!PulleyWorldAccess.transferAllowed()||!LoadedLevelAccess.isLoaded(server,task.mirror()))return false;
        try{if(!task.completed()){if(!LoadedLevelAccess.isLoaded(server,task.parent()))return false;LoadedEntityAccess.requireReady(server,task.parent());if(server.getEntity(task.owner()) instanceof AbstractContraptionEntity owner&&owner.hasOwnedStructure())return false;}
            if(!(LoadedLevelAccess.blockEntity(server,task.mirror()) instanceof PulleyBlockEntity mirror))return true;
            return mirror.finishMirroring(server,task.parent(),task.owner(),task.offset());
        }catch(AssemblyException unavailable){return false;}
    }
    public static boolean tryFinish(ServerLevel server,BlockPos mirror){var data=get(server);if(data.processing)return false;data.processing=true;boolean finished=false;try{for(var task:List.copyOf(data.work))if(task.mirror().equals(mirror)&&data.work.contains(task)&&apply(server,task)){data.work.remove(task);data.setDirty();finished=true;}}finally{data.processing=false;}return finished;}
    public static void tick(ServerLevel server){var data=get(server);if(data.processing)return;data.processing=true;try{for(var task:List.copyOf(data.work))if(data.work.contains(task)&&apply(server,task)){data.work.remove(task);data.setDirty();}}finally{data.processing=false;}}
}
