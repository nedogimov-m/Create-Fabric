package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;

/** Pinned3955 vanilla non-container material owners. No entity factories, loot rolls or world writes. */
public final class NeoForgeVanillaBlockEntityImport {
    private static final Set<String> TYPES=Set.of("jukebox","lectern","beehive","mob_spawner",
        "brushable_block","decorated_pot","trial_spawner","vault");
    private static final Set<String> BASE=Set.of("id","x","y","z","keepPacked","components","NeoForgeData","neoforge:attachments");

    public static boolean supports(Identifier type){return type.getNamespace().equals("minecraft")&&TYPES.contains(type.getPath());}

    public static CompoundTag convert(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries){
        return convert(type,source,version,registries,null);
    }
    public static CompoundTag convert(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        var prepared=prepareForVanillaFixer(type,source,version,registries,scope);prepared.putString("id",type.toString());
        Tag fixed=DataFixers.getDataFixer().update(References.BLOCK_ENTITY,new Dynamic<Tag>(NbtOps.INSTANCE,prepared),
            version,SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if(!(fixed instanceof CompoundTag result))throw invalid("Actual vanilla material BE fixer returned a noncompound");
        return finishAfterVanillaFixer(type,source,result,version,registries,scope);
    }

    /** Leaves vanilla fields in3955 form; only typed Create item branches are prepared before enclosing DFU. */
    public static CompoundTag prepareForVanillaFixer(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries){
        return prepareForVanillaFixer(type,source,version,registries,null);
    }
    public static CompoundTag prepareForVanillaFixer(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        validate(type,source,version);var result=source.copy();
        transform(type,source,result,version,registries,true,scope);
        for(String extension:Set.of("NeoForgeData","neoforge:attachments"))result.remove(extension);
        if(source.contains("components"))result.put("components",NeoForgeItemComponentImport.prepareMapForVanillaFixer(compound(source,"components"),version,registries,child(scope,"components",0)));
        return result;
    }

    /** Repairs only actual typed original leaves after the one containing BLOCK_ENTITY/ITEM_STACK DFU. */
    public static CompoundTag finishAfterVanillaFixer(Identifier type,CompoundTag source,CompoundTag actualFixed,int version,HolderLookup.Provider registries){
        return finishAfterVanillaFixer(type,source,actualFixed,version,registries,null);
    }
    public static CompoundTag finishAfterVanillaFixer(Identifier type,CompoundTag source,CompoundTag actualFixed,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        validate(type,source,version);var result=actualFixed.copy();
        if(!type.equals(Identifier.parse(string(result,"id"))))throw invalid("Actual vanilla fixer changed the original material BE owner");
        for(String coordinate:Set.of("x","y","z"))if(source.contains(coordinate)&&integer(source,coordinate)!=integer(result,coordinate))
            throw invalid("Actual material BE fixer changed a genuine source coordinate");
        transform(type,source,result,version,registries,false,scope);
        if(source.contains("components"))result.put("components",NeoForgeItemComponentImport.finishMapAfterVanillaFixer(
            compound(source,"components"),result.getCompoundOrEmpty("components"),version,registries,child(scope,"components",0)));
        for(String extension:Set.of("NeoForgeData","neoforge:attachments"))result.remove(extension);
        return result;
    }

    private static void validate(Identifier type,CompoundTag source,int version){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION||!supports(type))throw invalid("Unreviewed vanilla material BE source/version "+type);
        if(source.contains("id")&&!type.equals(Identifier.parse(string(source,"id"))))throw invalid("Contradictory original material BE identity");
        var allowed=new HashSet<>(BASE);allowed.addAll(switch(type.getPath()){
            case "jukebox"->Set.of("RecordItem","ticks_since_song_started");
            case "lectern"->Set.of("Book","Page");
            case "beehive"->Set.of("bees","flower_pos");
            case "mob_spawner"->Set.of("Delay","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange","SpawnData","SpawnPotentials");
            case "brushable_block"->Set.of("item","LootTable","LootTableSeed","hit_direction");
            case "decorated_pot"->Set.of("item","LootTable","LootTableSeed","sherds");
            case "trial_spawner"->Set.of("normal_config","ominous_config","registered_players","current_mobs","cooldown_ends_at","next_mob_spawns_at","total_mobs_spawned","spawn_data","ejecting_loot_table","target_cooldown_length","required_player_range");
            case "vault"->Set.of("config","shared_data","server_data");
            default->throw invalid("Unreviewed vanilla physical owner");
        });
        keys(source,"vanilla "+type.getPath(),allowed.toArray(String[]::new));
        for(String coordinate:Set.of("x","y","z"))if(source.contains(coordinate)&&!(source.get(coordinate) instanceof IntTag))throw invalid("Original BE coordinate is not an int");
        if(source.contains("keepPacked"))bool(source,"keepPacked");
        for(String extension:Set.of("NeoForgeData","neoforge:attachments"))if(source.contains(extension)&&!compound(source,extension).isEmpty())
            throw invalid("Original vanilla BE extension needs its own genuine native owner: "+extension);
    }

    private static void transform(Identifier type,CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries,boolean prepare,NeoForgeMigrationScope scope){
        switch(type.getPath()){
            case "jukebox"->{itemField(source,result,"RecordItem",version,registries,prepare,scope);longField(source,"ticks_since_song_started");}
            case "lectern"->{itemField(source,result,"Book",version,registries,prepare,scope);intField(source,"Page");}
            case "brushable_block","decorated_pot"->{
                if(source.contains("LootTable")){
                    Identifier.parse(string(source,"LootTable"));
                    if(source.contains("item")&&!compound(source,"item").isEmpty())throw invalid("Original deferred loot and material item cannot have the same block owner");
                }
                longField(source,"LootTableSeed");itemField(source,result,"item",version,registries,prepare,scope);
                if(source.contains("hit_direction")&&(integer(source,"hit_direction")<0||integer(source,"hit_direction")>5))throw invalid("Original brush hit direction is outside its actual six sides");
                if(source.contains("sherds")){
                    var sherds=list(source,"sherds");if(sherds.size()>4)throw invalid("Original decorated pot has more than four actual decorations");
                    for(Tag sherd:sherds)if(!(sherd instanceof StringTag name)||!BuiltInRegistries.ITEM.containsKey(Identifier.parse(name.value())))throw invalid("Unknown original pot decoration item");
                    if(!prepare){
                        var decorations=PotDecorations.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),result.get("sherds")).getOrThrow();
                        var sides=java.util.List.of(decorations.back(),decorations.left(),decorations.right(),decorations.front());
                        for(int side=0;side<4;side++){
                            var expected=Identifier.parse(side<sherds.size()?((StringTag)sherds.get(side)).value():"minecraft:brick");
                            var actual=sides.get(side).orElseThrow(()->invalid("Native pot lost an original decoration side"));
                            if(actual.item().value()!=BuiltInRegistries.ITEM.getValue(expected)||actual.count()!=1||!actual.components().isEmpty())
                                throw invalid("Native pot decoration changed original side/item/count/components");
                        }
                    }
                }
            }
            case "beehive"->{
                if(source.contains("flower_pos"))BlockPos.CODEC.parse(NbtOps.INSTANCE,source.get("flower_pos")).getOrThrow();
                if(source.contains("bees")){
                    var old=list(source,"bees");var inert=NeoForgeInertHiveSlots.fromOriginal(old);
                    var actual=prepare?NeoForgeInertHiveSlots.realOriginalRows(old):list(result,"bees");
                    if(old.size()-inert.size()!=actual.size())throw invalid("Actual vanilla fixer changed genuine original hive occupant count");
                    var carried=result.contains(NeoForgeInertHiveSlots.KEY)?NeoForgeInertHiveSlots.read(compound(result,NeoForgeInertHiveSlots.KEY)):NeoForgeInertHiveSlots.EMPTY;
                    if(!prepare&&!inert.equals(carried))throw invalid("Actual vanilla fixer changed original inert hive occupancy/timers/provenance");
                    if(prepare&&!inert.isEmpty())result.put(NeoForgeInertHiveSlots.KEY,inert.write());
                    var bees=new ListTag();int nativeIndex=0;
                    for(int i=0;i<old.size();i++){
                        var row=row(old.get(i),"hive occupant");keys(row,"hive occupant","entity_data","ticks_in_hive","min_ticks_in_hive");
                        if(NeoForgeInertHiveSlots.isInertOriginal(row))continue;
                        integer(row,"ticks_in_hive");integer(row,"min_ticks_in_hive");var next=row(actual.get(nativeIndex++),"actual hive occupant").copy();
                        for(String timer:Set.of("ticks_in_hive","min_ticks_in_hive"))if(integer(row,timer)!=integer(next,timer))throw invalid("Actual vanilla fixer changed original hive residence time");
                        if(row.contains("entity_data"))next.put("entity_data",entity(compound(row,"entity_data"),next.getCompoundOrEmpty("entity_data"),version,registries,prepare,child(child(scope,"bee",i),"entity_data",0)));
                        bees.add(next);
                    }
                    if(!prepare)canonical(BeehiveBlockEntity.Occupant.LIST_CODEC,bees,registries);
                    result.put("bees",bees);
                }
            }
            case "mob_spawner"->{
                for(String scalar:Set.of("Delay","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"))
                    if(source.contains(scalar)&&!(source.get(scalar) instanceof ShortTag))throw invalid("Original BaseSpawner scalar lost its source short type: "+scalar);
                if(source.contains("SpawnData"))result.put("SpawnData",spawn(compound(source,"SpawnData"),prepare?compound(source,"SpawnData"):compound(result,"SpawnData"),version,registries,prepare,child(scope,"spawn_data",0)));
                if(source.contains("SpawnPotentials"))result.put("SpawnPotentials",potentials(list(source,"SpawnPotentials"),prepare?list(source,"SpawnPotentials"):list(result,"SpawnPotentials"),version,registries,prepare,child(scope,"spawn_potentials",0)));
            }
            case "trial_spawner"->{
                uuidSet(source,"registered_players");uuidSet(source,"current_mobs");
                longField(source,"cooldown_ends_at");longField(source,"next_mob_spawns_at");
                for(String scalar:Set.of("total_mobs_spawned","target_cooldown_length"))if(source.contains(scalar)&&integer(source,scalar)<0)throw invalid("Negative original trial counter");
                if(source.contains("required_player_range")&&(integer(source,"required_player_range")<1||integer(source,"required_player_range")>128))throw invalid("Original trial player range outside source codec");
                if(source.contains("ejecting_loot_table"))Identifier.parse(string(source,"ejecting_loot_table"));
                if(source.contains("spawn_data"))result.put("spawn_data",spawn(compound(source,"spawn_data"),prepare?compound(source,"spawn_data"):compound(result,"spawn_data"),version,registries,prepare,child(scope,"spawn_data",0)));
                for(String key:Set.of("normal_config","ominous_config"))if(source.contains(key)||key.equals("ominous_config")&&source.contains("normal_config")){
                    var old=effectiveTrialConfig(source,key);keys(old,"trial config","spawn_range","total_mobs","simultaneous_mobs","total_mobs_added_per_player","simultaneous_mobs_added_per_player","ticks_between_spawn","spawn_potentials","loot_tables_to_eject","items_to_drop_when_ominous");
                    var next=prepare?old.copy():trialConfig(result.get(key),registries);
                    if(old.contains("spawn_range")&&(integer(old,"spawn_range")<1||integer(old,"spawn_range")>128))throw invalid("Original trial spawn range outside source codec");
                    if(old.contains("ticks_between_spawn")&&integer(old,"ticks_between_spawn")<0)throw invalid("Negative original trial spawn interval");
                    for(String counter:Set.of("total_mobs","simultaneous_mobs","total_mobs_added_per_player","simultaneous_mobs_added_per_player"))if(old.contains(counter)&&floating(old,counter)<0)throw invalid("Negative original trial mob quota");
                    if(old.contains("items_to_drop_when_ominous"))Identifier.parse(string(old,"items_to_drop_when_ominous"));
                    if(old.contains("loot_tables_to_eject"))for(Tag value:list(old,"loot_tables_to_eject")){
                        var loot=row(value,"trial deferred loot weight");keys(loot,"trial deferred loot weight","data","weight");Identifier.parse(string(loot,"data"));
                        if(loot.contains("weight")&&integer(loot,"weight")<0)throw invalid("Negative original deferred loot weight");
                    }
                    if(old.contains("spawn_potentials"))next.put("spawn_potentials",potentials(list(old,"spawn_potentials"),prepare?list(old,"spawn_potentials"):listOrDefault(next,"spawn_potentials"),version,registries,prepare,false,child(child(scope,key,0),"spawn_potentials",0)));
                    if(!prepare)canonical(TrialSpawnerConfig.DIRECT_CODEC,next,registries);
                    result.put(key,next);
                }
            }
            case "vault"->{
                for(String key:Set.of("config","shared_data","server_data"))if(source.contains(key)){
                    var old=compound(source,key);var next=prepare?old.copy():compound(result,key).copy();
                    switch(key){
                        case "config"->{
                            keys(old,"vault config","loot_table","activation_range","deactivation_range","key_item","override_loot_table_to_display");
                            for(String resource:Set.of("loot_table","override_loot_table_to_display"))if(old.contains(resource))Identifier.parse(string(old,resource));
                            for(String range:Set.of("activation_range","deactivation_range"))if(old.contains(range))number(old,range);
                            itemField(old,next,"key_item",version,registries,prepare,child(scope,key,0));
                            if(!prepare)canonical(VaultConfig.CODEC,next,registries);
                        }
                        case "shared_data"->{
                            keys(old,"vault shared data","display_item","connected_players","connected_particles_range");
                            uuidSet(old,"connected_players");if(old.contains("connected_particles_range"))number(old,"connected_particles_range");
                            itemField(old,next,"display_item",version,registries,prepare,child(scope,key,0));
                            if(!prepare)canonical(VaultSharedData.CODEC,next,registries);
                        }
                        case "server_data"->{
                            keys(old,"vault server data","rewarded_players","state_updating_resumes_at","items_to_eject","total_ejections_needed");
                            uuidSet(old,"rewarded_players");longField(old,"state_updating_resumes_at");intField(old,"total_ejections_needed");
                            if(old.contains("items_to_eject")){
                                var items=list(old,"items_to_eject");var actual=prepare?items:listOrDefault(next,"items_to_eject");
                                if(items.size()!=actual.size())throw invalid("Actual vanilla fixer changed original vault queued reward count");
                                var rewards=new ListTag();for(int i=0;i<items.size();i++)rewards.add(stack(row(items.get(i),"vault reward"),row(actual.get(i),"actual vault reward"),version,registries,prepare,child(child(scope,key,0),"items_to_eject",i)));
                                next.put("items_to_eject",rewards);
                            }
                            if(!prepare)canonical(VaultServerData.CODEC,next,registries);
                        }
                    }
                    result.put(key,next);
                }
            }
            default->throw invalid("No reviewed vanilla material BE branch");
        }
    }

    private static CompoundTag trialConfig(Tag actual,HolderLookup.Provider registries){
        if(actual instanceof CompoundTag tag)return tag.copy();
        // Native DFU can intern an exact vanilla inline config in the current registry.
        // Resolve that actual holder, never synthesize a different spawn authority.
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        var holder=TrialSpawnerConfig.CODEC.parse(ops,actual).getOrThrow();
        return row(TrialSpawnerConfig.DIRECT_CODEC.encodeStart(ops,holder.value()).getOrThrow(),"actual registered trial config");
    }

    private static CompoundTag effectiveTrialConfig(CompoundTag source,String key){
        if(key.equals("normal_config"))return compound(source,key);
        // Actual3955 TrialSpawnerBlockEntity.loadAdditional merges normal into ominous.
        // Equal configurations are serialized by omitting the ominous override entirely.
        var effective=source.contains("normal_config")?compound(source,"normal_config").copy():new CompoundTag();
        if(source.contains(key))effective.merge(compound(source,key));
        return effective;
    }

    private static CompoundTag spawn(CompoundTag source,CompoundTag actual,int version,HolderLookup.Provider registries,boolean prepare,NeoForgeMigrationScope scope){
        return spawn(source,actual,version,registries,prepare,true,scope);
    }
    private static CompoundTag spawn(CompoundTag source,CompoundTag actual,int version,HolderLookup.Provider registries,boolean prepare,boolean schemaOwnsEntity,NeoForgeMigrationScope scope){
        keys(source,"spawn data","entity","custom_spawn_rules","equipment");var result=actual.copy();
        var oldEntity=compound(source,"entity");var actualEntity=actual.getCompoundOrEmpty("entity");var entityScope=child(scope,"entity",0);
        if(!prepare&&!schemaOwnsEntity&&!oldEntity.isEmpty()){
            // Actual26.3 V3689 still types only top-level spawn_potentials/spawn_data.
            // Genuine3955 nested trial configs are untyped remainder, so their templates
            // require their own ENTITY_TREE boundary. Never postprocess them as already fixed.
            if(!NeoForgeEntityItemImport.prepare(oldEntity,version,registries,entityScope).equals(actualEntity))
                throw invalid("Actual BE fixer unexpectedly rewrote an untyped source trial-config entity");
            result.put("entity",NeoForgeVanillaEntityImport.convert(oldEntity,version,registries,entityScope));
        }else result.put("entity",entity(oldEntity,actualEntity,version,registries,prepare,entityScope));
        if(!prepare)canonical(SpawnData.CODEC,result,registries);
        return result;
    }
    private static ListTag potentials(ListTag source,ListTag actual,int version,HolderLookup.Provider registries,boolean prepare,NeoForgeMigrationScope scope){
        return potentials(source,actual,version,registries,prepare,true,scope);
    }
    private static ListTag potentials(ListTag source,ListTag actual,int version,HolderLookup.Provider registries,boolean prepare,boolean schemaOwnsEntity,NeoForgeMigrationScope scope){
        if(source.size()!=actual.size())throw invalid("Actual vanilla fixer changed original spawn potential count/order");
        var result=new ListTag();
        for(int i=0;i<source.size();i++){
            var old=row(source.get(i),"spawn potential");keys(old,"spawn potential","data","weight");var next=row(actual.get(i),"actual spawn potential").copy();
            int weight=old.contains("weight")?integer(old,"weight"):1;
            if(weight<0||weight!=(next.contains("weight")?integer(next,"weight"):1))throw invalid("Actual vanilla fixer changed original spawn weight");
            next.put("data",spawn(compound(old,"data"),compound(next,"data"),version,registries,prepare,schemaOwnsEntity,child(child(scope,"potential",i),"data",0)));result.add(next);
        }
        if(!prepare)canonical(SpawnData.LIST_CODEC,result,registries);
        return result;
    }
    private static CompoundTag entity(CompoundTag source,CompoundTag actual,int version,HolderLookup.Provider registries,boolean prepare,NeoForgeMigrationScope scope){
        if(source.isEmpty()){
            if(!actual.isEmpty())throw invalid("Actual vanilla fixer invented an original default spawn/hive entity");
            return new CompoundTag();
        }
        // These are actual containing templates, including UUID-shaped source fields.
        // Only the separate complete saved-entity boundary may derive a world entity scope.
        var prepared=NeoForgeEntityItemImport.prepare(source,version,registries,scope);
        if(prepare)return prepared;
        // A native optional list can remain an untyped remainder when another genuine
        // row has an empty default entity{}. Only an EXACT complete prepared image
        // permits a separate original ENTITY_TREE conversion. Partly changed/lost stock
        // still enters the strict native postpass and must refuse.
        if(prepared.equals(actual))return NeoForgeVanillaEntityImport.convert(source,version,registries,scope);
        return NeoForgeVanillaEntityImport.finishAfterVanillaFixer(source,actual,version,registries,scope);
    }
    private static void itemField(CompoundTag source,CompoundTag result,String key,int version,HolderLookup.Provider registries,boolean prepare,NeoForgeMigrationScope scope){
        if(!source.contains(key))return;
        var old=compound(source,key);var fixed=prepare?old:result.getCompoundOrEmpty(key);
        var next=stack(old,fixed,version,registries,prepare,child(scope,key.toLowerCase(java.util.Locale.ROOT),0));
        if(!prepare&&next.isEmpty())result.remove(key);else result.put(key,next);
    }
    private static CompoundTag stack(CompoundTag source,CompoundTag actual,int version,HolderLookup.Provider registries,boolean prepare,NeoForgeMigrationScope scope){
        var expected=item(source,version,registries,scope);
        if(prepare)return NeoForgeItemComponentImport.prepareItemForVanillaFixer(source,version,registries,scope);
        var repaired=NeoForgeItemComponentImport.finishItemAfterVanillaFixer(source,actual,version,registries,scope);
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        var nativeStack=ItemStack.OPTIONAL_CODEC.parse(ops,repaired).getOrThrow();
        var canonical=ItemStack.OPTIONAL_CODEC.encodeStart(ops,nativeStack).getOrThrow();
        if(!expected.equals(canonical))throw invalid("Actual vanilla BE item lost original item/component/count lineage");
        return row(canonical,"native original physical item");
    }
    private static void uuidSet(CompoundTag source,String key){
        if(!source.contains(key))return;var seen=new HashSet<java.util.UUID>();
        for(Tag value:list(source,key))if(!seen.add(UUIDUtil.CODEC.parse(NbtOps.INSTANCE,value).getOrThrow()))throw invalid("Duplicate original set UUID in "+key);
    }
    private static void longField(CompoundTag source,String key){if(source.contains(key)&&!(source.get(key) instanceof LongTag))throw invalid("Original field lost its genuine long type: "+key);}
    private static void intField(CompoundTag source,String key){if(source.contains(key))integer(source,key);}
    private static CompoundTag row(Tag tag,String context){if(!(tag instanceof CompoundTag result))throw invalid("Missing/malformed original "+context);return result;}
    private static ListTag listOrDefault(CompoundTag source,String key){return source.contains(key)?list(source,key):new ListTag();}
    private static <T> void canonical(Codec<T> codec,Tag value,HolderLookup.Provider registries){codec.parse(registries.createSerializationContext(NbtOps.INSTANCE),value).getOrThrow();}
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope,String role,int index){return scope==null?null:scope.item(role,index);}
    private NeoForgeVanillaBlockEntityImport(){}
}
