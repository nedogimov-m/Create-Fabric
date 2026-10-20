package com.simibubi.create.foundation.utility;

import java.util.UUID;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.HauntingHorseConversion;
import com.simibubi.create.test.CartAcceptanceTestFixtures;
import com.simibubi.create.content.trains.schedule.hat.ConductorHatState;
import com.simibubi.create.content.contraptions.ContraptionPassengerData;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;

/** Real historical DFU/native codec cases. Every authored test is UNRUN in this packet. */
public final class NeoForgeVanillaEntityImportGameTests {
    private static CompoundTag entity(String id) { var tag = new CompoundTag(); tag.putString("id", id); return tag; }
    private static ListTag list(Tag... values) { var list = new ListTag(); for (Tag value : values) list.add(value); return list; }
    private static CompoundTag stack(int count) {
        var tag = new CompoundTag(); tag.putString("id", "minecraft:diamond"); tag.putInt("count", count);
        var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("actual-source", "original paid stock"); components.put("minecraft:custom_data", custom); tag.put("components", components); return tag;
    }
    private static CompoundTag equipped() {
        var tag = entity("minecraft:pig"); tag.store("UUID", UUIDUtil.CODEC, UUID.randomUUID());
        tag.put("ArmorItems", list(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag()));
        tag.put("HandItems", list(stack(11), new CompoundTag())); return tag;
    }
    private static void refuses(GameTestHelper h, CompoundTag source) {
        var before = source.copy(); boolean refused = false;
        try { NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); }
        catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused && before.equals(source), "Original invalid typed source is refused without modifying the original tree");
    }
    @GameTest public void sourceEquipmentUsesActualNativeCodecAndPreservesOriginalEleven(GameTestHelper h) {
        var source = equipped(); var before = source.copy(); var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        var equipment = EntityEquipment.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), actual.get("equipment")).getOrThrow();
        h.assertTrue(source.equals(before) && source.get("UUID").equals(actual.get("UUID")) && equipment.get(EquipmentSlot.MAINHAND).is(Items.DIAMOND)
            && equipment.get(EquipmentSlot.MAINHAND).getCount() == 11 && !actual.contains("HandItems"), "Original entity UUID and eleven physical items survive one genuine ENTITY_TREE DFU with native equipment schema"); h.succeed();
    }
    @GameTest public void genuineTemplateWithoutUuidNeverReceivesSyntheticOwner(GameTestHelper h) {
        var actual = NeoForgeVanillaEntityImport.convert(entity("minecraft:pig"), 3955, h.getLevel().registryAccess());
        h.assertTrue(!actual.contains("UUID"), "Entity template remains without any manufactured UUID/owner epoch"); h.succeed();
    }
    @GameTest public void unknownEquipmentComponentsAndOutOfRangePhysicalCountsRefuse(GameTestHelper h) {
        var source = equipped(); source.getList("HandItems").orElseThrow().getCompoundOrEmpty(0).getCompound("components").orElseThrow().putString("other:unreviewed", "must not become empty"); refuses(h, source);
        for (int count : new int[] {0, 100}) { source = equipped(); source.getList("HandItems").orElseThrow().getCompoundOrEmpty(0).putInt("count", count); refuses(h, source); }
        h.succeed();
    }
    @GameTest public void actualSparseVanillaCartSlotsRemainUniqueAndByteTyped(GameTestHelper h) {
        var source = entity("minecraft:chest_minecart"); var entry = stack(11); entry.putByte("Slot", (byte) 2); source.put("Items", list(entry));
        var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); var row = actual.getList("Items").orElseThrow().getCompoundOrEmpty(0);
        h.assertTrue(row.getByte("Slot").orElseThrow() == 2 && row.getInt("count").orElseThrow() == 11, "Actual sparse cart stock keeps its original slot and finite count");
        source.getList("Items").orElseThrow().add(entry.copy()); refuses(h, source);
        source.put("Items", list(entry.copy())); source.getList("Items").orElseThrow().getCompoundOrEmpty(0).putInt("Slot", 2); refuses(h, source); h.succeed();
    }
    @GameTest public void inventoryCarrierUsesGenuineDenseRowsAndRealCapacity(GameTestHelper h) {
        var source = entity("minecraft:allay"); source.put("Inventory", list(stack(7)));
        var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        h.assertTrue(actual.getList("Inventory").orElseThrow().getCompoundOrEmpty(0).getInt("count").orElseThrow() == 7, "Allay's actual one-slot Inventory carrier retains its seven source items");
        source.getList("Inventory").orElseThrow().add(stack(1)); refuses(h, source);
        source.put("Inventory", list(stack(7))); source.getList("Inventory").orElseThrow().getCompoundOrEmpty(0).putByte("Slot", (byte) 0); refuses(h, source); h.succeed();
    }
    @GameTest public void merchantCostKeepsArbitraryPositiveCountAndNeverClampsToPhysicalStackLimit(GameTestHelper h) {
        var source = entity("minecraft:villager"); var offer = new CompoundTag(); offer.put("buy", stack(1000)); offer.put("sell", stack(7));
        var offers = new CompoundTag(); offers.put("Recipes", list(offer)); source.put("Offers", offers);
        var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        var nativeOffers = MerchantOffers.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), actual.get("Offers")).getOrThrow();
        h.assertTrue(!nativeOffers.isEmpty() && actual.getCompound("Offers").orElseThrow().getList("Recipes").orElseThrow().getCompoundOrEmpty(0).getCompound("buy").orElseThrow().getInt("count").orElseThrow() == 1000, "Source ItemCost count1000 survives; only its component validation probe has count1");
        offer.getCompound("buy").orElseThrow().getCompound("components").orElseThrow().putString("other:unreviewed", "predicate"); refuses(h, source); h.succeed();
    }
    @GameTest public void nestedSourcePassengersKeepExactOriginalUuidsAndRejectDuplicateOwners(GameTestHelper h) {
        var source = equipped(); var child = entity("minecraft:parrot"); child.store("UUID", UUIDUtil.CODEC, UUID.randomUUID()); source.put("Passengers", list(child));
        var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        h.assertTrue(actual.getList("Passengers").orElseThrow().getCompoundOrEmpty(0).get("UUID").equals(child.get("UUID")), "Exact original nested entity UUID survives without any factory/world admission");
        child.put("UUID", source.get("UUID").copy()); refuses(h, source); h.succeed();
    }
    @GameTest public void genuineSpawnProvenanceSurvivesActualNativeFactoryAdmissionAndResave(GameTestHelper h) {
        var source = equipped(); var position = h.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        source.put("Pos", list(DoubleTag.valueOf(position.getX() + .5), DoubleTag.valueOf(position.getY()), DoubleTag.valueOf(position.getZ() + .5)));
        source.put("Motion", list(DoubleTag.valueOf(0), DoubleTag.valueOf(0), DoubleTag.valueOf(0)));
        source.put("Rotation", list(FloatTag.valueOf(0), FloatTag.valueOf(0))); source.putBoolean("NoAI", true);
        source.putString("neoforge:spawn_type", "TRIAL_SPAWNER"); var originalId = UUIDUtil.CODEC.parse(NbtOps.INSTANCE, source.get("UUID")).getOrThrow();
        var nativeTag = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        Entity original = null, restored = null;
        try {
            original = EntityType.loadEntityRecursive(nativeTag, h.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
            h.assertTrue(original != null && original.getUUID().equals(originalId) && h.getLevel().addFreshEntity(original), "Real native LOAD factory admits the original saved Mob UUID");
            var origin = ((NeoForgeEntityProvenanceOwner) original).create$neoForgeSpawnOrigin().orElseThrow();
            h.assertTrue(origin.sourceDataVersion() == 3955 && origin.sourceSpawnType().equals("TRIAL_SPAWNER"), "Original spawn provenance remains historical metadata alongside the actual native LOAD");
            var problems = new ProblemReporter.Collector(original.problemPath()); var output = TagValueOutput.createWithContext(problems, original.registryAccess());
            h.assertTrue(original.saveAsPassenger(output) && problems.isEmpty(), "Actual admitted native Mob re-saves through real ValueOutput and provenance mixin");
            var saved = output.buildResult(); original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            restored = EntityType.loadEntityRecursive(saved, h.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
            h.assertTrue(restored != null && restored.getUUID().equals(originalId) && h.getLevel().addFreshEntity(restored)
                && ((NeoForgeEntityProvenanceOwner) restored).create$neoForgeSpawnOrigin().orElseThrow().equals(origin)
                && h.getLevel().getEntity(originalId) == restored, "Same original Mob owner retains exact source provenance through native unload/factory/re-save without a second admitted UUID owner");
        } finally { if (original != null && !original.isRemoved()) original.discard(); if (restored != null && !restored.isRemoved()) restored.discard(); }
        h.succeed();
    }
    @GameTest public void sourceAndNativeProvenanceSchemasAreIndependentlyGuarded(GameTestHelper h) {
        var source = entity("minecraft:pig"); source.putString("neoforge:spawn_type", "UNKNOWN_FUTURE"); refuses(h, source);
        source = entity("minecraft:pig"); source.put(NeoForgeEntityProvenance.KEY, new CompoundTag()); refuses(h, source);
        var nativeOrigin = NeoForgeEntityProvenance.write(new NeoForgeEntityProvenance.SpawnOrigin(3955, "NATURAL")); nativeOrigin.putInt("SourceDataVersion", 9999);
        boolean rejected = false; try { NeoForgeEntityProvenance.read(nativeOrigin); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "Native provenance refuses an invented source data version rather than defaulting to absence");
        nativeOrigin.putInt("SourceDataVersion", 3955); nativeOrigin.putString("FutureOwnedPayload", "never dropped"); rejected = false;
        try { NeoForgeEntityProvenance.read(nativeOrigin); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "Unknown native provenance schema cannot be silently discarded"); h.succeed();
    }
    private static CompoundTag positioned(GameTestHelper h, CompoundTag source) {
        var position = h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        source.put("Pos", list(DoubleTag.valueOf(position.getX() + .5), DoubleTag.valueOf(position.getY()), DoubleTag.valueOf(position.getZ() + .5)));
        source.put("Motion", list(DoubleTag.valueOf(0), DoubleTag.valueOf(0), DoubleTag.valueOf(0)));
        source.put("Rotation", list(FloatTag.valueOf(0), FloatTag.valueOf(0))); return source;
    }
    private static Entity nativeLoad(GameTestHelper h, CompoundTag source) {
        return EntityType.loadEntityRecursive(source, h.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
    }
    private static CompoundTag nativeSave(Entity actual) {
        var problems = new ProblemReporter.Collector(actual.problemPath()); var output = TagValueOutput.createWithContext(problems, actual.registryAccess());
        if (!actual.saveAsPassenger(output) || !problems.isEmpty()) throw new AssertionError("Actual native entity save: " + problems.getReport()); return output.buildResult();
    }
    private static Horse originalHorse(GameTestHelper h, int progress) {
        var source = positioned(h, equipped()); source.putString("id", "minecraft:horse"); source.putBoolean("NoAI", true); source.putBoolean("Tame", true);
        var saddle = new CompoundTag(); saddle.putString("id", "minecraft:saddle"); saddle.putInt("count", 1); source.put("SaddleItem", saddle);
        var armor = new CompoundTag(); armor.putString("id", "minecraft:diamond_horse_armor"); armor.putInt("count", 1); source.put("body_armor_item", armor);
        var persistent = new CompoundTag(); persistent.putInt("CreateHaunting", progress); persistent.putBoolean("CreateFireImmune", false); source.put("NeoForgeData", persistent);
        var actual = nativeLoad(h, NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()));
        h.assertTrue(actual instanceof Horse && h.getLevel().addFreshEntity(actual), "Actual original source horse is admitted with genuine gear and source progress"); return (Horse) actual;
    }
    private static SkeletonHorse convertedHorse(GameTestHelper h, Horse original) {
        var horses = h.getLevel().getEntitiesOfClass(SkeletonHorse.class, original.getBoundingBox().inflate(2));
        h.assertTrue(horses.size() == 1 && original.isRemoved(), "Exactly one real native skeleton horse replaces the original horse"); return horses.getFirst();
    }
    private static int droppedArmor(GameTestHelper h, Entity near) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, near.getBoundingBox().inflate(2)).stream().filter(item -> item.getItem().is(Items.DIAMOND_HORSE_ARMOR)).mapToInt(item -> item.getItem().getCount()).sum();
    }
    @GameTest public void originalHauntingProgressRetainsActualGearRiderAndOneArmorDropAcrossConversion(GameTestHelper h) {
        var horse = originalHorse(h, 99); var rider = EntityTypes.PIG.create(h.getLevel(), EntitySpawnReason.COMMAND); SkeletonHorse converted = null;
        h.assertTrue(rider != null && h.getLevel().addFreshEntity(rider) && rider.startRiding(horse, true, true), "Real native rider is owned by the original horse before conversion");
        try {
            HauntingHorseConversion.process(h.getLevel(), horse);
            h.assertTrue(!horse.isRemoved() && ((AttachmentTarget) horse).getAttached(HauntingHorseConversion.PROGRESS) == 100, "Original99 advances once to100 without premature source retirement");
            HauntingHorseConversion.process(h.getLevel(), horse); converted = convertedHorse(h, horse);
            h.assertTrue(converted.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND) && converted.getItemBySlot(EquipmentSlot.MAINHAND).getCount() == 11
                && converted.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE) && converted.getItemBySlot(EquipmentSlot.BODY).isEmpty()
                && droppedArmor(h, converted) == 1 && rider.getVehicle() == converted, "Actual section insertion transfers eleven original items, one saddle and the real rider, while source body armor becomes exactly one actual drop");
            rider.stopRiding(); var saved = nativeSave(converted); var id = converted.getUUID(); converted.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored = nativeLoad(h, saved); h.assertTrue(restored instanceof SkeletonHorse && restored.getUUID().equals(id) && h.getLevel().addFreshEntity(restored), "Actual converted native horse reloads with the same admitted UUID");
            converted = (SkeletonHorse) restored;
            h.assertTrue(converted.getItemBySlot(EquipmentSlot.MAINHAND).getCount() == 11 && converted.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE)
                && converted.getItemBySlot(EquipmentSlot.BODY).isEmpty() && droppedArmor(h, converted) == 1, "Native re-save cannot replay the old physical armor or haunting counter");
        } finally { if (!horse.isRemoved()) horse.discard(); if (converted != null && !converted.isRemoved()) converted.discard(); if (rider != null && !rider.isRemoved()) rider.discard(); }
        h.succeed();
    }
    @GameTest public void nativeRejectedHorseAndArmorEndpointsLeaveOneActualStockOwner(GameTestHelper h) throws Exception {
        var horse = originalHorse(h, 100); SkeletonHorse converted = null;
        try {
            try (var rejection = CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity -> entity instanceof SkeletonHorse, entity -> entity.setUUID(horse.getUUID()))) {
                HauntingHorseConversion.process(h.getLevel(), horse);
            }
            h.assertTrue(!horse.isRemoved() && h.getLevel().getEntity(horse.getUUID()) == horse && horse.getItemBySlot(EquipmentSlot.MAINHAND).getCount() == 11
                && horse.getItemBySlot(EquipmentSlot.BODY).is(Items.DIAMOND_HORSE_ARMOR), "Actual native UUID rejection keeps the source horse and all its original physical gear");
            try (var rejection = CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity -> entity instanceof ItemEntity item && item.getItem().is(Items.DIAMOND_HORSE_ARMOR), entity -> entity.setUUID(h.getLevel().getEntitiesOfClass(SkeletonHorse.class, horse.getBoundingBox().inflate(2)).getFirst().getUUID()))) {
                HauntingHorseConversion.process(h.getLevel(), horse);
            }
            converted = convertedHorse(h, horse);
            h.assertTrue(!converted.getItemBySlot(EquipmentSlot.BODY).isEmpty() && droppedArmor(h, converted) == 0,
                "Rejected actual armor drop stays on its one real converted horse owner");
            HauntingHorseConversion.retryBodyArmor(h.getLevel(), converted);
            h.assertTrue(converted.getItemBySlot(EquipmentSlot.BODY).isEmpty() && droppedArmor(h, converted) == 1, "A subsequent actual accepted endpoint consumes that exact body slot once");
        } finally { if (!horse.isRemoved()) horse.discard(); if (converted != null && !converted.isRemoved()) converted.discard(); }
        h.succeed();
    }
    @GameTest public void realPendingFanUsesActualItemOwnerOnceAndNativeSavedProgressWins(GameTestHelper h) {
        var source = positioned(h, entity("minecraft:item")); source.store("UUID", UUIDUtil.CODEC, UUID.randomUUID()); source.put("Item", stack(11)); source.putInt("Lifespan", 6000);
        var progress = new CompoundTag(); progress.putString("Type", "create:smoking"); progress.putInt("Time", 23); var create = new CompoundTag(); create.put("Processing", progress); var old = new CompoundTag(); old.put("CreateData", create);
        var bypass = h.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2)); var originalPos = new CompoundTag(); originalPos.putInt("X", bypass.getX()); originalPos.putInt("Y", bypass.getY()); originalPos.putInt("Z", bypass.getZ()); old.put("BypassCrushingWheel", originalPos); source.put("NeoForgeData", old);
        var actual = nativeLoad(h, NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess())); Entity restored = null;
        try {
            h.assertTrue(actual instanceof ItemEntity && h.getLevel().addFreshEntity(actual), "Actual native ItemEntity owns all eleven original source items");
            var target = (AttachmentTarget) actual; var nativeProgress = target.getAttached(FanProcessing.PROGRESS);
            h.assertTrue(nativeProgress != null && nativeProgress.time() == 23 && nativeProgress.count() == 11 && nativeProgress.lastTick() == h.getLevel().getGameTime() && nativeProgress.type().equals("create:smoking") && nativeProgress.input().matches(((ItemEntity) actual).getItem()) && bypass.equals(target.getAttached(CrushingWheelControllerBlockEntity.BYPASS)), "Genuine source timer23 binds to the actual current native ItemEntity and native scheduling tick: progress=" + nativeProgress + ", actualTick=" + h.getLevel().getGameTime() + ", bypass=" + target.getAttached(CrushingWheelControllerBlockEntity.BYPASS) + ", expectedBypass=" + bypass);
            target.setAttached(FanProcessing.PROGRESS, new FanProcessing.Progress(nativeProgress.type(), nativeProgress.input(), 11, 5, nativeProgress.lastTick()));
            var saved = nativeSave(actual); actual.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); restored = nativeLoad(h, saved);
            h.assertTrue(restored instanceof ItemEntity item && h.getLevel().addFreshEntity(restored) && item.getItem().getCount() == 11
                && ((AttachmentTarget) restored).getAttached(FanProcessing.PROGRESS).time() == 5
                && bypass.equals(((AttachmentTarget) restored).getAttached(CrushingWheelControllerBlockEntity.BYPASS)), "Native timer5, exact native bypass position and their one actual stock owner win over retained historical source timer23 after real save/load");
        } finally { if (actual != null && !actual.isRemoved()) actual.discard(); if (restored != null && !restored.isRemoved()) restored.discard(); }
        h.succeed();
    }
    @GameTest public void genuineCoreHatAndDismountStateMapsOnceAndHistoryNeverReactivatesConsumedState(GameTestHelper h) {
        var source = positioned(h, entity("minecraft:parrot")); source.store("UUID", UUIDUtil.CODEC, UUID.randomUUID());
        var data = new CompoundTag(); data.putBoolean("TrainHat", true); data.putByte("CreateNetheriteDivingBits", (byte) 0); data.putBoolean("CreateFireImmune", false);
        data.put("ContraptionDismountLocation", list(DoubleTag.valueOf(12.5), DoubleTag.valueOf(128), DoubleTag.valueOf(-3.25))); source.put("NeoForgeData", data);
        var actual = nativeLoad(h, NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess())); Entity restored = null;
        try {
            h.assertTrue(actual != null && h.getLevel().addFreshEntity(actual) && ConductorHatState.has(actual), "Original TrainHat maps to the actual native persistent conductor attachment");
            var dismount = ContraptionPassengerData.takeImportedDismountLocation(actual);
            h.assertTrue(dismount != null && dismount.equals(new net.minecraft.world.phys.Vec3(12.5, 128, -3.25)), "Actual passenger owns and consumes its exact genuine source dismount vector");
            ConductorHatState.set(actual, false); var saved = nativeSave(actual); var historical = NeoForgeEntityPersistentDataImport.read(saved.getCompound(NeoForgeEntityPersistentDataImport.KEY).orElseThrow());
            h.assertTrue(historical.payload().equals(data), "Exact original core fields remain versioned historical data while current native equipped stacks control fire immunity");
            actual.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); restored = nativeLoad(h, saved);
            h.assertTrue(restored != null && h.getLevel().addFreshEntity(restored) && !ConductorHatState.has(restored) && ContraptionPassengerData.takeImportedDismountLocation(restored) == null,
                "Native consumed dismount and removed hat stay consumed after real native save/load; historical source metadata never replays active state");
        } finally { if (actual != null && !actual.isRemoved()) actual.discard(); if (restored != null && !restored.isRemoved()) restored.discard(); }
        h.succeed();
    }
    @GameTest public void customTypesPlayersAndUnconvertedExtensionOwnersAreExplicitlyRefused(GameTestHelper h) {
        refuses(h, entity("create:contraption")); refuses(h, entity("minecraft:player"));
        var source = entity("minecraft:pig"); var attachment = new CompoundTag(); attachment.putInt("ActualUnconvertedStock", 11); source.put("neoforge:attachments", attachment); refuses(h, source);
        source = entity("minecraft:falling_block"); source.put("TileEntityData", attachment); refuses(h, source);
        source = entity("minecraft:pig"); source.put("FutureOwnedInventory", attachment); refuses(h, source); h.succeed();
    }

    private static CompoundTag textWithCreateHover(int count) {
        var result = stack(count);
        var sample = stack(3); sample.putString("id", "minecraft:emerald");
        var sampleComponents = new CompoundTag(); sampleComponents.putString("minecraft:custom_name", "{\"text\":\"Nested3955\"}"); sample.put("components", sampleComponents);
        var slot = new CompoundTag(); slot.putInt("slot", 17); slot.put("item", sample);
        var filter = new CompoundTag(); filter.putString("id", "create:filter"); filter.putInt("count", 1);
        var filterComponents = new CompoundTag(); filterComponents.put("create:filter_items", list(slot)); filter.put("components", filterComponents);
        var hover = new CompoundTag(); hover.putString("action", "show_item"); hover.put("contents", filter);
        var text = new CompoundTag(); text.putString("text", "Historical item"); text.putBoolean("bold", true); text.put("hoverEvent", hover);
        var originalJson = NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, text).getAsJsonObject(); originalJson.addProperty("bold", true);
        result.getCompoundOrEmpty("components").putString("minecraft:custom_name", originalJson.toString());
        return result;
    }
    private static net.minecraft.world.item.ItemStack assertStoredText(GameTestHelper h, CompoundTag actual) {
        var stack = net.minecraft.world.item.ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), actual).getOrThrow();
        var text = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        h.assertTrue(text != null && text.getString().equals("Historical item") && text.getStyle().isBold()
            && text.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowItem,
            "The actual returned native entity item has semantic text/style and a typed rich item hover");
        var hover = (net.minecraft.network.chat.HoverEvent.ShowItem) text.getStyle().getHoverEvent();
        var filter = hover.item().create(); var samples = filter.get(com.simibubi.create.AllDataComponents.FILTER_ITEMS).itemCopies().toList();
        h.assertTrue(filter.is(com.simibubi.create.AllItems.FILTER) && samples.size() == 18
            && samples.subList(0, 17).stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty) && samples.get(17).is(Items.EMERALD)
            && samples.get(17).getCount() == 3 && samples.get(17).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("Nested3955"),
            "Actual hover filter retains all seventeen empty source positions and its genuine named three-emerald sample in exact slot17");
        return stack;
    }
    @GameTest public void actualReturnedEquipmentAndNestedPassengerItemsReceiveTypedTextPostpass(GameTestHelper h) {
        var source = equipped(); source.put("HandItems", list(textWithCreateHover(11), new CompoundTag()));
        var passenger = entity("minecraft:item"); passenger.put("Item", textWithCreateHover(7)); passenger.putInt("Lifespan", 6000); source.put("Passengers", list(passenger));
        var old = source.copy(); var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        var hand = assertStoredText(h, actual.getCompoundOrEmpty("equipment").getCompoundOrEmpty("mainhand"));
        var drop = assertStoredText(h, actual.getListOrEmpty("Passengers").getCompoundOrEmpty(0).getCompoundOrEmpty("Item"));
        h.assertTrue(source.equals(old) && hand.getCount() == 11 && drop.getCount() == 7,
            "Postpass writes actual equipment and child leaf values without mutating original source or changing real counts"); h.succeed();
    }
    @GameTest public void actualSparseAndDenseInventoryRowsReceiveTextWithoutLosingByteSlotCustody(GameTestHelper h) {
        var source = entity("minecraft:chest_minecart"); var row = textWithCreateHover(11); row.putByte("Slot", (byte) 26); source.put("Items", list(row));
        var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); var savedRow = actual.getListOrEmpty("Items").getCompoundOrEmpty(0).copy();
        h.assertTrue(savedRow.get("Slot") instanceof net.minecraft.nbt.ByteTag && savedRow.getByte("Slot").orElseThrow() == 26,
            "Original source byte slot remains on its actual sparse inventory row"); savedRow.remove("Slot");
        h.assertTrue(assertStoredText(h, savedRow).getCount() == 11, "Actual sparse row receives returned postprocessed components");
        source = entity("minecraft:allay"); source.put("Inventory", list(textWithCreateHover(7)));
        actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        h.assertTrue(assertStoredText(h, actual.getListOrEmpty("Inventory").getCompoundOrEmpty(0)).getCount() == 7,
            "Actual dense row receives returned postprocessed components"); h.succeed();
    }
    @GameTest public void actualMerchantCostAndSellTextKeepLargeRealCostAndNativeResave(GameTestHelper h) {
        var source = entity("minecraft:villager"); var offer = new CompoundTag(); offer.put("buy", textWithCreateHover(1000)); offer.put("sell", textWithCreateHover(7));
        var offers = new CompoundTag(); offers.put("Recipes", list(offer)); source.put("Offers", offers); var old = source.copy();
        var actual = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var nativeOffers = MerchantOffers.CODEC.parse(ops, actual.get("Offers")).getOrThrow();
        var resaved = (CompoundTag) MerchantOffers.CODEC.encodeStart(ops, nativeOffers).getOrThrow(); var nativeOffer = resaved.getListOrEmpty("Recipes").getCompoundOrEmpty(0);
        var buy = nativeOffer.getCompoundOrEmpty("buy").copy(); h.assertTrue(buy.getInt("count").orElseThrow() == 1000, "Original ItemCost count1000 remains real after native merchant re-save"); buy.putInt("count", 1);
        assertStoredText(h, buy); h.assertTrue(assertStoredText(h, nativeOffer.getCompoundOrEmpty("sell")).getCount() == 7 && source.equals(old),
            "Actual native re-saved buy/sell retain typed text and source stays unchanged"); h.succeed();
    }
}
