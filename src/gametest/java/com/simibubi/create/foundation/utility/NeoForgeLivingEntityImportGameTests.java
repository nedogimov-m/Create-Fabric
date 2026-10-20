package com.simibubi.create.foundation.utility;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueOutput;

/** Genuine source shapes, actual native factories and ValueOutput reloads; authored4 UNRUN. */
public final class NeoForgeLivingEntityImportGameTests {
    private static ListTag rows(Tag... values) { var result = new ListTag(); for (var value : values) result.add(value); return result; }
    private static ListTag vector(double x, double y, double z) { return rows(DoubleTag.valueOf(x), DoubleTag.valueOf(y), DoubleTag.valueOf(z)); }
    private static CompoundTag xyz(BlockPos pos) { var result = new CompoundTag(); result.putInt("X", pos.getX()); result.putInt("Y", pos.getY()); result.putInt("Z", pos.getZ()); return result; }
    private static CompoundTag attribute(String id, double base) { var result = new CompoundTag(); result.putString("id", id); result.putDouble("base", base); return result; }
    private static CompoundTag base(GameTestHelper h, String type) {
        var result = new CompoundTag(); result.putString("id", type); result.store("UUID", UUIDUtil.CODEC, UUID.randomUUID());
        var pos = h.absolutePos(new BlockPos(2, 3, 2)); result.put("Pos", vector(pos.getX() + .5, pos.getY(), pos.getZ() + .5));
        result.put("Motion", vector(0, 0, 0)); result.put("Rotation", rows(FloatTag.valueOf(0), FloatTag.valueOf(0)));
        result.putFloat("FallDistance", 0); result.putShort("Fire", (short) -1); result.putShort("Air", (short) 300);
        result.putBoolean("OnGround", false); result.putBoolean("Invulnerable", false); result.putInt("PortalCooldown", 0); result.putBoolean("NoGravity", true); return result;
    }
    private static CompoundTag speedEffect() {
        var result = new CompoundTag(); result.putString("id", "minecraft:speed"); result.putByte("amplifier", (byte) 1); result.putInt("duration", 120);
        result.put("neoforge:cures", rows(StringTag.valueOf("milk"), StringTag.valueOf("protected_by_totem")));
        var hidden = new CompoundTag(); hidden.putByte("amplifier", (byte) 0); hidden.putInt("duration", 240); hidden.put("neoforge:cures", result.get("neoforge:cures").copy()); result.put("hidden_effect", hidden); return result;
    }
    private static CompoundTag pig(GameTestHelper h) {
        var result = base(h, "minecraft:pig"); result.put("ArmorItems", rows(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag())); result.put("HandItems", rows(new CompoundTag(), new CompoundTag()));
        result.putFloat("Health", 13); result.putFloat("AbsorptionAmount", 2); result.putShort("HurtTime", (short) 0); result.putShort("DeathTime", (short) 0); result.putInt("HurtByTimestamp", 17); result.putBoolean("FallFlying", false);
        result.put("attributes", rows(attribute("minecraft:generic.max_health", 30), attribute("minecraft:generic.movement_speed", .33), attribute("minecraft:generic.gravity", .08), attribute("neoforge:nametag_distance", 32), attribute("neoforge:swim_speed", 1), attribute("neoforge:creative_flight", 0)));
        result.put("active_effects", rows(speedEffect())); var brain = new CompoundTag(); brain.put("memories", new CompoundTag()); result.put("Brain", brain);
        result.putBoolean("NoAI", true); result.putBoolean("CanPickUpLoot", false); result.putBoolean("PersistenceRequired", true); result.putBoolean("LeftHanded", false); result.putInt("Age", 0); result.putInt("ForcedAge", 0); result.putInt("InLove", 0); return result;
    }
    private static Entity admit(GameTestHelper h, CompoundTag current) {
        var result = EntityType.loadEntityRecursive(current, h.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
        h.assertTrue(result != null && h.getLevel().addFreshEntity(result), "The actual native LOAD factory admits one real entity with its original saved UUID"); return result;
    }
    private static CompoundTag save(Entity actual) {
        var problems = new ProblemReporter.Collector(actual.problemPath()); var output = TagValueOutput.createWithContext(problems, actual.registryAccess());
        if (!actual.saveAsPassenger(output) || !problems.isEmpty()) throw new AssertionError("Actual native save: " + problems.getReport()); return output.buildResult();
    }
    private static void refuses(GameTestHelper h, CompoundTag original) {
        var before = original.copy(); boolean rejected = false;
        try { NeoForgeVanillaEntityImport.convert(original, 3955, h.getLevel().registryAccess()); } catch (RuntimeException expected) { rejected = true; }
        h.assertTrue(rejected && original.equals(before), "Unreviewed source semantics refuse before native publication and leave the genuine original unchanged");
    }

    @GameTest public void genuineSavedLivingAliasesEffectsAndInertHistorySurviveNativeMutationAndReload(GameTestHelper h) {
        var source = pig(h); var before = source.copy(); var current = NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        var history = NeoForgeLivingHistory.read(current.getCompoundOrEmpty(NeoForgeLivingHistory.KEY));
        h.assertTrue(source.equals(before) && history.boundary() == NeoForgeLivingHistory.Boundary.SAVED && history.originalEntity().orElseThrow().equals(source.read("UUID", UUIDUtil.CODEC).orElseThrow())
            && history.originalValues().get("attributes").equals(source.get("attributes")) && history.originalValues().get("active_effects").equals(source.get("active_effects")) && history.originalValues().getInt("HurtByTimestamp").orElseThrow() == 17
            && !current.contains("HurtByTimestamp") && !current.contains("last_hurt_by_mob") && current.getListOrEmpty("attributes").size() == 4,
            "Exact original rows/cures/timestamp retain source custody as inert evidence; unmatched proven identity defaults never become another native behavior");
        LivingEntity original = null, restored = null;
        try {
            original = (LivingEntity) admit(h, current);
            h.assertTrue(original.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 30 && original.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == .33
                && original.getAttribute(Attributes.NAME_TAG_DISTANCE).getBaseValue() == 32 && original.getEffect(MobEffects.SPEED).getDuration() == 120 && original.getEffect(MobEffects.SPEED).getAmplifier() == 1
                && original.getLastHurtByMob() == null && original.getLastHurtByPlayer() == null && ((NeoForgeLivingHistory.Holder) original).create$originalLivingHistory().orElseThrow().equals(history),
                "Actual native pig reads mapped attributes/default-equivalent effects and exact inert history without inventing an old attacker");
            original.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.22); original.removeEffect(MobEffects.SPEED); original.setHealth(18); var saved = save(original); var id = original.getUUID(); original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            restored = (LivingEntity) admit(h, saved);
            h.assertTrue(restored.getUUID().equals(id) && restored.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == .22 && restored.getHealth() == 18 && restored.getEffect(MobEffects.SPEED) == null
                && restored.getLastHurtByMob() == null && ((NeoForgeLivingHistory.Holder) restored).create$originalLivingHistory().orElseThrow().equals(history),
                "Fresh real native reload preserves later native attribute/health/effect changes; the saved old rows and cures never replay");
        } finally { if (original != null && !original.isRemoved()) original.discard(); if (restored != null && !restored.isRemoved()) restored.discard(); }
        h.succeed();
    }

    @GameTest public void partialLivingTemplatesKeepPresenceAndNeverPromoteTemplateUuidIntoSavedHistoryOwner(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("id", "minecraft:pig"); source.putInt("HurtByTimestamp", 0);
        var current = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); var history = NeoForgeLivingHistory.read(current.getCompoundOrEmpty(NeoForgeLivingHistory.KEY));
        h.assertTrue(!current.contains("UUID") && !current.contains("attributes") && !current.contains("active_effects") && history.boundary() == NeoForgeLivingHistory.Boundary.TEMPLATE && history.originalEntity().isEmpty(),
            "A genuine partial living template has no manufactured entity UUID, stock attribute defaults, effects or saved owner");
        source.store("UUID", UUIDUtil.CODEC, UUID.randomUUID()); source.put("attributes", rows(attribute("neoforge:swim_speed", 1)));
        current = NeoForgeVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess(), NeoForgeMigrationScope.worldBlock(Identifier.parse("minecraft:overworld"), h.absolutePos(BlockPos.ZERO)));
        history = NeoForgeLivingHistory.read(current.getCompoundOrEmpty(NeoForgeLivingHistory.KEY));
        h.assertTrue(current.get("UUID").equals(source.get("UUID")) && history.boundary() == NeoForgeLivingHistory.Boundary.TEMPLATE && history.originalEntity().isEmpty(), "An optional UUID in template data remains data; only complete saved admission establishes an actual entity owner");
        var forged = history.write(); forged.store("Entity", UUIDUtil.CODEC, source.read("UUID", UUIDUtil.CODEC).orElseThrow()); boolean rejected = false; try { NeoForgeLivingHistory.read(forged); } catch (RuntimeException expected) { rejected = true; }
        h.assertTrue(rejected, "Typed native history refuses promotion of a template UUID into an original saved owner"); h.succeed();
    }

    private static CompoundTag packed() {
        var state = new CompoundTag(); state.putString("Name", BuiltInRegistries.BLOCK.getKey(AllBlocks.MINECART_ANCHOR).toString()); var properties = new CompoundTag(); AllBlocks.MINECART_ANCHOR.defaultBlockState().getValues().forEach(value -> properties.putString(value.property().getName(), value.valueName())); if (!properties.isEmpty()) state.put("Properties", properties);
        var row = new CompoundTag(); row.putLong("Pos", BlockPos.ZERO.asLong()); row.putInt("State", 0); var blocks = new CompoundTag(); blocks.put("Palette", rows(state)); blocks.put("BlockList", rows(row)); var body = new CompoundTag(); body.putString("Type", "create:mounted"); body.putString("RotationMode", "ROTATE"); body.putString("InitialOrientation", "EAST"); body.put("Anchor", xyz(BlockPos.ZERO)); body.putBoolean("Stalled", false); body.putBoolean("BottomlessSupply", false); body.put("Blocks", blocks);
        for (String key : Set.of("CapturedMultiblocks", "Actors", "DisabledActors", "Interactors", "Superglue", "Seats", "Passengers", "SubContraptions")) body.put(key, new ListTag());
        var components = new CompoundTag(); components.put("create:minecart_contraption_data", body); var result = new CompoundTag(); result.putString("id", "create:minecart_contraption"); result.putInt("count", 1); result.put("components", components); return result;
    }
    private static String richText() { return richText(packed()); }
    private static String richText(CompoundTag actualPacked) {
        var hover = new CompoundTag(); hover.putString("action", "show_item"); hover.put("contents", actualPacked); var text = new CompoundTag(); text.putString("text", "Original display"); text.put("hoverEvent", hover);
        return NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, text).toString();
    }
    private static CompoundTag plan(GameTestHelper h, Component text) {
        h.assertTrue(text.getString().equals("Original display") && text.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem, "Actual native text retains its genuine rich item hover");
        var result = ((HoverEvent.ShowItem) text.getStyle().getHoverEvent()).item().create().get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
        h.assertTrue(result != null && result.contains("NativePlanId") && !result.contains("CustodyReceipt") && !result.contains("BaseCarts"), "Hover value owns a scoped NEW transport identity without creating paid material or an entity owner"); return result;
    }
    @GameTest public void savedDisplayTextAndCustomNameKeepDistinctScopedPackedHoversAcrossRealNativeReload(GameTestHelper h) {
        var source = base(h, "minecraft:text_display"); source.putString("text", richText()); source.putString("CustomName", richText()); var before = source.copy();
        var genuineJson=(CompoundTag)com.mojang.serialization.JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE,com.google.gson.JsonParser.parseString(richText()));
        var genuinePosition=genuineJson.getCompoundOrEmpty("hoverEvent").getCompoundOrEmpty("contents").getCompoundOrEmpty("components").getCompoundOrEmpty("create:minecart_contraption_data").getCompoundOrEmpty("Blocks").getListOrEmpty("BlockList").getCompoundOrEmpty(0).get("Pos");
        h.assertTrue(genuinePosition instanceof ByteTag&&((ByteTag)genuinePosition).byteValue()==0,"Actual original flattened CompoundTag hover JSON reconstructs genuine saved packed LongTag0 as ByteTag0; source geometry must retain its exact integer value");
        var current = NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); var repeated = NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var displayPlan = plan(h, ComponentSerialization.CODEC.parse(ops, current.get("text")).getOrThrow()); var namePlan = plan(h, ComponentSerialization.CODEC.parse(ops, current.get("CustomName")).getOrThrow());
        h.assertTrue(source.equals(before) && current.equals(repeated) && !displayPlan.get("NativePlanId").equals(namePlan.get("NativePlanId")), "The same actual saved entity UUID with display_text/customname roles gives stable distinct NEW value identities while exact source text stays unchanged");
        Display.TextDisplay original = null, restored = null;
        try {
            original = (Display.TextDisplay) admit(h, current); h.assertTrue(plan(h, original.getText()).equals(displayPlan) && plan(h, original.getCustomName()).equals(namePlan), "Real native TextDisplay reads both exact scoped hover values");
            var saved = save(original); var id = original.getUUID(); original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); restored = (Display.TextDisplay) admit(h, saved);
            h.assertTrue(restored.getUUID().equals(id) && plan(h, restored.getText()).equals(displayPlan) && plan(h, restored.getCustomName()).equals(namePlan), "Native display save/factory reload keeps each real value identity without a source replay or second material owner");
        } finally { if (original != null && !original.isRemoved()) original.discard(); if (restored != null && !restored.isRemoved()) restored.discard(); } h.succeed();
    }

    @GameTest public void unsupportedLivingBehaviorsMalformedCuresAndUnreviewedNativeHistoryRefuse(GameTestHelper h) {
        var source = pig(h); source.getListOrEmpty("attributes").getCompoundOrEmpty(4).putDouble("base", 2); refuses(h, source);
        source = pig(h); source.getListOrEmpty("attributes").add(attribute("other:unreviewed", 1)); refuses(h, source);
        source = pig(h); source.getListOrEmpty("active_effects").getCompoundOrEmpty(0).put("neoforge:cures", new ListTag()); refuses(h, source);
        source = pig(h); source.getListOrEmpty("active_effects").getCompoundOrEmpty(0).getCompoundOrEmpty("hidden_effect").remove("neoforge:cures"); refuses(h, source);
        source = pig(h); source.putInt("SleepingX", 3); refuses(h, source);
        source = base(h, "minecraft:text_display"); source.put("text", new CompoundTag()); refuses(h, source);
        var converted = NeoForgeSavedVanillaEntityImport.convert(pig(h), 3955, h.getLevel().registryAccess()); var history = converted.getCompoundOrEmpty(NeoForgeLivingHistory.KEY).copy(); history.putString("UnreviewedOwnedState", "must refuse"); boolean rejected = false; try { NeoForgeLivingHistory.read(history); } catch (RuntimeException expected) { rejected = true; }
        h.assertTrue(rejected, "Native typed inert history does not pass through an unknown saved owner schema");
        var fractional=packed();fractional.getCompoundOrEmpty("components").getCompoundOrEmpty("create:minecart_contraption_data").getCompoundOrEmpty("Blocks").getListOrEmpty("BlockList").getCompoundOrEmpty(0).putDouble("Pos",.5);
        var malformedDisplay=base(h,"minecraft:text_display");malformedDisplay.putString("text",richText(fractional));boolean badPosition=false;
        try{NeoForgeSavedVanillaEntityImport.convert(malformedDisplay,3955,h.getLevel().registryAccess());}catch(IllegalArgumentException expected){badPosition=expected.getMessage().contains("Malformed original packed block position");}
        h.assertTrue(badPosition,"The actual saved display boundary refuses fractional packed positions instead of truncating them to an invented block coordinate");h.succeed();
    }
}
