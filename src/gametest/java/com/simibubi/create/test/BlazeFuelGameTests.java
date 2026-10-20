package com.simibubi.create.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.datamaps.BlazeBurnerFuel;
import com.simibubi.create.api.registry.CreateDataMaps;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native server-loaded maps, actual pending tags, real block use and Fabric rollback. Runtime UNRUN. */
public final class BlazeFuelGameTests {
    private static final BlockPos POS = new BlockPos(3, 2, 3);

    private static BlazeBurnerBlockEntity burner(GameTestHelper helper) {
        helper.setBlock(POS, AllBlocks.BLAZE_BURNER.defaultBlockState()
            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        return helper.getBlockEntity(POS, BlazeBurnerBlockEntity.class);
    }

    @GameTest public void sourceFuelCodecAcceptsBothFormsAndRejectsNonpositiveDurations(GameTestHelper helper) {
        helper.assertValueEqual(BlazeBurnerFuel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("1357"))
            .getOrThrow().burnTime(), 1357, "Actual source integer form");
        helper.assertValueEqual(BlazeBurnerFuel.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"burn_time\":2147483647}")).getOrThrow().burnTime(), Integer.MAX_VALUE,
            "Actual source object form supports the full positive integer range");
        for (String value : new String[] {"0", "-1", "{\"burn_time\":0}", "{}"})
            helper.assertTrue(BlazeBurnerFuel.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(value)).error().isPresent(),
                "Invalid source fuel must fail its real codec: " + value);
        helper.succeed();
    }

    @GameTest public void nativeDatapackResolvesNewTagsConditionsAndRemovalsWithoutLegacyCakeHardcode(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var regular = CreateDataMaps.REGULAR_BLAZE_BURNER_FUELS;
        helper.assertValueEqual(CreateDataMaps.get(server, regular, BlazeFuelTestFixtures.item("tagged")).burnTime(), 2400,
            "Actual new native pending tag resolves its item before Named binding");
        helper.assertValueEqual(CreateDataMaps.get(server, regular, BlazeFuelTestFixtures.item("conditioned")).burnTime(), 3456,
            "Source nested conditions use real loaded mods, item existence and nonempty pending tag");
        helper.assertValueEqual(CreateDataMaps.get(server, regular, BlazeFuelTestFixtures.item("fabric_conditioned")).burnTime(), 5678,
            "Native Fabric condition recognizes a genuinely present empty pending tag");
        for (String name : new String[] {"removed", "tag_removed", "false_conditioned"})
            helper.assertTrue(CreateDataMaps.get(server, regular, BlazeFuelTestFixtures.item(name)) == null,
                "Actual item/tag removal or false source condition leaves no fuel: " + name);
        helper.assertValueEqual(CreateDataMaps.get(server, CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS,
            AllItems.BLAZE_CAKE).burnTime(), 3200, "Exact pinned source data map provides default blaze cake");
        helper.succeed();
    }

    @GameTest public void realNativeUseConsumesOneOriginalAndSourceMapPrecedenceRejectsSecondSuperheatedRound(GameTestHelper helper) {
        var burner = burner(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 7));
        var stock = new ItemStack(BlazeFuelTestFixtures.item("both"), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stock);
        var target = helper.absolutePos(POS);
        var hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
        helper.assertTrue(helper.getBlockState(POS).useItemOn(stock, helper.getLevel(), player,
            InteractionHand.MAIN_HAND, hit).consumesAction(), "Actual native block use admits the original map fuel");
        helper.assertTrue(stock.getCount() == 1 && burner.getRemainingBurnTime() == 4567
            && helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SEETHING,
            "Superheated map beats regular map and legacy special tag and spends exactly one real original");
        helper.assertFalse(helper.getBlockState(POS).useItemOn(stock, helper.getLevel(), player,
            InteractionHand.MAIN_HAND, hit).consumesAction(), "Actual native threshold rejects the next superheated round");
        helper.assertTrue(stock.getCount() == 1 && burner.getRemainingBurnTime() == 4567
            && player.getInventory().getItem(1).is(Items.DIAMOND) && player.getInventory().getItem(1).getCount() == 7,
            "Rejected native use leaves the true original tail and all unrelated material unchanged");
        helper.succeed();
    }

    @GameTest public void mapRegularFuelRespectsNativeSimulationAndTransactionAbortAndKeepsLegacyFallbacks(GameTestHelper helper) {
        var burner = burner(helper);
        var fuel = new ItemStack(BlazeFuelTestFixtures.item("plain"));
        helper.assertTrue(burner.tryFuel(fuel, false, true) && burner.getRemainingBurnTime() == 0,
            "Native source map simulation leaves real fuel fields untouched");
        try (var transaction = Transaction.openOuter()) {
            helper.assertTrue(burner.tryFuel(fuel, false, transaction) && burner.getRemainingBurnTime() == 1234,
                "Actual Fabric transaction reserves the datapack duration");
        }
        helper.assertTrue(burner.getRemainingBurnTime() == 0
            && helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SMOULDERING,
            "Aborted native transaction restores fuel and publishes no heat");
        helper.assertTrue(burner.tryFuel(new ItemStack(BlazeFuelTestFixtures.item("legacy_normal")), false, false)
            && burner.getRemainingBurnTime() == 1600, "Actual legacy regular tag remains an addon fallback");
        helper.assertTrue(burner.tryFuel(new ItemStack(BlazeFuelTestFixtures.item("legacy_special")), false, false)
            && burner.getRemainingBurnTime() == 3200, "Actual legacy special tag can upgrade normal fuel");
        helper.succeed();
    }
    @GameTest public void realLongFuelKeepsFullTimerAcrossNativeOwnerReloadAndFurtherForcedFuel(GameTestHelper helper) {
        var names=new String[]{"long_regular","max_regular"};
        var durations=new int[]{2_000_000,Integer.MAX_VALUE};
        for(int index=0;index<names.length;index++) {
            helper.setBlock(POS,net.minecraft.world.level.block.Blocks.AIR);
            var original=burner(helper);var target=helper.absolutePos(POS);
            var player=helper.makeMockPlayer(GameType.SURVIVAL);
            var diamonds=new ItemStack(Items.DIAMOND,7);player.getInventory().setItem(1,diamonds);
            var stock=new ItemStack(BlazeFuelTestFixtures.item(names[index]),2);
            player.setItemInHand(InteractionHand.MAIN_HAND,stock);
            var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
            helper.assertTrue(helper.getBlockState(POS).useItemOn(stock,helper.getLevel(),player,
                InteractionHand.MAIN_HAND,hit).consumesAction(),"Native block interaction admits full positive-int source fuel");
            helper.assertTrue(stock.getCount()==1&&original.getRemainingBurnTime()==durations[index],
                "Actual resource map supplies exact long duration and consumes only one original");
            var encoded=original.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(target);
            var loaded=new BlazeBurnerBlockEntity(target,helper.getLevel().getBlockState(target));
            loaded.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),encoded));
            helper.getLevel().setBlockEntity(loaded);
            helper.assertTrue(helper.getLevel().getBlockEntity(target)==loaded&&loaded!=original
                &&loaded.getRemainingBurnTime()==durations[index],
                "Actual native owner save/removal/new-instance load retains two-million and MAX_VALUE timers");
            helper.assertTrue(helper.getBlockState(POS).useItemOn(stock,helper.getLevel(),player,
                InteractionHand.MAIN_HAND,hit).consumesAction(),"Pinned source still consumes forced regular input above capacity");
            helper.assertTrue(stock.isEmpty()&&loaded.getRemainingBurnTime()==durations[index]
                &&player.getInventory().getItem(1)==diamonds&&diamonds.getCount()==7,
                "Forced refill keeps exact already-over-cap timer without losing unrelated original inventory");
        }
        helper.succeed();
    }

}
