package com.simibubi.create.content.trains.entity;

import com.simibubi.create.foundation.mixin.accessor.CarriageNativeSourceManagerAccessor;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;

/** Proof supplied by actual native storage/unload calls, never by loading chunks or guessing UUIDs. */
public final class CarriageNativeSources {
    private record Original(Entity actual,UUID id,int entityId,ServerLevel world){}
    private static final ThreadLocal<ArrayDeque<Scope>> SOURCES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Batch>> BATCHES=ThreadLocal.withInitial(ArrayDeque::new);
    public static final class Batch implements AutoCloseable {
        private final Object manager;private final long chunk;private final Set<Entity> retired=Collections.newSetFromMap(new IdentityHashMap<>());private boolean refused;
        private Batch(Object manager,long chunk){this.manager=manager;this.chunk=chunk;BATCHES.get().push(this);}
        public boolean refused(){return refused;}
        @Override public void close(){if(BATCHES.get().pop()!=this)throw new IllegalStateException("Native save/unload batches close in invocation order");if(BATCHES.get().isEmpty())BATCHES.remove();}
    }
    public static Batch batch(){return new Batch(null,0);}
    public static Batch batch(Object manager,long chunk){return new Batch(manager,chunk);}
    private static boolean storedCurrent(Object manager,Entity actual){
        if(!(actual.level() instanceof ServerLevel world)||actual.isRemoved()||((ServerLevelEntityManagerAccessor)world).create$getEntityManager()!=manager)return false;
        var source=(CarriageNativeSourceManagerAccessor)manager;if(!source.create$getKnownSourceUuids().contains(actual.getUUID()))return false;var section=source.create$getSourceSections().getSection(SectionPos.asLong(actual.blockPosition()));return section!=null&&section.getEntities().anyMatch(member->member==actual);
    }
    /** Actual native eligibility must still visit unsaved managed roots for its real unload callback. */
    public static boolean includeManagedSource(EntityAccess entry){
        if(!(entry instanceof CarriageContraptionEntity front)||!front.hasOwnedStructure()||front.isIndependentNativeOwner())return false;
        for(var batch:BATCHES.get())if(batch.manager!=null&&net.minecraft.world.level.ChunkPos.pack(front.blockPosition())==batch.chunk&&storedCurrent(batch.manager,front))return true;return false;
    }
    public static void refuse(){for(var batch:BATCHES.get())batch.refused=true;}
    public static void retired(Entity actual){for(var batch:BATCHES.get())batch.retired.add(actual);}
    public static boolean alreadyRetired(Entity actual){for(var batch:BATCHES.get())if(batch.retired.contains(actual))return actual.getRemovalReason()==Entity.RemovalReason.UNLOADED_TO_CHUNK;return false;}
    public static final class Scope implements AutoCloseable {
        private final Object manager;private final Map<Entity,Original> originals;
        private Scope(Object manager,List<Entity> actuals){
            this.manager=manager;var prepared=new IdentityHashMap<Entity,Original>();
            for(var actual:actuals)if(actual.level() instanceof ServerLevel world&&!actual.isRemoved()){
                if(((ServerLevelEntityManagerAccessor)world).create$getEntityManager()!=manager)throw new IllegalArgumentException("Actual native manager/source world disagreement");
                prepared.put(actual,new Original(actual,actual.getUUID(),actual.getId(),world));
            }
            originals=Collections.unmodifiableMap(prepared);SOURCES.get().push(this);
        }
        private boolean current(Entity actual){
            var original=originals.get(actual);if(original==null||actual.isRemoved()||actual.level()!=original.world()||actual.getId()!=original.entityId()||!actual.getUUID().equals(original.id())||((ServerLevelEntityManagerAccessor)original.world()).create$getEntityManager()!=manager)return false;
            var source=(CarriageNativeSourceManagerAccessor)manager;if(!source.create$getKnownSourceUuids().contains(original.id()))return false;
            var section=source.create$getSourceSections().getSection(SectionPos.asLong(actual.blockPosition()));return section!=null&&section.getEntities().anyMatch(member->member==actual);
        }
        @Override public void close(){if(SOURCES.get().pop()!=this)throw new IllegalStateException("Actual native source scopes close in invocation order");if(SOURCES.get().isEmpty())SOURCES.remove();}
    }
    public static Scope storage(Object manager,List<? extends EntityAccess> entries){
        var sources=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());for(var entry:entries)if(entry instanceof Entity entity){
            // Native storage also owns a still-live detached original after a partial unlink.
            // Its actual section/UUID witness must remain visible during this real storage call.
            entity.getSelfAndPassengers().forEach(sources::add);if(entity.getRootVehicle() instanceof CarriageContraptionEntity front)front.getSelfAndPassengers().forEach(sources::add);
        }return new Scope(manager,List.copyOf(sources));
    }
    public static Scope unloading(Object manager,EntityAccess entry){
        if(entry instanceof Entity actual){Entity root=actual.getRootVehicle();return new Scope(manager,(root instanceof CarriageContraptionEntity?root:actual).getSelfAndPassengers().toList());}return new Scope(manager,List.of());
    }
    public static boolean current(Entity actual){
        if(!(actual.level() instanceof ServerLevel world)||actual.isRemoved())return false;
        if(world.getEntity(actual.getUUID())==actual&&LoadedEntityAccess.isReady(world,actual.blockPosition()))return true;
        for(var scope:SOURCES.get())if(scope.current(actual))return true;return false;
    }
    public static Entity lookup(ServerLevel world,UUID id){
        var visible=world.getEntity(id);if(visible!=null)return visible;Entity found=null;
        for(var scope:SOURCES.get())for(var original:scope.originals.values())if(original.world()==world&&original.id().equals(id)&&scope.current(original.actual())){if(found!=null&&found!=original.actual())return null;found=original.actual();}return found;
    }
    /** False withholds the native source and its callback; the enclosing batch remains queued. */
    public static boolean prepareUnload(EntityAccess source){
        if(!(source instanceof Entity actual)||alreadyRetired(actual))return true;
        if(actual instanceof CarriageContraptionEntity front)return front.prepareNativePassengerUnload();
        if(actual.getRootVehicle() instanceof CarriageContraptionEntity front&&!front.isIndependentNativeOwner())return front.prepareNativeNpcRootUnload(actual);
        return true;
    }
    private CarriageNativeSources(){}
}
