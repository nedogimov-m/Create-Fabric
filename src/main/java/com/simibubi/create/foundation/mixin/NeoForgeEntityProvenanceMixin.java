package com.simibubi.create.foundation.mixin;

import com.mojang.serialization.Codec;
import com.simibubi.create.foundation.utility.NeoForgeEntityProvenance;
import com.simibubi.create.foundation.utility.NeoForgeEntityProvenanceOwner;
import com.simibubi.create.foundation.utility.NeoForgeEntityPersistentDataImport;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real native entity owner persists original provenance separately from native LOAD. */
@Mixin(Entity.class)
public abstract class NeoForgeEntityProvenanceMixin implements NeoForgeEntityProvenanceOwner {
    @Unique private NeoForgeEntityProvenance.SpawnOrigin create$neoForgeSpawnOrigin;
    @Unique private NeoForgeEntityPersistentDataImport.Historical create$neoForgePersistentData;
    @Override public Optional<NeoForgeEntityProvenance.SpawnOrigin> create$neoForgeSpawnOrigin() { return Optional.ofNullable(create$neoForgeSpawnOrigin); }
    @Inject(method = "load", at = @At("HEAD"))
    private void create$loadHistoricalProvenance(ValueInput input, CallbackInfo callback) {
        if (input.read(NeoForgeEntityProvenance.SOURCE_KEY, Codec.PASSTHROUGH).isPresent())
            throw new IllegalArgumentException("Original NeoForge entity spawn metadata requires genuine pre-factory migration");
        var raw = input.read(NeoForgeEntityProvenance.KEY, Codec.PASSTHROUGH);
        NeoForgeEntityProvenance.SpawnOrigin proposal = null;
        if (raw.isPresent()) {
            if (!((Object) this instanceof Mob)) throw new IllegalArgumentException("Native source spawn provenance requires an actual Mob owner");
            var value = raw.get().convert(NbtOps.INSTANCE).getValue();
            if (!(value instanceof CompoundTag tag)) throw new IllegalArgumentException("Present native historical entity provenance must be a compound");
            proposal = NeoForgeEntityProvenance.read(tag);
        }
        create$neoForgeSpawnOrigin = proposal;
    }
    @Inject(method = "load", at = @At("RETURN"))
    private void create$loadHistoricalPersistentData(ValueInput input, CallbackInfo callback) {
        var raw = input.read(NeoForgeEntityPersistentDataImport.KEY, Codec.PASSTHROUGH);
        NeoForgeEntityPersistentDataImport.Historical proposal = null;
        if (raw.isPresent()) {
            var value = raw.get().convert(NbtOps.INSTANCE).getValue();
            if (!(value instanceof CompoundTag tag)) throw new IllegalArgumentException("Present native source persistent data must be a compound");
            proposal = NeoForgeEntityPersistentDataImport.read(tag);
            proposal = NeoForgeEntityPersistentDataImport.admitPendingFan((Entity) (Object) this, input, proposal);
        }
        create$neoForgePersistentData = proposal;
    }
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void create$saveHistoricalProvenance(ValueOutput output, CallbackInfo callback) {
        if (create$neoForgePersistentData != null) output.store(NeoForgeEntityPersistentDataImport.KEY, CompoundTag.CODEC, NeoForgeEntityPersistentDataImport.write(create$neoForgePersistentData));
        if (create$neoForgeSpawnOrigin != null) output.store(NeoForgeEntityProvenance.KEY, NeoForgeEntityProvenance.CODEC, create$neoForgeSpawnOrigin);
    }
}
