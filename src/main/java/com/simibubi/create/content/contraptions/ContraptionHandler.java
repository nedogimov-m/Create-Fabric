package com.simibubi.create.content.contraptions;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.world.level.Level;

/** Per-world loaded owners, populated by native entity ticks without chunk or entity-loading lookups. */
public final class ContraptionHandler {
    private static final Map<Level,Map<Integer,WeakReference<AbstractContraptionEntity>>> LOADED=new WeakHashMap<>();
    private static synchronized Map<Integer,WeakReference<AbstractContraptionEntity>> loaded(Level level){return LOADED.computeIfAbsent(level,key->new LinkedHashMap<>());}
    public static void addSpawnedContraptionsToCollisionList(AbstractContraptionEntity entity){if(entity.level().getEntity(entity.getId())!=entity)return;var entries=loaded(entity.level());var previous=entries.get(entity.getId());if(previous==null||previous.get()!=entity)entries.put(entity.getId(),new WeakReference<>(entity));}
    public static void tick(Level level){
        for(var iterator=loaded(level).values().iterator();iterator.hasNext();){var entity=iterator.next().get();if(entity==null||!entity.isAlive()||entity.getContraption()==null){iterator.remove();continue;}entity.beginCollisionTick();ContraptionCollider.collideEntities(entity);entity.finishCollisionTick();}
    }
    public static List<AbstractContraptionEntity> collidingWith(net.minecraft.world.entity.Entity entity){
        return loaded(entity.level()).values().stream().map(WeakReference::get)
            .filter(owner->owner!=null&&owner.isAlive()&&owner.getContraption()!=null
                &&owner.collidingEntities.containsKey(entity)).toList();
    }
    private ContraptionHandler(){}
}
