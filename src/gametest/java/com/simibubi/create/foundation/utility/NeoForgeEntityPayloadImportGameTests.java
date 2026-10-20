package com.simibubi.create.foundation.utility;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;

/** Exact source delegated serializers, native entity reload and actual once-only falling landing; authored5 UNRUN. */
public final class NeoForgeEntityPayloadImportGameTests {
    private static ListTag rows(Tag... values) { var result = new ListTag(); for (var value : values) result.add(value); return result; }
    private static ListTag vector(double x, double y, double z) { return rows(DoubleTag.valueOf(x), DoubleTag.valueOf(y), DoubleTag.valueOf(z)); }
    private static CompoundTag xyz(BlockPos pos) { var result = new CompoundTag(); result.putInt("X", pos.getX()); result.putInt("Y", pos.getY()); result.putInt("Z", pos.getZ()); return result; }
    private static CompoundTag state(BlockState state) {
        var result = new CompoundTag(); result.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()); var properties = new CompoundTag(); state.getValues().forEach(value -> properties.putString(value.property().getName(), value.valueName())); if (!properties.isEmpty()) result.put("Properties", properties); return result;
    }
    private static CompoundTag base(GameTestHelper h, String id) {
        var result = new CompoundTag(); result.putString("id", id); result.store("UUID", UUIDUtil.CODEC, UUID.randomUUID()); var pos = h.absolutePos(new BlockPos(3, 7, 3));
        result.put("Pos", vector(pos.getX() + .5, pos.getY(), pos.getZ() + .5)); result.put("Motion", vector(0, 0, 0)); result.put("Rotation", rows(FloatTag.valueOf(0), FloatTag.valueOf(0))); result.putFloat("FallDistance", 0); result.putShort("Fire", (short) -1); result.putShort("Air", (short) 300); result.putBoolean("OnGround", false); result.putBoolean("Invulnerable", false); result.putInt("PortalCooldown", 0); result.putBoolean("NoGravity", true); return result;
    }
    private static CompoundTag stack(String id, int count) { var result = new CompoundTag(); result.putString("id", id); result.putInt("count", count); return result; }
    private static CompoundTag packed() {
        var row = new CompoundTag(); row.putLong("Pos", BlockPos.ZERO.asLong()); row.putInt("State", 0); var blocks = new CompoundTag(); blocks.put("Palette", rows(state(AllBlocks.MINECART_ANCHOR.defaultBlockState()))); blocks.put("BlockList", rows(row)); var body = new CompoundTag(); body.putString("Type", "create:mounted"); body.putString("RotationMode", "ROTATE"); body.putString("InitialOrientation", "EAST"); body.put("Anchor", xyz(BlockPos.ZERO)); body.putBoolean("Stalled", false); body.putBoolean("BottomlessSupply", false); body.put("Blocks", blocks);
        for (String key : Set.of("CapturedMultiblocks", "Actors", "DisabledActors", "Interactors", "Superglue", "Seats", "Passengers", "SubContraptions")) body.put(key, new ListTag()); var result = stack("create:minecart_contraption", 1); var components = new CompoundTag(); components.put("create:minecart_contraption_data", body); result.put("components", components); return result;
    }
    private static String text() {
        var hover = new CompoundTag(); hover.putString("action", "show_item"); hover.put("contents", packed()); var text = new CompoundTag(); text.putString("text", "Actual original payload"); text.put("hoverEvent", hover); return NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, text).toString();
    }
    private static CompoundTag plan(GameTestHelper h, Component component) {
        h.assertTrue(component != null && component.getString().equals("Actual original payload") && component.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem, "Actual native component retains source text and rich packed-item hover");
        var result = ((HoverEvent.ShowItem) component.getStyle().getHoverEvent()).item().create().get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
        h.assertTrue(result != null && result.contains("NativePlanId") && !result.contains("CustodyReceipt") && !result.contains("BaseCarts"), "Actual native hover has its scoped NEW value identity without invented source material receipts"); return result;
    }
    private static CompoundTag namedEleven() { var result = stack("minecraft:diamond", 11); var components = new CompoundTag(); components.putString("minecraft:custom_name", text()); result.put("components", components); return result; }
    private static CompoundTag falling(GameTestHelper h, BlockState block, CompoundTag actualSourcePayload) {
        var result = base(h, "minecraft:falling_block"); result.putBoolean("NoGravity", false); result.put("BlockState", state(block)); result.putInt("Time", 1); result.putBoolean("DropItem", true); result.putBoolean("HurtEntities", false); result.putFloat("FallHurtAmount", 0); result.putInt("FallHurtMax", 40); result.putBoolean("CancelDrop", false); result.put("TileEntityData", actualSourcePayload); return result;
    }
    private static CompoundTag chest() { var row = namedEleven(); row.putByte("Slot", (byte) 17); var result = new CompoundTag(); result.put("Items", rows(row)); result.putString("CustomName", text()); return result; }
    private static CompoundTag convert(GameTestHelper h, CompoundTag source) { return NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); }
    private static Entity admit(GameTestHelper h, CompoundTag current) {
        var result = EntityType.loadEntityRecursive(current, h.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP); h.assertTrue(result != null && h.getLevel().addFreshEntity(result), "Actual native entity LOAD admits one real saved UUID owner"); return result;
    }
    private static CompoundTag save(Entity actual) {
        var problems = new ProblemReporter.Collector(actual.problemPath()); var output = TagValueOutput.createWithContext(problems, actual.registryAccess()); if (!actual.saveAsPassenger(output) || !problems.isEmpty()) throw new AssertionError("Actual native save: " + problems.getReport()); return output.buildResult();
    }
    @GameTest(structure="create_test:reservoir", maxTicks=160) public void actualFallingContainerRetainsSparseElevenThroughNativeReloadAndOneRealLanding(GameTestHelper h) {
        h.setBlock(new BlockPos(3, 2, 3), Blocks.STONE); var source = falling(h, Blocks.CHEST.defaultBlockState(), chest()); var before = source.copy(); var current = convert(h, source); var payload = current.getCompoundOrEmpty("TileEntityData");
        h.assertTrue(source.equals(before) && !payload.contains("id") && !payload.contains("x") && !payload.contains("y") && !payload.contains("z"), "Actual source falling payload remains without a fabricated original BE id/coordinate");
        var initial = (FallingBlockEntity) admit(h, current); var nativeSaved = save(initial); var id = initial.getUUID(); initial.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); var restored = (FallingBlockEntity) admit(h, nativeSaved);
        h.assertTrue(restored.getUUID().equals(id) && !restored.blockData.contains("id") && restored.blockData.getListOrEmpty("Items").getCompoundOrEmpty(0).getByte("Slot").orElseThrow() == 17, "Fresh actual native falling entity reload keeps its real sparse slot and original absent BE metadata");
        var beforeLandingPlan = ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), withoutSlot(restored.blockData.getListOrEmpty("Items").getCompoundOrEmpty(0))).getOrThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        var expectedPlan = plan(h, beforeLandingPlan); var floor = h.absolutePos(new BlockPos(3, 3, 3));
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(restored.isRemoved() && h.getLevel().getBlockEntity(floor) instanceof ChestBlockEntity, "Actual native falling tick lands once into the real world chest owner"); var actual = (ChestBlockEntity) h.getLevel().getBlockEntity(floor); var stock = actual.getItem(17);
            h.assertTrue(stock.is(Items.DIAMOND) && stock.getCount() == 11 && plan(h, stock.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)).equals(expectedPlan) && actual.getContainerSize() == 27,
                "The actual landed BE retains precisely eleven source diamonds in original sparse slot17 and the exact native rich-hover value");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, restored.getBoundingBox().inflate(3)).stream().noneMatch(value -> value.getItem().is(Items.DIAMOND)), "Native landing did not also create a second dropped owner of the original eleven diamonds");
        }).thenExecute(() -> { h.getLevel().removeBlockEntity(floor); h.getLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), 3); }).thenSucceed();
    }
    private static CompoundTag withoutSlot(CompoundTag row) { var result = row.copy(); result.remove("Slot"); return result; }
    @GameTest public void fallingMaterialOwnerUsesExistingStrictPotPostpassAndPreservesActualMetadataPresence(GameTestHelper h) {
        var payload = new CompoundTag(); payload.put("item", namedEleven()); payload.putString("id", "minecraft:decorated_pot"); payload.putInt("x", 8); payload.putInt("y", 65); payload.putInt("z", 9);
        var source = falling(h, Blocks.DECORATED_POT.defaultBlockState(), payload); var current = convert(h, source); var actual = current.getCompoundOrEmpty("TileEntityData");
        h.assertTrue(actual.getString("id").orElseThrow().equals("minecraft:decorated_pot") && actual.getInt("x").orElseThrow() == 8 && actual.getInt("y").orElseThrow() == 65 && actual.getInt("z").orElseThrow() == 9,
            "A real original payload id/coordinate remains the actual supplied metadata rather than a guessed landing coordinate");
        var initial = admit(h, current); var saved = save(initial); initial.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); var restored = admit(h, saved);
        try { var item = ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), save(restored).getCompoundOrEmpty("TileEntityData").get("item")).getOrThrow(); h.assertTrue(item.is(Items.DIAMOND) && item.getCount() == 11, "Actual native falling pot re-save retains the one source eleven-item material leaf"); plan(h, item.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)); }
        finally { restored.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); } h.succeed();
    }
    private static CompoundTag template(UUID optionalTemplateId) {
        var result = new CompoundTag(); result.putString("id", "minecraft:pig"); result.store("UUID", UUIDUtil.CODEC, optionalTemplateId); result.put("ArmorItems", rows(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag())); result.put("HandItems", rows(packed(), new CompoundTag())); return result;
    }
    private static CompoundTag spawner(GameTestHelper h) {
        var source = base(h, "minecraft:spawner_minecart"); source.putShort("Delay", (short) 200); source.putShort("MinSpawnDelay", (short) 200); source.putShort("MaxSpawnDelay", (short) 800); source.putShort("SpawnCount", (short) 4); source.putShort("MaxNearbyEntities", (short) 6); source.putShort("RequiredPlayerRange", (short) 16); source.putShort("SpawnRange", (short) 4);
        var templateId = UUID.randomUUID(); var next = new CompoundTag(); next.put("entity", template(templateId)); source.put("SpawnData", next); var potential = new CompoundTag(); potential.putInt("weight", 8); var data = new CompoundTag(); data.put("entity", template(templateId)); potential.put("data", data); source.put("SpawnPotentials", rows(potential)); return source;
    }
    private static CompoundTag templatePlan(GameTestHelper h, CompoundTag entity) {
        var equipment = EntityEquipment.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), entity.get("equipment")).getOrThrow(); return equipment.get(EquipmentSlot.MAINHAND).get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
    }
    @GameTest public void actualSpawnerCartComposedFieldsKeepDistinctContainingTemplateScopesAcrossNativeReload(GameTestHelper h) {
        var source = spawner(h); var before = source.copy(); var current = convert(h, source); var repeated = convert(h, source); var next = current.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity"); var potential = current.getListOrEmpty("SpawnPotentials").getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity"); var first = templatePlan(h, next); var second = templatePlan(h, potential);
        h.assertTrue(source.equals(before) && current.equals(repeated) && first != null && second != null && !first.get("NativePlanId").equals(second.get("NativePlanId")) && next.get("UUID").equals(potential.get("UUID")),
            "Actual cart UUID + spawner typed source location define distinct stable NEW values even when templates contain the same UUID-shaped data");
        var actual = (MinecartSpawner) admit(h, current); var saved = save(actual); actual.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); var restored = (MinecartSpawner) admit(h, saved);
        try { var after = save(restored); h.assertTrue(after.getShort("Delay").orElseThrow() == 200 && after.getShort("SpawnCount").orElseThrow() == 4 && after.getListOrEmpty("SpawnPotentials").getCompoundOrEmpty(0).getInt("weight").orElseThrow() == 8
            && templatePlan(h, after.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity")).equals(first) && templatePlan(h, after.getListOrEmpty("SpawnPotentials").getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity")).equals(second)
            && h.getLevel().getEntity(next.read("UUID", UUIDUtil.CODEC).orElseThrow()) == null, "Actual native BaseSpawner save/reload preserves exact shorts, source weight/order and both values without spawning a template body or changing its owner scope"); }
        finally { restored.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); } h.succeed();
    }
    private static CompoundTag command(GameTestHelper h) {
        var source = base(h, "minecraft:command_block_minecart"); source.putString("Command", "say original_command_must_never_execute"); source.putInt("SuccessCount", 7); source.putBoolean("TrackOutput", true); source.putString("LastOutput", text()); source.putBoolean("UpdateLastExecution", true); source.putLong("LastExecution", 53); source.putString("CustomName", text()); return source;
    }
    @GameTest public void actualCommandCartReloadRetainsComposedScalarsAndDistinctRichOutputWithoutExecution(GameTestHelper h) {
        var source = command(h); var before = source.copy(); var current = convert(h, source); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); var output = plan(h, ComponentSerialization.CODEC.parse(ops, current.get("LastOutput")).getOrThrow()); var name = plan(h, ComponentSerialization.CODEC.parse(ops, current.get("CustomName")).getOrThrow());
        h.assertTrue(source.equals(before) && current.getInt("SuccessCount").orElseThrow()==7 && !output.get("NativePlanId").equals(name.get("NativePlanId")), "Source converter retains original SuccessCount7 and independent actual typed output/name values before the real vanilla command-name callback");
        var actual = (MinecartCommandBlock) admit(h, current); h.assertTrue(actual.getCommandBlock().getCommand().equals(source.getStringOr("Command", "")) && plan(h, actual.getCommandBlock().getLastOutput()).equals(output), "Actual native command cart receives exact source command text and migrated rich last output");h.assertTrue(actual.getCommandBlock().getSuccessCount()==0,"Pinned original and native MinecartCommandBlock sync command-name after load; real BaseCommandBlock.setCommand resets SuccessCount to0 without executing a command"); var saved = save(actual); actual.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); var restored = (MinecartCommandBlock) admit(h, saved);
        try { var after = save(restored); h.assertTrue(after.getInt("SuccessCount").orElseThrow()==0,"Fresh native reload retains original vanilla synchronized-command reset semantics; actual SuccessCount="+after.getInt("SuccessCount"));
            h.assertTrue(after.getLong("LastExecution").orElseThrow()==53,"Native command history retains actual original LastExecution53; actual="+after.getLong("LastExecution"));
            h.assertTrue(after.getBooleanOr("TrackOutput",false)&&after.getBooleanOr("UpdateLastExecution",false),"Native command tracking flags retain actual source values; TrackOutput="+after.getBoolean("TrackOutput")+", UpdateLastExecution="+after.getBoolean("UpdateLastExecution"));
            h.assertTrue(restored.getCommandBlock().getCommand().equals(source.getStringOr("Command","")),"Native command text remains unchanged through actual reload without execution");
            h.assertTrue(plan(h,restored.getCommandBlock().getLastOutput()).equals(output),"Native rich command output preserves its exact scoped value through actual reload"); }
        finally { restored.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); } h.succeed();
    }
    private static void refuses(GameTestHelper h, CompoundTag original) {
        var before = original.copy(); boolean rejected = false; try { convert(h, original); } catch (RuntimeException expected) { rejected = true; } h.assertTrue(rejected && original.equals(before), "Malformed/unreviewed embedded ownership refuses without source mutation or world publication");
    }
    @GameTest public void contradictoryPayloadTypesUnknownFieldsDuplicateStockAndInvalidDelegatedSchemasRefuse(GameTestHelper h) {
        var source = falling(h, Blocks.CHEST.defaultBlockState(), chest()); source.getCompoundOrEmpty("TileEntityData").putString("id", "minecraft:hopper"); refuses(h, source);
        source = falling(h, Blocks.STONE.defaultBlockState(), chest()); refuses(h, source);
        source = falling(h, Blocks.CHEST.defaultBlockState(), chest()); source.getCompoundOrEmpty("TileEntityData").putInt("FuturePaidStock", 11); refuses(h, source);
        source = falling(h, Blocks.CHEST.defaultBlockState(), chest()); var rows = source.getCompoundOrEmpty("TileEntityData").getListOrEmpty("Items"); rows.add(rows.getFirst().copy()); refuses(h, source);
        source = spawner(h); source.putInt("Delay", 200); refuses(h, source);
        source = spawner(h); source.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").putInt("FutureOwnedPayload", 11); refuses(h, source);
        source = command(h); source.putInt("LastExecution", 53); refuses(h, source);
        source = command(h); source.putBoolean("TrackOutput", false); refuses(h, source);
        source = command(h); source.remove("Command"); refuses(h, source); h.succeed();
    }
}
