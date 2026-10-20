package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.contraptions.NeoForgeEntityChunkImport;
import com.simibubi.create.content.contraptions.NeoForgePlayerVehicleContext;
import com.simibubi.create.content.contraptions.NeoForgeStandaloneEntityImport;
import com.simibubi.create.foundation.mixin.accessor.CarriageNativeSourceManagerAccessor;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.*;

/** PlayerData alone owns a genuine player vehicle tree; a matching old region row cannot clone it. */
public final class NeoForgePlayerRegionImport {
    private record Pending(Prepared preparation, EntityStorage storage, SimpleRegionStorage region) {}
    private static final Map<ChunkEntities<?>,Pending> PENDING=Collections.synchronizedMap(new IdentityHashMap<>());
    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{synchronized(PENDING){PENDING.entrySet().removeIf(entry->entry.getValue().preparation.level.getServer()==server);}});
    }
    public static final class Prepared {
        private final ServerLevel level;
        private final ChunkPos requested;
        private final CompoundTag original,converted;
        private final List<NeoForgePlayerStorageImport.RegionVehicleFile> files;
        private final Set<UUID> suppressed,materialIds;
        private Prepared(ServerLevel level,ChunkPos requested,CompoundTag original,CompoundTag converted,
                List<NeoForgePlayerStorageImport.RegionVehicleFile> files,Set<UUID> suppressed,Set<UUID> materialIds) {
            this.level=level;this.requested=requested;this.original=original.copy();this.converted=converted.copy();
            this.files=List.copyOf(files);this.suppressed=Set.copyOf(suppressed);this.materialIds=Set.copyOf(materialIds);
        }
        public CompoundTag converted() { return converted.copy(); }
        /** Pure guard, shared by preparation and the real before-first-add admission boundary. */
        public void requireCurrent() {
            var server=level.getServer();
            if(!server.isSameThread()||server.getLevel(level.dimension())!=level)throw invalid("Original player-region token lost its actual server-thread/level owner");
            for(var file:files)file.requireCurrent();
            var identities=new HashSet<>(materialIds);for(var file:files)identities.add(file.player());
            for(var current:server.getAllLevels()) {
                var manager=((ServerLevelEntityManagerAccessor)(Object)current).create$getEntityManager();
                var known=((CarriageNativeSourceManagerAccessor)(Object)manager).create$getKnownSourceUuids();
                if(!Collections.disjoint(identities,known))throw invalid("An actual native entity/player owner supersedes the old player-owned region vehicle tree");
            }
        }
        /** Bind only the exact native parser result that the real manager will later dequeue. */
        public ChunkEntities<Entity> bind(ChunkEntities<Entity> actual,EntityStorage storage,SimpleRegionStorage region) {
            if(!actual.getPos().equals(requested)||((NeoForgeOriginalImportFailure.EntityStorageAccess)(Object)storage).create$originalImportRegionStorage()!=region)
                throw invalid("Original player-region token changed its exact native chunk/storage owner");
            var actualIds=actual.getEntities().map(Entity::getUUID).toList();var expected=new HashSet<>(materialIds);expected.removeAll(suppressed);
            if(actualIds.size()!=new HashSet<>(actualIds).size()||!expected.equals(new HashSet<>(actualIds)))throw invalid("Actual native parser lost/duplicated an original entity body or instantiated a player-owned duplicate");
            synchronized(PENDING){if(PENDING.putIfAbsent(actual,new Pending(this,storage,region))!=null)throw invalid("Actual native entity batch already has a player-region token");}
            return actual;
        }
    }
    /** Pure raw/typed preparation on the native deserializer; live ownership is checked at actual admission. */
    public static Prepared prepare(CompoundTag original,ChunkPos requested,ServerLevel level) {
        var converted=NeoForgeEntityChunkImport.convert(original,requested,level.registryAccess());
        var sources=list(original,"Entities");var candidates=NeoForgePlayerStorageImport.regionVehicleFiles(level.getServer());
        var suppressed=new HashSet<UUID>();var materialIds=new HashSet<UUID>();var claims=new ArrayList<NeoForgePlayerStorageImport.RegionVehicleFile>();var kept=new ListTag();
        for(int index=0;index<sources.size();index++) {
            var root=(CompoundTag)sources.get(index);var ids=treeIds(root);var overlaps=new ArrayList<NeoForgePlayerStorageImport.RegionVehicleFile>();
            materialIds.addAll(ids);
            for(var file:candidates)if(!Collections.disjoint(ids,treeIds(file.originalRoot())))overlaps.add(file);
            if(overlaps.isEmpty()){kept.add(list(converted,"Entities").get(index).copy());continue;}
            if(overlaps.size()!=1)throw invalid("Original region vehicle material has multiple current/historical player-file owners");
            var file=overlaps.getFirst();file.requireRawCurrent();var type=Identifier.parse(string(root,"id"));
            if(!file.dimension().equals(level.dimension().identifier()))throw invalid("Player-owned source vehicle belongs to another actual region dimension");
            // These Create roots override hasExactlyOnePlayerPassenger=false in the pinned writer.
            // A genuine player-owned ordinary root is vanilla, or SeatEntity which inherits vanilla persistence.
            if(!type.getNamespace().equals("minecraft")&&!type.toString().equals("create:seat"))throw invalid("Original RootVehicle region custody cannot use a source root that overrides player-owned persistence");
            if(!root.equals(file.originalRoot()))throw invalid("Original player-owned region root differs from its exact current canonical RootVehicle tree");
            var player=file.originalPlayer();NeoForgePlayerVehicleContext.validateCompletePlayer(player);
            // Validate its actual Attach, complete source tree and seat topology without a player/entity factory.
            NeoForgeStandaloneEntityImport.playerRootVehicle(player,NeoForgeSavedDataImport.SOURCE_DATA_VERSION,level.registryAccess());
            if(!Collections.disjoint(suppressed,ids))throw invalid("Two original region roots claim one player-owned material tree");
            suppressed.addAll(ids);claims.add(file);
        }
        converted.put("Entities",kept);var prepared=new Prepared(level,requested,original,converted,claims,suppressed,materialIds);
        for(var file:claims)file.requireRawCurrent();return prepared;
    }
    /** Exact native processPendingLoads boundary: before stream iteration/addEntity and LOADED status. */
    public static void beforeAdmission(EntityPersistentStorage<?> actualStorage,ChunkEntities<?> actualBatch) {
        Pending pending;synchronized(PENDING){pending=PENDING.remove(actualBatch);}if(pending==null)return;
        var prepared=pending.preparation;
        try {
            if(actualStorage!=pending.storage||!actualBatch.getPos().equals(prepared.requested))throw invalid("Actual player-region batch reached a different native storage/request owner");
            var failed=NeoForgeOriginalImportFailure.first(pending.region);if(failed!=null)throw failed.forNativeLoad(prepared.requested);
            var current=pending.region.read(prepared.requested).join();
            if(current.isEmpty()||!current.get().equals(prepared.original))throw invalid("Actual original entity-region record changed before first native batch publication");
            prepared.requireCurrent();
        }catch(RuntimeException error){throw NeoForgeOriginalImportFailure.refuse(pending.region,prepared.level,prepared.requested,prepared.original,error);}
    }
    private static Set<UUID> treeIds(CompoundTag root) {
        var ids=new HashSet<UUID>();var queue=new ArrayDeque<CompoundTag>();queue.add(root);
        while(!queue.isEmpty()) {
            var node=queue.removeFirst();if(ids.size()>=1024||!ids.add(uuid(node,"UUID")))throw invalid("Original player-region material tree has duplicate/excessive actual UUID owners");
            if(node.contains("Passengers"))for(var value:list(node,"Passengers")){if(!(value instanceof CompoundTag child))throw invalid("Malformed original player-region physical passenger tree");queue.add(child);}
        }
        return ids;
    }
    private NeoForgePlayerRegionImport() {}
}
