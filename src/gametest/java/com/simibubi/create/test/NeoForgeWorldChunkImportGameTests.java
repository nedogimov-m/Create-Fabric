package com.simibubi.create.test;

import com.simibubi.create.foundation.mixin.NeoForgeWorldChunkTestAccessor;
import com.simibubi.create.foundation.utility.NeoForgeWorldChunkImport;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;

/** Genuine3955 terrain owners through the real native chunk upgrader/parser and BE factory. */
public final class NeoForgeWorldChunkImportGameTests {
    private static ListTag rows(Tag... values) { var result = new ListTag(); for (var value : values) result.add(value); return result; }
    private static CompoundTag state(String id) { var result = new CompoundTag(); result.putString("Name", id); return result; }
    private static CompoundTag source(GameTestHelper h) {
        var absolute = h.absolutePos(new BlockPos(2, 2, 2)); var chunk = ChunkPos.containing(absolute);
        var result = new CompoundTag(); result.putInt("DataVersion", 3955); result.putInt("xPos", chunk.x()); result.putInt("zPos", chunk.z()); result.putInt("yPos", -4);
        result.putString("Status", "minecraft:full"); result.putLong("LastUpdate", 0); result.putLong("InhabitedTime", 0); result.putBoolean("isLightOn", false);
        var blockStates = new CompoundTag(); blockStates.put("palette", rows(state("minecraft:air"), state("minecraft:chest")));
        var packed = new SimpleBitStorage(4, 4096); int index = (Math.floorMod(absolute.getY(), 16) << 8) | (Math.floorMod(absolute.getZ(), 16) << 4) | Math.floorMod(absolute.getX(), 16);
        packed.set(index, 1); blockStates.putLongArray("data", packed.getRaw()); var section = new CompoundTag(); section.putByte("Y", (byte) Math.floorDiv(absolute.getY(), 16)); section.put("block_states", blockStates);
        result.put("sections", rows(section)); var be = new CompoundTag(); be.putString("id", "minecraft:chest"); be.putInt("x", absolute.getX()); be.putInt("y", absolute.getY()); be.putInt("z", absolute.getZ());
        var item = new CompoundTag(); item.putString("id", "minecraft:diamond"); item.putInt("count", 11); item.putByte("Slot", (byte) 26); var components = new CompoundTag(); components.putString("minecraft:custom_name", "{\"text\":\"Original world cargo\"}"); item.put("components", components); be.put("Items", rows(item)); result.put("block_entities", rows(be)); return result;
    }
    @GameTest public void originalTerrainUsesActualNativeUpgradeAndPaletteParserBeforeFreshChestReload(GameTestHelper h) {
        var source = source(h); var before = source.copy(); var requested = ChunkPos.containing(h.absolutePos(new BlockPos(2, 2, 2)));
        var actualNative = (NeoForgeWorldChunkTestAccessor) (Object) h.getLevel().getChunkSource().chunkMap;
        var actual = NeoForgeWorldChunkImport.convert(source, requested, h.getLevel(), actualNative::create$upgradeActualOriginalChunk);
        var parsed = SerializableChunkData.parse(h.getLevel(), h.getLevel().palettedContainerFactory(), actual);
        h.assertTrue(parsed != null && parsed.chunkPos().equals(requested) && source.equals(before), "Actual native chunk parser retains the real source terrain coordinates and untouched original");
        var chest = new ChestBlockEntity(h.absolutePos(new BlockPos(2, 2, 2)), Blocks.CHEST.defaultBlockState()); chest.setLevel(h.getLevel()); var problems = new ProblemReporter.Collector();
        chest.loadWithComponents(TagValueInput.create(problems, h.getLevel().registryAccess(), actual.getListOrEmpty("block_entities").getCompoundOrEmpty(0)));
        h.assertTrue(problems.isEmpty() && chest.getItem(26).is(Items.DIAMOND) && chest.getItem(26).getCount() == 11 && chest.getItem(26).getHoverName().getString().equals("Original world cargo") && chest.getItem(0).isEmpty(), "The actual native chest factory retains exactly eleven named original diamonds in source byte slot26");
        var saved = chest.saveWithFullMetadata(h.getLevel().registryAccess()); var reloaded = new ChestBlockEntity(chest.getBlockPos(), chest.getBlockState()); reloaded.setLevel(h.getLevel());
        reloaded.loadWithComponents(TagValueInput.create(problems, h.getLevel().registryAccess(), saved));
        h.assertTrue(problems.isEmpty() && net.minecraft.world.item.ItemStack.matches(chest.getItem(26), reloaded.getItem(26)), "The real native save and fresh factory retain the original material exactly"); h.succeed();
    }
    @GameTest public void wrongRecordDuplicateOwnerAndMismatchedActualStateRefuseBeforeAnyNativeUpgrade(GameTestHelper h) {
        var original = source(h); var requested = ChunkPos.containing(h.absolutePos(new BlockPos(2, 2, 2))); var calls = new AtomicInteger();
        var wrong = original.copy(); wrong.putInt("xPos", requested.x() + 1); refuses(h, wrong, requested, calls);
        var duplicate = original.copy(); duplicate.getListOrEmpty("block_entities").add(duplicate.getListOrEmpty("block_entities").getCompoundOrEmpty(0).copy()); refuses(h, duplicate, requested, calls);
        var foreign = original.copy(); foreign.getListOrEmpty("block_entities").getCompoundOrEmpty(0).putString("id", "create:deployer"); refuses(h, foreign, requested, calls);
        var lostState = original.copy(); lostState.getListOrEmpty("sections").getCompoundOrEmpty(0).remove("block_states"); refuses(h, lostState, requested, calls);
        h.assertTrue(calls.get() == 0, "No native terrain upgrade/parser may start before all actual original owners validate"); h.succeed();
    }
    private static void refuses(GameTestHelper h, CompoundTag source, ChunkPos requested, AtomicInteger calls) {
        var before = source.copy(); boolean refused = false;
        try { NeoForgeWorldChunkImport.convert(source, requested, h.getLevel(), input -> { calls.incrementAndGet(); return input; }); } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused && source.equals(before), "Refused genuine source metadata retains all original material bytes");
    }
}
