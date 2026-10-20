package com.simibubi.create.foundation.utility;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/** Complete original saved owners are distinct from genuine source entity-data templates. All cases UNRUN. */
public final class NeoForgeSavedVanillaEntityImportGameTests {
    private static ListTag list(Tag... values) { var tag = new ListTag(); for (var value : values) tag.add(value); return tag; }
    private static CompoundTag saved(String type) {
        var tag = new CompoundTag(); tag.putString("id", type); tag.store("UUID", UUIDUtil.CODEC, UUID.randomUUID());
        tag.put("Pos", list(DoubleTag.valueOf(1), DoubleTag.valueOf(128), DoubleTag.valueOf(2))); tag.put("Motion", list(DoubleTag.valueOf(0), DoubleTag.valueOf(0), DoubleTag.valueOf(0))); tag.put("Rotation", list(FloatTag.valueOf(0), FloatTag.valueOf(0)));
        tag.putFloat("FallDistance", 0); tag.putShort("Fire", (short) 0); tag.putShort("Air", (short) 300); tag.putBoolean("OnGround", false); tag.putBoolean("Invulnerable", false); tag.putInt("PortalCooldown", 0);
        tag.put("ArmorItems", list(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag()));
        var stock = new CompoundTag(); stock.putString("id", "minecraft:diamond"); stock.putInt("count", 11); tag.put("HandItems", list(stock, new CompoundTag())); return tag;
    }
    private static void refuses(GameTestHelper h, CompoundTag source) {
        var original = source.copy(); boolean refused = false;
        try { NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess()); } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused && original.equals(source), "Incomplete original saved owner is refused without modifying its actual source tree");
    }
    @GameTest public void fullOriginalSavedEquipmentKeepsElevenButMissingRowsCannotBecomeEmpty(GameTestHelper h) {
        var source = saved("minecraft:pig"); var actual = NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        var equipment = EntityEquipment.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), actual.get("equipment")).getOrThrow();
        h.assertTrue(equipment.get(EquipmentSlot.MAINHAND).is(Items.DIAMOND) && equipment.get(EquipmentSlot.MAINHAND).getCount() == 11, "All eleven source items retain their exact actual native equipment owner");
        for (String key : new String[] {"HandItems", "ArmorItems", "Pos", "UUID"}) { var missing = source.copy(); missing.remove(key); refuses(h, missing); }
        h.succeed();
    }
    @GameTest public void actualSavedCarriersAndChestedHorsesMustSupplyTheirRealInventories(GameTestHelper h) {
        var source = saved("minecraft:allay"); refuses(h, source); source.put("Inventory", new ListTag()); NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        source = saved("minecraft:donkey"); source.putBoolean("ChestedHorse", true); refuses(h, source); source.put("Items", new ListTag()); NeoForgeSavedVanillaEntityImport.convert(source, 3955, h.getLevel().registryAccess());
        var cart = new CompoundTag(); var frame = saved("minecraft:pig"); for (String key : frame.keySet()) if (!key.equals("ArmorItems") && !key.equals("HandItems")) cart.put(key, frame.get(key).copy()); cart.putString("id", "minecraft:chest_minecart"); refuses(h, cart);
        cart.put("Items", new ListTag()); NeoForgeSavedVanillaEntityImport.convert(cart, 3955, h.getLevel().registryAccess()); h.succeed();
    }
    @GameTest public void genuineUuidlessTemplateRemainsValidOnlyAtItsProperTemplateBoundary(GameTestHelper h) {
        var template = new CompoundTag(); template.putString("id", "minecraft:pig");
        var nativeTemplate = NeoForgeVanillaEntityImport.convert(template, 3955, h.getLevel().registryAccess());
        h.assertTrue(!nativeTemplate.contains("UUID"), "Genuine template does not receive a fictional source UUID or empty saved inventory receipt"); refuses(h, template); h.succeed();
    }
}
