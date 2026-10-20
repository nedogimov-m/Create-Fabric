package com.simibubi.create.foundation.utility;

import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;

/**
 * Transfers a module-prepared endpoint at actual native section insertion, before onCreated.
 * Both source functions must be callback-free; prepare the complete commit off-world.
 * A return value, tracking callback or removed prospective entity cannot prove acceptance.
 */
public final class NativeEntityAcceptance implements AutoCloseable {
    private static final ThreadLocal<IdentityHashMap<Entity,NativeEntityAcceptance>> ACTIVE=
        ThreadLocal.withInitial(IdentityHashMap::new);
    private final Entity entity;
    private final ServerLevel level;
    private final Object manager;
    private final UUID uuid;
    private final int entityId;
    private final BooleanSupplier validSource;
    private final Consumer<Insertion> commit;
    private Insertion insertion;
    private Set<UUID> nativeUuidOwner;
    private UUID nativeUuid;
    private boolean attempted,insideNativeAdd,nativeStarted,validated,sectionValidated,closed;

    private NativeEntityAcceptance(Entity entity,BooleanSupplier validSource,Consumer<Insertion> commit){
        if(!(entity.level() instanceof ServerLevel server)||!server.getServer().isSameThread())
            throw new IllegalArgumentException("An actual server-thread entity endpoint is required");
        if(entity.isRemoved()||server.getEntity(entity.getUUID())!=null||!LoadedEntityAccess.isReady(server,entity.blockPosition()))
            throw new IllegalStateException("Actual empty loaded native endpoint required");
        this.entity=entity;level=server;manager=((ServerLevelEntityManagerAccessor)server).create$getEntityManager();
        uuid=entity.getUUID();entityId=entity.getId();this.validSource=Objects.requireNonNull(validSource);this.commit=Objects.requireNonNull(commit);
        if(ACTIVE.get().putIfAbsent(entity,this)!=null)throw new IllegalStateException("Native endpoint already has an active handoff");
    }
    public static NativeEntityAcceptance prepare(Entity entity,BooleanSupplier validSource,Consumer<Insertion> commit){
        return new NativeEntityAcceptance(entity,validSource,commit);
    }
    /** One real attempt; insertion() remains evidence if a subsequent native callback throws. */
    public boolean add(){
        if(closed||attempted||!level.getServer().isSameThread()||ACTIVE.get().get(entity)!=this)
            throw new IllegalStateException("A native endpoint may be offered once on its actual server thread");
        attempted=true;
        // The fertilizer dream defers entities for later replay, outside this real
        // ownership scope. Leave the source intact instead of queuing an unowned copy.
        if(TreeFertilizerSimulation.active(level)!=null)return false;
        insideNativeAdd=true;
        try{return level.addFreshEntity(entity);}finally{insideNativeAdd=false;}
    }
    public Insertion insertion(){return insertion;}
    public static boolean beforeNativeAdd(Object actualManager,EntityAccess candidate,boolean loadedFromDisk){
        if(!(candidate instanceof Entity actual))return true;
        var scope=ACTIVE.get().get(actual);if(scope==null)return true;
        if(scope.nativeStarted)return false;
        scope.nativeStarted=true;
        scope.validated=false;
        if(scope.closed||!scope.insideNativeAdd||scope.insertion!=null||loadedFromDisk||scope.manager!=actualManager
            ||actual.level()!=scope.level||actual.getId()!=scope.entityId||!actual.getUUID().equals(scope.uuid)
            ||actual.isRemoved()||!LoadedEntityAccess.isReady(scope.level,actual.blockPosition())||!scope.validSource.getAsBoolean())return false;
        scope.validated=true;return true;
    }
    /** Record only the exact UUID that vanilla actually reserved successfully. */
    public static void afterNativeUuidAdd(Object actualManager,EntityAccess candidate,Set<UUID> actualOwner,UUID actualUuid){
        if(!(candidate instanceof Entity actual))return;
        var scope=ACTIVE.get().get(actual);
        if(scope==null||!scope.insideNativeAdd||scope.manager!=actualManager||!scope.validated)return;
        if(scope.nativeUuidOwner!=null)throw new IllegalStateException("A prospective endpoint cannot reserve two native UUIDs");
        scope.nativeUuidOwner=actualOwner;scope.nativeUuid=actualUuid;
    }
    /** Last callback-free source check, after UUID hooks and immediately before section admission. */
    public static boolean beforeNativeSectionAdd(Object actualManager,EntityAccess candidate){
        if(!(candidate instanceof Entity actual))return true;
        var scope=ACTIVE.get().get(actual);if(scope==null)return true;
        boolean current=false;
        try{
            current=!scope.closed&&scope.insideNativeAdd&&scope.nativeStarted&&scope.validated
                &&scope.insertion==null&&scope.manager==actualManager&&scope.nativeUuidOwner!=null
                &&scope.nativeUuidOwner.contains(scope.nativeUuid)&&scope.uuid.equals(scope.nativeUuid)
                &&actual.level()==scope.level&&actual.getId()==scope.entityId&&actual.getUUID().equals(scope.uuid)
                &&!actual.isRemoved()&&LoadedEntityAccess.isReady(scope.level,actual.blockPosition())
                &&scope.validSource.getAsBoolean();
            if(current)scope.sectionValidated=true;
            return current;
        }finally{
            if(!current){
                // No section/callback owns this reservation yet. Never touch another entity's UUID.
                if(scope.manager==actualManager&&scope.nativeUuidOwner!=null)scope.nativeUuidOwner.remove(scope.nativeUuid);
                scope.validated=false;
            }
        }
    }
    /** Generic copied-drop interceptors must leave this exact prospective endpoint to native admission. */
    public static boolean isPreparedEndpointInCurrentAdd(Entity actual){
        var scope=ACTIVE.get().get(actual);
        return scope!=null&&scope.insideNativeAdd&&!scope.closed&&scope.attempted&&scope.entity==actual
            &&actual.level()==scope.level&&actual.getId()==scope.entityId&&actual.getUUID().equals(scope.uuid)
            &&((ServerLevelEntityManagerAccessor)scope.level).create$getEntityManager()==scope.manager;
    }
    /** Accepted endpoint during early onCreated, before its visible index exists. */
    public static boolean isAcceptedSourceInCurrentAdd(Entity actual){
        var scope=ACTIVE.get().get(actual);
        return scope!=null&&scope.insideNativeAdd&&scope.insertion!=null&&!scope.closed&&!actual.isRemoved()
            &&actual.level()==scope.level&&((ServerLevelEntityManagerAccessor)scope.level).create$getEntityManager()==scope.manager
            &&scope.insertion.section.getEntities().anyMatch(member->member==actual);
    }
    /**
     * Called only after this exact native add's LevelCallback.onCreated has returned.
     * A removed endpoint with a completed paid section receipt was historically accepted;
     * vanilla must not put it back into tracking after its installed removal callback ran.
     * This does not test source validity again: its material already belongs to the receipt.
     */
    public static boolean removedAfterNativeCreation(Object actualManager,EntityAccess candidate){
        if(!(candidate instanceof Entity actual))return false;
        var scope=ACTIVE.get().get(actual);
        return scope!=null&&scope.entity==actual&&!scope.closed&&scope.attempted&&scope.insideNativeAdd
            &&scope.nativeStarted&&scope.validated&&scope.sectionValidated&&scope.manager==actualManager
            &&scope.level.getServer().isSameThread()&&actual.level()==scope.level
            &&((ServerLevelEntityManagerAccessor)scope.level).create$getEntityManager()==actualManager
            &&actual.getId()==scope.entityId&&actual.getUUID().equals(scope.uuid)
            &&scope.nativeUuidOwner!=null&&scope.uuid.equals(scope.nativeUuid)
            &&scope.insertion!=null&&scope.insertion.scope==scope
            &&scope.insertion.committed&&!scope.insertion.committing&&actual.isRemoved();
    }
    /** Invoked only around the actual EntitySection.add in PersistentEntitySectionManager. */
    public static void afterNativeSectionAdd(Object actualManager,EntitySection<?> actualSection,EntityAccess candidate){
        if(!(candidate instanceof Entity actual))return;
        var scope=ACTIVE.get().get(actual);if(scope==null||!scope.insideNativeAdd||scope.manager!=actualManager||!scope.validated||!scope.sectionValidated||scope.insertion!=null)return;
        if(!actualSection.getEntities().anyMatch(member->member==actual))return;
        var proof=new Insertion(scope,actualSection);scope.insertion=proof;
        proof.committing=true;
        try{scope.commit.accept(proof);proof.committed=true;}finally{proof.committing=false;}
    }
    /** Actual historical admission, even when a later callback consumes or removes the endpoint. */
    public static final class Insertion {
        private final NativeEntityAcceptance scope;
        private final EntitySection<?> section;
        private boolean committing,committed;
        private Insertion(NativeEntityAcceptance scope,EntitySection<?> section){this.scope=scope;this.section=section;}
        public Entity entity(){return scope.entity;}
        public void requireCommitting(Entity actual){
            if(!committing||scope.insertion!=this||!scope.insideNativeAdd||scope.closed||actual!=scope.entity
                ||((ServerLevelEntityManagerAccessor)scope.level).create$getEntityManager()!=scope.manager
                ||!section.getEntities().anyMatch(member->member==actual))throw new IllegalStateException("Actual current native section insertion proof required");
        }
    }
    @Override public void close(){
        if(!level.getServer().isSameThread()||insideNativeAdd)throw new IllegalStateException("Close admission on its server thread after native insertion");
        if(!closed){closed=true;ACTIVE.get().remove(entity,this);if(ACTIVE.get().isEmpty())ACTIVE.remove();}
    }
}
