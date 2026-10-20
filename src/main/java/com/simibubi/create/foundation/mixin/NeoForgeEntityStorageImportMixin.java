package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import com.simibubi.create.foundation.utility.NeoForgeOriginalImportFailure;
import com.simibubi.create.foundation.utility.NeoForgePlayerRegionImport;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Real original header/dimension gate precedes native permissive position logs and every entity factory.
 * Install together with the exact native storage failure latch, save fences and bounded shutdown mixins.
 */
@Mixin(EntityStorage.class)
public abstract class NeoForgeEntityStorageImportMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;
    @WrapMethod(method="lambda$loadEntities$0")
    private ChunkEntities<Entity> create$prepareOriginalEntityChunk(ChunkPos actualRequested,Optional<CompoundTag> actualRaw,
            Operation<ChunkEntities<Entity>> original){
        var aborted=NeoForgeOriginalImportFailure.first(simpleRegionStorage);
        if(aborted!=null)throw aborted.forNativeLoad(actualRequested);
        if(actualRaw.isPresent()&&actualRaw.get().get("DataVersion") instanceof IntTag version
                &&version.intValue()==NeoForgeSavedDataImport.SOURCE_DATA_VERSION){
            NeoForgePlayerRegionImport.Prepared prepared;
            try{
                if(!level.dimension().equals(simpleRegionStorage.storageInfo().dimension()))
                    throw new IllegalArgumentException("Actual original entity-region dimension disagrees with its native level/storage owner");
                prepared=NeoForgePlayerRegionImport.prepare(actualRaw.get(),actualRequested,level);
            }catch(RuntimeException ownOriginalRefusal){
                throw NeoForgeOriginalImportFailure.refuse(simpleRegionStorage,level,actualRequested,actualRaw.get(),ownOriginalRefusal);
            }
            try{return prepared.bind(original.call(actualRequested,Optional.of(prepared.converted())),(EntityStorage)(Object)this,simpleRegionStorage);}
            catch(RuntimeException originalAdmissionFailure){throw NeoForgeOriginalImportFailure.refuse(simpleRegionStorage,level,actualRequested,actualRaw.get(),originalAdmissionFailure);}
        }
        return original.call(actualRequested,actualRaw);
    }
}
