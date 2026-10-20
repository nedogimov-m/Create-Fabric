package com.simibubi.create.test;

import com.simibubi.create.foundation.mixin.NeoForgeWorldChunkTestAccessor;
import com.simibubi.create.foundation.utility.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.mounted.PackedCartData;
import com.simibubi.create.foundation.item.ItemSampleEquality;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;

/** Actual pinned3955 typed stock through enclosing DFU, native factories and fresh native save/reload. */
public final class NeoForgeVanillaBlockEntityMaterialGameTests {
    private static final UUID PLAYER=UUID.fromString("8de16215-4ced-4a50-9c2f-1aeb4acf7a21");
    private static final UUID MOB=UUID.fromString("eef93251-9cb5-43ed-987c-ed872eb9f147");
    private static ListTag rows(Tag... tags){var result=new ListTag();for(Tag tag:tags)result.add(tag);return result;}
    private static CompoundTag source(String type){var result=new CompoundTag();result.putString("id","minecraft:"+type);return result;}
    private static CompoundTag item(String id,int count,String name){
        var result=new CompoundTag();result.putString("id",id);result.putInt("count",count);
        var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\""+name+"\",\"color\":\"gold\"}");result.put("components",components);return result;
    }
    private static CompoundTag spawn(CompoundTag entity){var result=new CompoundTag();result.put("entity",entity);return result;}
    private static CompoundTag weighted(CompoundTag spawn,int weight){var result=new CompoundTag();result.put("data",spawn);result.putInt("weight",weight);return result;}
    private static CompoundTag zombie(){
        var result=new CompoundTag();result.putString("id","minecraft:zombie");
        result.put("HandItems",rows(item("minecraft:diamond",3,"Original spawner hand3"),new CompoundTag()));
        result.put("ArmorItems",rows(new CompoundTag(),new CompoundTag(),new CompoundTag(),new CompoundTag()));return result;
    }
    private static CompoundTag spawner(){
        var result=source("mob_spawner");
        for(String key:List.of("Delay","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"))
            result.putShort(key,(short)(key.equals("Delay")?31:key.equals("MaxSpawnDelay")?800:key.equals("MinSpawnDelay")?200:4));
        result.put("SpawnData",spawn(zombie()));result.put("SpawnPotentials",rows(weighted(spawn(zombie()),7),weighted(spawn(new CompoundTag()),0)));return result;
    }
    private static CompoundTag trial(){
        var result=source("trial_spawner");var config=new CompoundTag();
        config.putFloat("total_mobs",5.5f);config.putFloat("simultaneous_mobs",2.5f);config.putInt("ticks_between_spawn",37);
        config.put("spawn_potentials",rows(weighted(spawn(zombie()),5)));result.put("normal_config",config);
        // Genuine source writer omits ominous_config when both actual configs are equal.
        result.put("spawn_data",spawn(zombie()));result.putLong("cooldown_ends_at",9123);result.putLong("next_mob_spawns_at",9876);result.putInt("total_mobs_spawned",2);
        result.put("registered_players",rows(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,PLAYER).getOrThrow()));
        result.put("current_mobs",rows(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,MOB).getOrThrow()));return result;
    }
    private static CompoundTag vault(){
        var result=source("vault");var config=new CompoundTag();config.put("key_item",item("minecraft:trial_key",2,"Original key sample2"));
        config.putString("loot_table","minecraft:chests/trial_chambers/reward");config.putDouble("activation_range",4);config.putDouble("deactivation_range",4.5);result.put("config",config);
        var shared=new CompoundTag();shared.put("display_item",item("minecraft:emerald",1,"Original display sample"));shared.put("connected_players",rows(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,PLAYER).getOrThrow()));result.put("shared_data",shared);
        var server=new CompoundTag();server.put("items_to_eject",rows(item("minecraft:diamond",7,"Original queued reward7"),item("minecraft:gold_ingot",5,"Original queued reward5")));
        server.put("rewarded_players",rows(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,PLAYER).getOrThrow()));server.putLong("state_updating_resumes_at",43210);server.putInt("total_ejections_needed",2);result.put("server_data",server);return result;
    }

    @GameTest public void originalJukeboxAndLecternStockSurviveRealNativeSaveAndFreshFactories(GameTestHelper h){
        var disc=source("jukebox");disc.put("RecordItem",item("minecraft:music_disc_13",1,"Original record"));disc.putLong("ticks_since_song_started",137);
        var saved=roundTrip(h,disc,Blocks.JUKEBOX.defaultBlockState());sameItem(h,disc.getCompoundOrEmpty("RecordItem"),saved.getCompoundOrEmpty("RecordItem"));
        h.assertTrue(saved.getLongOr("ticks_since_song_started",-1)==137,"Actual native jukebox preserves source song time without restarting or creating a second record");
        var lectern=source("lectern");var book=item("minecraft:written_book",1,"Original lectern book");
        var content=new CompoundTag();content.putString("title","Original title");content.putString("author","Create");content.putInt("generation",0);content.putBoolean("resolved",true);
        content.put("pages",rows(StringTag.valueOf("{\"text\":\"First\"}"),StringTag.valueOf("{\"text\":\"Second\"}"),StringTag.valueOf("{\"text\":\"Third\"}")));
        book.getCompoundOrEmpty("components").put("minecraft:written_book_content",content);lectern.put("Book",book);lectern.putInt("Page",1);
        var savedLectern=roundTrip(h,lectern,Blocks.LECTERN.defaultBlockState());sameItem(h,book,savedLectern.getCompoundOrEmpty("Book"));
        h.assertTrue(savedLectern.getIntOr("Page",-1)==1,"Actual native lectern keeps original selected page and all real book components");h.succeed();
    }

    @GameTest public void originalBrushablePotAndDeferredLootKeepDistinctActualOwners(GameTestHelper h){
        var brush=source("brushable_block");brush.put("item",item("minecraft:diamond",13,"Original archaeology13"));
        var saved=roundTrip(h,brush,Blocks.SUSPICIOUS_SAND.defaultBlockState());sameItem(h,brush.getCompoundOrEmpty("item"),saved.getCompoundOrEmpty("item"));
        var pot=source("decorated_pot");pot.put("item",item("minecraft:emerald",17,"Original pot17"));
        pot.put("sherds",rows(StringTag.valueOf("minecraft:angler_pottery_sherd"),StringTag.valueOf("minecraft:brick"),StringTag.valueOf("minecraft:archer_pottery_sherd"),StringTag.valueOf("minecraft:arms_up_pottery_sherd")));
        var savedPot=roundTrip(h,pot,Blocks.DECORATED_POT.defaultBlockState());sameItem(h,pot.getCompoundOrEmpty("item"),savedPot.getCompoundOrEmpty("item"));
        var decorations=PotDecorations.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),savedPot.get("sherds")).getOrThrow();
        var sides=List.of(decorations.back(),decorations.left(),decorations.right(),decorations.front());
        var expectedSides=List.of("minecraft:angler_pottery_sherd","minecraft:brick","minecraft:archer_pottery_sherd","minecraft:arms_up_pottery_sherd");
        for(int side=0;side<4;side++){
            var actual=sides.get(side).orElseThrow();
            h.assertTrue(actual.item().value()==BuiltInRegistries.ITEM.getValue(Identifier.parse(expectedSides.get(side)))&&actual.count()==1&&actual.components().isEmpty(),"Actual pot native back/left/right/front template keeps its exact original item/count/components");
        }
        for(var type:List.of("brushable_block","decorated_pot")){
            var deferred=source(type);deferred.putString("LootTable","minecraft:archaeology/desert_pyramid");deferred.putLong("LootTableSeed",728349L);
            var actual=roundTrip(h,deferred,type.equals("brushable_block")?Blocks.SUSPICIOUS_SAND.defaultBlockState():Blocks.DECORATED_POT.defaultBlockState());
            h.assertTrue(actual.getStringOr("LootTable","").equals("minecraft:archaeology/desert_pyramid")&&actual.getLongOr("LootTableSeed",0)==728349L&&!actual.contains("item"),"Real native load/save never rolls deferred original loot into invented physical stock");
        }h.succeed();
    }

    @GameTest public void originalHiveOccupantsAndGenuineEmptyOccupantDefaultsRemainOffWorld(GameTestHelper h){
        var hive=source("beehive");var bee=new CompoundTag();bee.putString("id","minecraft:bee");bee.putFloat("Health",7.5f);bee.putBoolean("HasNectar",true);bee.putInt("Age",-100);
        var occupant=new CompoundTag();occupant.put("entity_data",bee);occupant.putInt("ticks_in_hive",712);occupant.putInt("min_ticks_in_hive",2400);
        var empty=new CompoundTag();empty.putInt("ticks_in_hive",0);empty.putInt("min_ticks_in_hive",600);
        hive.put("bees",rows(occupant,empty));hive.store("flower_pos",BlockPos.CODEC,new BlockPos(17,66,-8));
        var saved=roundTrip(h,hive,Blocks.BEEHIVE.defaultBlockState());
        var inert=com.simibubi.create.foundation.utility.NeoForgeInertHiveSlots.read(saved.getCompoundOrEmpty(com.simibubi.create.foundation.utility.NeoForgeInertHiveSlots.KEY));
        h.assertTrue(saved.getListOrEmpty("bees").size()==1&&inert.size()==1,"Only the genuine source bee is in actual native BeeData; the codec-empty row is actual inert native capacity");
        var bees=inert.combinedRows(saved.getListOrEmpty("bees"));
        h.assertTrue(bees.size()==2&&bees.getCompoundOrEmpty(0).getIntOr("ticks_in_hive",0)==712&&bees.getCompoundOrEmpty(0).getIntOr("min_ticks_in_hive",0)==2400,"Actual native hive keeps all source occupant rows and residence times");
        var entity=bees.getCompoundOrEmpty(0).getCompoundOrEmpty("entity_data");
        h.assertTrue(entity.getStringOr("id","").equals("minecraft:bee")&&entity.getFloatOr("Health",0)==7.5f&&!entity.contains("UUID")&&bees.getCompoundOrEmpty(1).getCompoundOrEmpty("entity_data").isEmpty(),"Stripped hive snapshots and codec-empty defaults never invent saved entity UUIDs or default bees");
        h.assertTrue(saved.get("flower_pos").equals(hive.get("flower_pos")),"Actual hive factory keeps original flower position");h.succeed();
    }

    @GameTest public void originalSpawnerAndTrialTemplatesKeepEquipmentWeightsAndOminousInheritance(GameTestHelper h){
        var spawner=spawner();var saved=roundTrip(h,spawner,Blocks.SPAWNER.defaultBlockState());
        sameEntity(h,zombie(),saved.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity"));
        var weights=saved.getListOrEmpty("SpawnPotentials");
        h.assertTrue(weights.size()==2&&weights.getCompoundOrEmpty(0).getIntOr("weight",1)==7&&weights.getCompoundOrEmpty(1).getIntOr("weight",1)==0,"Real native spawner retains weighted potential order and genuine zero-weight empty default");
        h.assertTrue(weights.getCompoundOrEmpty(1).getCompoundOrEmpty("data").getCompoundOrEmpty("entity").isEmpty(),"Actual source empty SpawnData does not become a fabricated entity");
        var original=trial();var savedTrial=roundTrip(h,original,Blocks.TRIAL_SPAWNER.defaultBlockState());
        sameEntity(h,zombie(),savedTrial.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity"));
        for(String key:List.of("normal_config","ominous_config")){
            var config=savedTrial.getCompoundOrEmpty(key);var potentials=config.getListOrEmpty("spawn_potentials");
            h.assertTrue(potentials.size()==1&&potentials.getCompoundOrEmpty(0).getIntOr("weight",1)==5&&config.getIntOr("ticks_between_spawn",0)==37,"Actual native "+key+" keeps source normal/omitted-equal-ominous config and exact spawn weight");
            sameEntity(h,zombie(),potentials.getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity"));
        }
        h.assertTrue(savedTrial.getLongOr("cooldown_ends_at",0)==9123&&savedTrial.getLongOr("next_mob_spawns_at",0)==9876&&savedTrial.getIntOr("total_mobs_spawned",0)==2
            &&savedTrial.getListOrEmpty("registered_players").equals(original.getListOrEmpty("registered_players"))&&savedTrial.getListOrEmpty("current_mobs").equals(original.getListOrEmpty("current_mobs")),"Actual native trial keeps pending timing/player/mob UUID references without creating world entities");h.succeed();
    }

    @GameTest public void originalVaultSamplesAndQueuedPhysicalRewardsSurviveFreshNativeFactories(GameTestHelper h){
        var original=vault();var pure=NeoForgeBlockEntityImport.convert(original,3955,h.getLevel().registryAccess());
        h.assertTrue(pure.getCompoundOrEmpty("server_data").getIntOr("total_ejections_needed",0)==2,"Pure converter preserves the genuine original encoded ejection progress");
        var saved=roundTrip(h,original,Blocks.VAULT.defaultBlockState());
        sameItem(h,original.getCompoundOrEmpty("config").getCompoundOrEmpty("key_item"),saved.getCompoundOrEmpty("config").getCompoundOrEmpty("key_item"));
        sameItem(h,original.getCompoundOrEmpty("shared_data").getCompoundOrEmpty("display_item"),saved.getCompoundOrEmpty("shared_data").getCompoundOrEmpty("display_item"));
        var before=original.getCompoundOrEmpty("server_data");var after=saved.getCompoundOrEmpty("server_data");
        var rewards=after.getListOrEmpty("items_to_eject");h.assertTrue(rewards.size()==2,"Actual native vault retains exactly two pending paid reward rows");
        for(int i=0;i<2;i++)sameItem(h,before.getListOrEmpty("items_to_eject").getCompoundOrEmpty(i),rewards.getCompoundOrEmpty(i));
        h.assertTrue(after.getLongOr("state_updating_resumes_at",0)==43210&&after.getListOrEmpty("rewarded_players").equals(before.getListOrEmpty("rewarded_players")),"Native vault retains actual pending reward order, cooldown and rewarded-player ownership");
        // Both pinned21.1.219 and native26.3 VaultServerData.set omit this progress field.
        // Assert that genuine loader behavior explicitly; do not claim native progress retention.
        h.assertTrue(after.getIntOr("total_ejections_needed",0)==0,"Actual ordinary source/native vault reload resets presentation progress without changing paid queued rewards");h.succeed();
    }

    @GameTest public void genuineEmptyAndDefaultSourceOwnersDoNotRequireFabricatedMaterial(GameTestHelper h){
        var cases=List.of(source("jukebox"),source("lectern"),source("beehive"),source("brushable_block"),source("decorated_pot"),source("trial_spawner"),source("vault"));
        var states=List.of(Blocks.JUKEBOX.defaultBlockState(),Blocks.LECTERN.defaultBlockState(),Blocks.BEEHIVE.defaultBlockState(),Blocks.SUSPICIOUS_SAND.defaultBlockState(),Blocks.DECORATED_POT.defaultBlockState(),Blocks.TRIAL_SPAWNER.defaultBlockState(),Blocks.VAULT.defaultBlockState());
        cases.get(2).put("bees",new ListTag());
        for(String key:List.of("config","shared_data","server_data"))cases.getLast().put(key,new CompoundTag());
        for(int i=0;i<cases.size();i++){
            var original=cases.get(i);var converted=NeoForgeBlockEntityImport.convert(original,3955,h.getLevel().registryAccess());
            var saved=roundTrip(h,original,states.get(i));
            h.assertTrue(!converted.contains("RecordItem")&&!converted.contains("Book")&&!converted.contains("item")&&!saved.contains("RecordItem")&&!saved.contains("Book")&&!saved.contains("item"),"Actual absent source stock remains absent through pure conversion and native fresh save");
        }
        var emptySpawner=source("mob_spawner");emptySpawner.putShort("Delay",(short)20);emptySpawner.put("SpawnPotentials",new ListTag());
        var saved=roundTrip(h,emptySpawner,Blocks.SPAWNER.defaultBlockState());
        h.assertTrue(saved.getListOrEmpty("SpawnPotentials").isEmpty(),"Actual default original spawner retains empty potential definition");h.succeed();
    }

    @GameTest public void originalTypedBlockEntityInsideItemUsesOneContainingItemFixerAndRealNativeCodec(GameTestHelper h){
        var payload=source("jukebox");payload.put("RecordItem",item("minecraft:music_disc_13",1,"Original nested record"));
        var enclosing=item("minecraft:jukebox",1,"Original carried jukebox");enclosing.getCompoundOrEmpty("components").put("minecraft:block_entity_data",payload);
        var before=enclosing.copy();var converted=NeoForgeSaveCodecs.item(enclosing,3955,h.getLevel().registryAccess());
        var actual=ItemStack.OPTIONAL_CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),converted).getOrThrow();
        var resaved=(CompoundTag)ItemStack.OPTIONAL_CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),actual).getOrThrow();
        sameItem(h,payload.getCompoundOrEmpty("RecordItem"),resaved.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:block_entity_data").getCompoundOrEmpty("RecordItem"));
        h.assertTrue(enclosing.equals(before)&&converted.equals(resaved),"Real containing item codec keeps nested typed BE material and never mutates source bytes");h.succeed();
    }

    @GameTest public void actualTerrainGatePreflightsAllEightVanillaMaterialOwnersBeforeNativeParsing(GameTestHelper h){
        var original=terrain(h);var before=original.copy();var requested=new ChunkPos(original.getIntOr("xPos",0),original.getIntOr("zPos",0));
        var upgrade=(NeoForgeWorldChunkTestAccessor)(Object)h.getLevel().getChunkSource().chunkMap;
        var converted=NeoForgeWorldChunkImport.convert(original,requested,h.getLevel(),upgrade::create$upgradeActualOriginalChunk);
        var parsed=SerializableChunkData.parse(h.getLevel(),h.getLevel().palettedContainerFactory(),converted);
        h.assertTrue(parsed!=null&&parsed.chunkPos().equals(requested)&&converted.getListOrEmpty("block_entities").size()==8&&original.equals(before),"Real native terrain upgrade/parser keeps all eight source physical block owners and untouched source envelope");
        var bad=original.copy();bad.getListOrEmpty("block_entities").getCompoundOrEmpty(6).getCompoundOrEmpty("normal_config").getListOrEmpty("spawn_potentials").getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity").putString("id","create:unsupported_source_template");
        var calls=new AtomicInteger();boolean refused=false;var badBefore=bad.copy();
        try{NeoForgeWorldChunkImport.convert(bad,requested,h.getLevel(),input->{calls.incrementAndGet();return input;});}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused&&calls.get()==0&&bad.equals(badBefore),"One unsupported typed pending entity refuses the whole genuine terrain owner before actual native upgrade/parser and preserves all stock");h.succeed();
    }

    @GameTest public void malformedTypedStockForeignOwnersAndUnsupportedExtensionsRefuseWithoutMutation(GameTestHelper h){
        var badCount=source("jukebox");badCount.put("RecordItem",item("minecraft:diamond",0,"Invalid original"));refuses(h,badCount);
        var conflict=source("decorated_pot");conflict.put("item",item("minecraft:diamond",3,"Conflicting owner"));conflict.putString("LootTable","minecraft:chests/simple_dungeon");refuses(h,conflict);
        var wrongOwner=source("jukebox");wrongOwner.put("Book",item("minecraft:written_book",1,"Wrong source owner"));refuses(h,wrongOwner);
        var extension=source("lectern");var extra=new CompoundTag();extra.putInt("UnknownPaidStock",17);extension.put("NeoForgeData",extra);refuses(h,extension);
        var unknown=spawner();unknown.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").putString("id","create:unreviewed_original_entity");refuses(h,unknown);
        var duplicate=trial();duplicate.getListOrEmpty("current_mobs").add(duplicate.getListOrEmpty("current_mobs").getFirst().copy());refuses(h,duplicate);
        var intact=spawner();var before=intact.copy();var tampered=NeoForgeBlockEntityImport.convert(intact,3955,h.getLevel().registryAccess());
        tampered.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").remove("equipment");boolean lostEquipmentRefused=false;
        try{NeoForgeVanillaBlockEntityImport.finishAfterVanillaFixer(Identifier.parse("minecraft:mob_spawner"),intact,tampered,3955,h.getLevel().registryAccess());}
        catch(RuntimeException expected){lostEquipmentRefused=true;}
        h.assertTrue(lostEquipmentRefused&&intact.equals(before),"A partly changed native image with lost physical equipment cannot use exact-source-remainder conversion to fabricate replacement stock");h.succeed();
    }

    @GameTest public void scopedVaultPackedSamplesAndQueuedRowsRetainSeparateRealNativeIdentities(GameTestHelper h){
        var source=vault();source.getCompoundOrEmpty("config").put("key_item",originalPacked());source.getCompoundOrEmpty("shared_data").put("display_item",originalPacked());
        source.getCompoundOrEmpty("server_data").put("items_to_eject",rows(originalPacked(),originalPacked()));var before=source.copy();var scope=scope(h,2);
        var actual=roundTrip(h,source,Blocks.VAULT.defaultBlockState(),scope);var rewards=actual.getCompoundOrEmpty("server_data").getListOrEmpty("items_to_eject");
        var first=read(h,rewards.getCompoundOrEmpty(0));var second=read(h,rewards.getCompoundOrEmpty(1));
        var key=read(h,actual.getCompoundOrEmpty("config").getCompoundOrEmpty("key_item"));var display=read(h,actual.getCompoundOrEmpty("shared_data").getCompoundOrEmpty("display_item"));
        h.assertTrue(rewards.size()==2&&Set.of(plan(first),plan(second),plan(key),plan(display)).size()==4,"Actual queued source row0,row1,key and display sample have four separate exact native packed value identities");
        h.assertTrue(ItemSampleEquality.same(first,second)&&ItemSampleEquality.same(first,key)&&ItemSampleEquality.same(first,display),"Source sample equality retains the original packed value while native physical ownership remains distinct");
        var repeated=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess(),scope);
        var other=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess(),scope(h,3));
        h.assertTrue(ItemStack.matches(first,read(h,repeated.getCompoundOrEmpty("server_data").getListOrEmpty("items_to_eject").getCompoundOrEmpty(0)))
            &&!plan(first).equals(plan(read(h,other.getCompoundOrEmpty("server_data").getListOrEmpty("items_to_eject").getCompoundOrEmpty(0))))&&source.equals(before),"Same actual source owner/listindex is deterministic; a different actual block owner never collides or mutates original reward stock");h.succeed();
    }

    @GameTest public void scopedSpawnerTemplatesInheritTheirContainingPathsEvenWithUuidShapedFields(GameTestHelper h){
        var source=spawner();var entity=zombie();entity.store("UUID",UUIDUtil.CODEC,PLAYER);entity.put("HandItems",rows(originalPacked(),new CompoundTag()));
        source.put("SpawnData",spawn(entity.copy()));source.put("SpawnPotentials",rows(weighted(spawn(entity.copy()),3),weighted(spawn(entity.copy()),5)));
        var before=source.copy();var scope=scope(h,2);var converted=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess(),scope);
        var first=hand(h,converted.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity"));var potentials=converted.getListOrEmpty("SpawnPotentials");
        var second=hand(h,potentials.getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity"));var third=hand(h,potentials.getCompoundOrEmpty(1).getCompoundOrEmpty("data").getCompoundOrEmpty("entity"));
        h.assertTrue(Set.of(plan(first),plan(second),plan(third)).size()==3&&source.equals(before),"Actual main spawn template and two weighted row indexes keep separate inherited native identities despite identical template UUID fields");
        source.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").store("UUID",UUIDUtil.CODEC,MOB);
        var changed=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess(),scope);
        h.assertTrue(plan(first).equals(plan(hand(h,changed.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity")))),"Changing a template UUID cannot replace its actual containing world-block/item path");
        boolean refused=false;try{NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused,"Unscoped packed pending templates must refuse instead of inventing a saved-world entity owner from their UUID-shaped fields");h.succeed();
    }

    @GameTest public void scopedNestedTrialAndCarriedPotKeepRealOwnerContextAcrossBothContainingFixers(GameTestHelper h){
        var source=trial();var entity=zombie();entity.store("UUID",UUIDUtil.CODEC,PLAYER);entity.put("HandItems",rows(originalPacked(),new CompoundTag()));
        source.put("spawn_data",spawn(entity.copy()));source.getCompoundOrEmpty("normal_config").put("spawn_potentials",rows(weighted(spawn(entity.copy()),5)));
        var actual=roundTrip(h,source,Blocks.TRIAL_SPAWNER.defaultBlockState(),scope(h,2));
        var top=hand(h,actual.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity"));
        var normal=hand(h,actual.getCompoundOrEmpty("normal_config").getListOrEmpty("spawn_potentials").getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity"));
        var ominous=hand(h,actual.getCompoundOrEmpty("ominous_config").getListOrEmpty("spawn_potentials").getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity"));
        h.assertTrue(Set.of(plan(top),plan(normal),plan(ominous)).size()==3&&ItemSampleEquality.same(top,normal)&&ItemSampleEquality.same(normal,ominous),"Actual typed top template and separately fixed nested normal/inherited-ominous templates retain distinct scopes and the same original packed sample value");
        var pot=source("decorated_pot");pot.put("item",originalPacked());var enclosing=item("minecraft:decorated_pot",1,"Original carried packed pot");enclosing.getCompoundOrEmpty("components").put("minecraft:block_entity_data",pot);var before=enclosing.copy();
        var owner=scope(h,2);var first=NeoForgeSaveCodecs.item(enclosing,3955,h.getLevel().registryAccess(),owner.item("inventory",0));
        var second=NeoForgeSaveCodecs.item(enclosing,3955,h.getLevel().registryAccess(),owner.item("inventory",1));
        var firstPacked=read(h,first.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:block_entity_data").getCompoundOrEmpty("item"));
        var secondPacked=read(h,second.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:block_entity_data").getCompoundOrEmpty("item"));
        h.assertTrue(!plan(firstPacked).equals(plan(secondPacked))&&ItemSampleEquality.same(firstPacked,secondPacked)&&enclosing.equals(before),"Actual item block_entity_data postpass preserves outer inventory ownership down to its real packed pot item leaf");h.succeed();
    }

    private static CompoundTag roundTrip(GameTestHelper h,CompoundTag original,BlockState state){
        return roundTrip(h,original,state,null);
    }
    private static CompoundTag roundTrip(GameTestHelper h,CompoundTag original,BlockState state,NeoForgeMigrationScope scope){
        var source=original.copy();var pos=h.absolutePos(new BlockPos(2,2,2));source.putInt("x",pos.getX());source.putInt("y",pos.getY());source.putInt("z",pos.getZ());var before=source.copy();
        var converted=NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess(),scope);
        var first=factory(h,state,pos,converted);var saved=first.saveWithFullMetadata(h.getLevel().registryAccess());
        var second=factory(h,state,pos,saved);var secondSave=second.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(source.equals(before)&&saved.equals(secondSave),"Real native factory save and fresh reload must retain the exact converted material state with untouched original bytes");return saved;
    }
    private static BlockEntity factory(GameTestHelper h,BlockState state,BlockPos pos,CompoundTag input){
        var owner=((EntityBlock)state.getBlock()).newBlockEntity(pos,state);if(owner==null)throw new AssertionError("Actual native block factory returned no owner");
        owner.setLevel(h.getLevel());var problems=new ProblemReporter.Collector();owner.loadWithComponents(TagValueInput.create(problems,h.getLevel().registryAccess(),input));
        h.assertTrue(problems.isEmpty(),"Actual native factory reports no fallback/dropped typed data");return owner;
    }
    private static void sameItem(GameTestHelper h,CompoundTag original,CompoundTag actual){
        h.assertTrue(NeoForgeSaveCodecs.item(original,3955,h.getLevel().registryAccess()).equals(actual),"Native BE retains exact original physical item id/count/components");
    }
    private static void sameEntity(GameTestHelper h,CompoundTag original,CompoundTag actual){
        h.assertTrue(NeoForgeVanillaEntityImport.convert(original,3955,h.getLevel().registryAccess()).equals(actual),"Native pending template retains exact typed entity equipment and no invented UUID/world admission");
    }
    private static void refuses(GameTestHelper h,CompoundTag source){
        var before=source.copy();boolean refused=false;try{NeoForgeBlockEntityImport.convert(source,3955,h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused&&source.equals(before),"Malformed/unreviewed actual typed physical owner refuses without source mutation");
    }
    private static NeoForgeMigrationScope scope(GameTestHelper h,int x){return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(new BlockPos(x,2,2)));}
    private static ItemStack read(GameTestHelper h,CompoundTag tag){return ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();}
    private static ItemStack hand(GameTestHelper h,CompoundTag entity){return read(h,entity.getCompoundOrEmpty("equipment").getCompoundOrEmpty("mainhand"));}
    private static UUID plan(ItemStack item){return PackedCartData.receiptId(item);}
    private static CompoundTag originalPacked(){
        var state=AllBlocks.MINECART_ANCHOR.defaultBlockState();var sourceState=new CompoundTag();sourceState.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())sourceState.put("Properties",properties);
        var row=new CompoundTag();row.putLong("Pos",BlockPos.ZERO.asLong());row.putInt("State",0);var blocks=new CompoundTag();blocks.put("Palette",rows(sourceState));blocks.put("BlockList",rows(row));
        var body=new CompoundTag();body.putString("Type","create:mounted");body.putString("RotationMode","ROTATE");body.putString("InitialOrientation","EAST");var anchor=new CompoundTag();anchor.putInt("X",0);anchor.putInt("Y",0);anchor.putInt("Z",0);body.put("Anchor",anchor);body.putBoolean("Stalled",false);body.putBoolean("BottomlessSupply",false);body.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))body.put(key,new ListTag());
        var components=new CompoundTag();components.put("create:minecart_contraption_data",body);var item=new CompoundTag();item.putString("id","create:minecart_contraption");item.putInt("count",1);item.put("components",components);return item;
    }
    private static CompoundTag terrain(GameTestHelper h){
        var anchor=h.absolutePos(new BlockPos(2,2,2));var chunk=ChunkPos.containing(anchor);int y=Math.floorDiv(anchor.getY(),16)*16+2;
        var owners=List.of(source("jukebox"),source("lectern"),source("beehive"),spawner(),source("brushable_block"),source("decorated_pot"),trial(),vault());
        owners.get(0).put("RecordItem",item("minecraft:music_disc_13",1,"World record"));owners.get(2).put("bees",new ListTag());
        owners.get(4).put("item",item("minecraft:diamond",13,"World archaeology"));owners.get(5).put("item",item("minecraft:emerald",17,"World pot"));
        var blockNames=List.of("jukebox","lectern","beehive","spawner","suspicious_sand","decorated_pot","trial_spawner","vault");
        var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);var packed=new SimpleBitStorage(4,4096);var beRows=new ListTag();
        for(int i=0;i<owners.size();i++){
            var state=new CompoundTag();state.putString("Name","minecraft:"+blockNames.get(i));palette.add(state);
            var pos=new BlockPos(chunk.getMinBlockX()+i,y,chunk.getMinBlockZ()+4);int index=(Math.floorMod(y,16)<<8)|(4<<4)|i;packed.set(index,i+1);
            var owner=owners.get(i);owner.putInt("x",pos.getX());owner.putInt("y",y);owner.putInt("z",pos.getZ());beRows.add(owner);
        }
        var states=new CompoundTag();states.put("palette",palette);states.putLongArray("data",packed.getRaw());var section=new CompoundTag();section.putByte("Y",(byte)Math.floorDiv(y,16));section.put("block_states",states);
        var root=new CompoundTag();root.putInt("DataVersion",3955);root.putInt("xPos",chunk.x());root.putInt("zPos",chunk.z());root.putInt("yPos",-4);root.putString("Status","minecraft:full");root.putLong("LastUpdate",0);root.putLong("InhabitedTime",0);root.putBoolean("isLightOn",false);root.put("sections",rows(section));root.put("block_entities",beRows);return root;
    }
}
