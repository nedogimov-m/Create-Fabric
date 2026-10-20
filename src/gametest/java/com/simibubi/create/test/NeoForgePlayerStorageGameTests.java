package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.foundation.mixin.accessor.PlayerDataStorageAccessor;
import com.simibubi.create.foundation.utility.NeoForgePlayerStorageImport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueInput;

/** Real PlayerDataStorage reads, native player hydration, actual disk save and fresh native reload. */
public final class NeoForgePlayerStorageGameTests {
    private record FileOwner(PlayerDataStorage storage, UUID uuid, Path file, Path backup, NameAndId name) {}
    private static FileOwner owner(GameTestHelper h) {
        var storage = NeoForgePlayerStorageImport.actualStorage(h.getLevel().getServer());
        var uuid = UUID.randomUUID(); var directory = ((PlayerDataStorageAccessor)storage).create$getPlayerDirectory().toPath();
        var result = new FileOwner(storage, uuid, directory.resolve(uuid+".dat"), directory.resolve(uuid+".dat_old"), new NameAndId(uuid,"import_"+uuid.toString().substring(0,8)));
        h.assertTrue(!Files.exists(result.file)&&!Files.exists(result.backup),"Actual native player-file test owns fresh UUID paths");
        return result;
    }
    private static CompoundTag read(Path path) throws Exception { return NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()); }
    private static ServerPlayer player(GameTestHelper h, FileOwner owner, CompoundTag nativeData) {
        var result = new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(owner.uuid,owner.name.name()),ClientInformation.createDefault());
        var problems = new ProblemReporter.Collector(result.problemPath());
        result.load(TagValueInput.create(problems,h.getLevel().registryAccess(),nativeData));
        h.assertTrue(problems.isEmpty(),"Actual native player hydrates migrated data without a permissive decoding fallback: "+problems.getReport());
        h.assertTrue(result.getUUID().equals(owner.uuid)&&result.getInventory().getItem(0).is(Items.DIAMOND)&&result.getInventory().getItem(0).getCount()==11
            &&result.getInventory().getItem(35).is(Items.GOLD_INGOT)&&result.getInventory().getItem(35).getCount()==5
            &&result.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)&&result.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
            &&result.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD)&&result.getEnderChestInventory().getItem(26).is(Items.EMERALD)
            &&result.getEnderChestInventory().getItem(26).getCount()==7,"The real player owns the exact source sparse inventory, armor, signed-byte offhand and Ender slot26");
        return result;
    }
    @GameTest public void actualRawPlayerFileBecomesOneNativePlayerAndRealSaveRetainsOriginalBackupBeforeFreshReload(GameTestHelper h) throws Exception {
        var owner=owner(h);var original=NeoForgePlayerDataGameTests.original(h,owner.uuid);NbtIo.writeCompressed(original.copy(),owner.file);
        try {
            var migrated=owner.storage.load(owner.name).orElseThrow();
            h.assertTrue(read(owner.file).equals(original)&&migrated.getIntOr("DataVersion",-1)==SharedConstants.getCurrentVersion().dataVersion().version(),"Actual public native load converts the real3955 file outside fallback without rewriting its original source");
            var actual=player(h,owner,migrated);owner.storage.save(actual);
            h.assertTrue(read(owner.backup).equals(original)&&read(owner.file).getIntOr("DataVersion",-1)==SharedConstants.getCurrentVersion().dataVersion().version(),"Actual native safeReplace saves the hydrated current player and retains the exact original3955 backup");
            var fresh=owner.storage.load(owner.name).orElseThrow();player(h,owner,fresh);
            h.assertTrue(NeoForgePlayerStorageImport.saveAllowed(owner.storage)&&NeoForgePlayerStorageImport.failure(owner.storage)==null,"Successful native roundtrip never acquires an original-refusal save fence");h.succeed();
        } finally {Files.deleteIfExists(owner.file);Files.deleteIfExists(owner.backup);}
    }
    @GameTest public void genuineDatOldWinnerIsConvertedAfterOrdinaryCorruptPrimaryFallbackAndKeepsItsRealFilenameOwner(GameTestHelper h) throws Exception {
        var owner=owner(h);var original=NeoForgePlayerDataGameTests.original(h,owner.uuid);byte[] corrupt="Actual unrelated corrupt primary file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(owner.file,corrupt);NbtIo.writeCompressed(original.copy(),owner.backup);
        try {
            var migrated=owner.storage.load(owner.name).orElseThrow();player(h,owner,migrated);
            h.assertTrue(java.util.Arrays.equals(Files.readAllBytes(owner.file),corrupt)&&read(owner.backup).equals(original)&&NeoForgePlayerStorageImport.failure(owner.storage)==null,"The real native .dat_old winner retains its original source and actual UUID after ordinary primary fallback; no replacement empty player or refusal is manufactured");h.succeed();
        } finally {
            try(var files=Files.list(owner.file.getParent())){for(var path:files.toList())if(path.getFileName().toString().startsWith(owner.uuid.toString()))Files.deleteIfExists(path);}
        }
    }
    @GameTest(maxTicks=200) public void offThreadNativePlayerReadRunsItsActualLevelWitnessOnTheServerWithoutLosingFilenameContext(GameTestHelper h) throws Exception {
        var owner=owner(h);var original=NeoForgePlayerDataGameTests.original(h,owner.uuid);NbtIo.writeCompressed(original.copy(),owner.file);
        var future=CompletableFuture.supplyAsync(()->owner.storage.load(owner.name).orElseThrow());
        h.startSequence().thenWaitUntil(()->h.assertTrue(future.isDone(),"Actual off-thread native load waits for its real server task")).thenExecute(()->{
            try {player(h,owner,future.join());h.assertTrue(read(owner.file).equals(original),"Async public native read retains the exact genuine original file before any real save");Files.deleteIfExists(owner.file);}
            catch(Exception error){throw new IllegalStateException(error);}
        }).thenSucceed();
    }
}
