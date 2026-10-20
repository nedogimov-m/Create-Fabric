package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.*;
import com.simibubi.create.infrastructure.worldgen.AllPlacedFeatures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;

public class BurnerGameTests {
    private static final BlockPos POS = new BlockPos(3, 2, 3);
    private static BlazeBurnerBlockEntity burner(GameTestHelper helper) {
        helper.setBlock(POS, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        return helper.getBlockEntity(POS, BlazeBurnerBlockEntity.class);
    }
    @GameTest
    public void burnerReadsModernCookingFuelComponent(GameTestHelper helper) {
        var burner = burner(helper);
        helper.assertTrue(burner.tryFuel(new ItemStack(Items.COAL), false, true), "Coal is valid via cooking fuel component");
        helper.assertValueEqual(burner.getRemainingBurnTime(), 0, "Fuel simulation is read-only");
        helper.assertTrue(burner.tryFuel(new ItemStack(Items.COAL), false, false), "Coal accepted");
        helper.assertValueEqual(burner.getRemainingBurnTime(), 1600, "Coal burns 1600 ticks");
        helper.assertValueEqual(helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL), BlazeBurnerBlock.HeatLevel.KINDLED, "Coal heats burner");
        helper.assertFalse(burner.tryFuel(new ItemStack(Items.COAL), false, false), "Automation threshold rejects excess fuel"); helper.succeed();
    }
    @GameTest(maxTicks = 60)
    public void ordinaryFuelBurnsOutToSmouldering(GameTestHelper helper) {
        var burner = burner(helper); burner.tryFuel(new ItemStack(Items.STICK), false, false);
        helper.assertValueEqual(burner.getRemainingBurnTime(), 100, "Stick burn time");
        helper.assertValueEqual(helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL), BlazeBurnerBlock.HeatLevel.FADING, "Small remaining heat fades");
        // Advance the actual ticker without waiting for a real 100-tick test world.
        for (int i = 0; i < 100; i++) BlazeBurnerBlockEntity.tick(helper.getLevel(), helper.absolutePos(POS), burner.getBlockState(), burner);
        helper.assertValueEqual(burner.getRemainingBurnTime(), 0, "Burn time reaches zero");
        helper.assertValueEqual(helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL), BlazeBurnerBlock.HeatLevel.SMOULDERING, "Captured blaze remains after fuel ends"); helper.succeed();
    }
    @GameTest
    public void specialFuelTransitionsToNormalHeat(GameTestHelper helper) {
        var burner = burner(helper); burner.tryFuel(new ItemStack(AllItems.BLAZE_CAKE), false, false);
        helper.assertValueEqual(burner.getRemainingBurnTime(), 3200, "Blaze cake burn time");
        helper.assertValueEqual(helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL), BlazeBurnerBlock.HeatLevel.SEETHING, "Special fuel superheats");
        helper.assertFalse(burner.tryFuel(new ItemStack(Items.COAL), true, false), "Ordinary fuel cannot replace superheated fuel");
        for (int i = 0; i < 3200; i++) BlazeBurnerBlockEntity.tick(helper.getLevel(), helper.absolutePos(POS), burner.getBlockState(), burner);
        helper.assertValueEqual(burner.getRemainingBurnTime(), 5000, "Special fuel leaves normal heat reserve");
        helper.assertValueEqual(helper.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL), BlazeBurnerBlock.HeatLevel.KINDLED, "Superheat becomes normal heat"); helper.succeed();
    }
    @GameTest
    public void brassRecipeRequiresBurningBlaze(GameTestHelper helper) {
        var burner = burner(helper); helper.setBlock(POS.above(), AllBlocks.BASIN);
        var basin = helper.getBlockEntity(POS.above(), BasinBlockEntity.class);
        try (Transaction transaction = Transaction.openOuter()) {
            basin.inventory.automation.insert(ItemVariant.of(Items.COPPER_INGOT), 1, transaction);
            basin.inventory.automation.insert(ItemVariant.of(AllItems.ZINC_INGOT), 1, transaction); transaction.commit();
        }
        helper.assertTrue(basin.findRecipe(AllRecipeTypes.MIXING) == null, "Smouldering blaze cannot melt brass");
        burner.tryFuel(new ItemStack(Items.COAL), false, false);
        var recipe = basin.findRecipe(AllRecipeTypes.MIXING);
        helper.assertTrue(recipe != null && basin.process(recipe.value(), false), "Burning blaze enables brass mixing");
        try (Transaction transaction = Transaction.openOuter()) {
            helper.assertValueEqual(basin.inventory.automation.extract(ItemVariant.of(AllItems.BRASS_INGOT), 64, transaction), 2L, "One copper plus one zinc produces two brass ingots");
        }
        helper.succeed();
    }
    @GameTest
    public void superheatedRecipeProducesFluidInOutputTank(GameTestHelper helper) {
        var burner = burner(helper); helper.setBlock(POS.above(), AllBlocks.BASIN);
        var basin = helper.getBlockEntity(POS.above(), BasinBlockEntity.class);
        try (Transaction transaction = Transaction.openOuter()) {
            basin.inventory.automation.insert(ItemVariant.of(Items.COBBLESTONE), 1, transaction); transaction.commit();
        }
        burner.tryFuel(new ItemStack(Items.COAL), false, false);
        helper.assertTrue(basin.findRecipe(AllRecipeTypes.MIXING) == null, "Normal heat cannot melt cobble");
        burner.tryFuel(new ItemStack(AllItems.BLAZE_CAKE), false, false);
        var recipe = basin.findRecipe(AllRecipeTypes.MIXING);
        helper.assertTrue(recipe != null && basin.process(recipe.value(), false), "Superheated cobble recipe applies");
        helper.assertValueEqual(basin.fluids.outputs.getFirst().getAmount(), 4050L, "50 mB lava becomes 4050 Fabric units"); helper.succeed();
    }
    @GameTest
    public void burnerFuelPersistsAcrossLoad(GameTestHelper helper) {
        var burner = burner(helper); burner.tryFuel(new ItemStack(Items.COAL), false, false);
        var restored = (BlazeBurnerBlockEntity) BlockEntity.loadStatic(helper.absolutePos(POS), burner.getBlockState(),
                burner.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.getRemainingBurnTime(), 1600, "Fuel persisted"); helper.succeed();
    }
    @GameTest
    public void emptyAndCapturedBurnersDropCorrectItems(GameTestHelper helper) {
        var burner = burner(helper);
        for (var heat : BlazeBurnerBlock.HeatLevel.values()) {
            var state = burner.getBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat);
            var filled = Block.getDrops(state, helper.getLevel(), helper.absolutePos(POS), burner);
            helper.assertTrue(filled.size() == 1 && filled.getFirst().is(heat == BlazeBurnerBlock.HeatLevel.NONE ? AllItems.EMPTY_BLAZE_BURNER : AllItems.BLAZE_BURNER), "Every heat state drops correct burner item");
        }
        var empty = AllBlocks.BLAZE_BURNER.defaultBlockState();
        var drops = Block.getDrops(empty, helper.getLevel(), helper.absolutePos(POS), burner);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(AllItems.EMPTY_BLAZE_BURNER), "Empty burner drops empty item"); helper.succeed();
    }
    @GameTest
    public void emptyBurnerCapturesBlazeSpawnerWithoutDestroyingIt(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.SPAWNER);
        var spawner = helper.getBlockEntity(POS, SpawnerBlockEntity.class);
        spawner.getSpawner().setEntityId(net.minecraft.world.entity.EntityTypes.BLAZE, helper.getLevel(), helper.getLevel().getRandom(), helper.absolutePos(POS));
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.EMPTY_BLAZE_BURNER));
        var absolute = helper.absolutePos(POS);
        AllItems.EMPTY_BLAZE_BURNER.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        helper.assertTrue(player.getMainHandItem().is(AllItems.BLAZE_BURNER), "Filled burner received");
        helper.assertTrue(helper.getBlockState(POS).is(Blocks.SPAWNER), "Spawner remains usable"); helper.succeed();
    }
    @GameTest
    public void zincGenerationAndOreDropsAreRegistered(GameTestHelper helper) {
        helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(AllPlacedFeatures.ZINC_ORE);
        helper.setBlock(POS, AllBlocks.ZINC_ORE);
        var drops = Block.getDrops(helper.getBlockState(POS), helper.getLevel(), helper.absolutePos(POS), null,
                null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(AllItems.RAW_ZINC), "Zinc ore drops raw zinc"); helper.succeed();
    }
    @GameTest
    public void emptyCageIgnitesFromOffhandAndSoulFlameDoesNotConsumeSand(GameTestHelper h) {
        h.setBlock(POS,AllBlocks.BLAZE_BURNER); var player=h.makeMockPlayer(GameType.SURVIVAL); var flint=new ItemStack(Items.FLINT_AND_STEEL); player.setItemInHand(InteractionHand.OFF_HAND,flint);
        var pos=h.absolutePos(POS);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        h.assertTrue(h.getBlockState(POS).useItemOn(flint,h.getLevel(),player,InteractionHand.OFF_HAND,hit).consumesAction(),"Flint and steel ignites empty cage");h.assertBlockPresent(AllBlocks.LIT_BLAZE_BURNER,POS);h.assertValueEqual(flint.getDamageValue(),1,"Correct offhand tool damaged once");h.assertValueEqual(h.getBlockState(POS).getLightEmission(),12,"Ordinary fire light");
        var sand=new ItemStack(Items.SOUL_SAND);player.setItemInHand(InteractionHand.MAIN_HAND,sand);h.assertTrue(h.getBlockState(POS).useItemOn(sand,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Soul sand converts flame");h.assertValueEqual(sand.getCount(),1,"Soul catalyst not consumed");h.assertValueEqual(h.getBlockState(POS).getValue(LitBlazeBurnerBlock.FLAME_TYPE),LitBlazeBurnerBlock.FlameType.SOUL,"Soul state");h.assertValueEqual(h.getBlockState(POS).getLightEmission(),9,"Soul light");
        h.assertFalse(h.getBlockState(POS).useItemOn(sand,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Repeated catalyst passes through");h.succeed();
    }
    @GameTest
    public void shovelExtinguishesBothFlamesWithoutCapturingBlazeOrDuplicatingItems(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);var shovel=new ItemStack(Items.IRON_SHOVEL);player.setItemInHand(InteractionHand.MAIN_HAND,shovel);var pos=h.absolutePos(POS);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        for(var flame:LitBlazeBurnerBlock.FlameType.values()) {
            h.setBlock(POS,AllBlocks.LIT_BLAZE_BURNER.defaultBlockState().setValue(LitBlazeBurnerBlock.FLAME_TYPE,flame));h.assertTrue(h.getBlockState(POS).useItemOn(shovel,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Shovel extinguishes "+flame);h.assertBlockPresent(AllBlocks.BLAZE_BURNER,POS);h.assertValueEqual(h.getBlockState(POS).getValue(BlazeBurnerBlock.HEAT_LEVEL),BlazeBurnerBlock.HeatLevel.NONE,"Cage has no captured blaze");
        } h.assertValueEqual(shovel.getDamageValue(),2,"Tool damaged once per extinguish");h.succeed();
    }
    @GameTest
    public void litCageProvidesOnlyPassiveBoilerHeatAndCorrectComparatorAndDrops(GameTestHelper h) {
        for(var flame:LitBlazeBurnerBlock.FlameType.values()) {
            var state=AllBlocks.LIT_BLAZE_BURNER.defaultBlockState().setValue(LitBlazeBurnerBlock.FLAME_TYPE,flame);h.setBlock(POS,state);
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(POS))==null,"Lit fire needs no captured-blaze block entity");h.assertValueEqual(com.simibubi.create.api.boiler.BoilerHeater.findHeat(h.getLevel(),h.absolutePos(POS),state),0f,"Passive heat only");h.assertValueEqual(state.getAnalogOutputSignal(h.getLevel(),h.absolutePos(POS),Direction.NORTH),flame==LitBlazeBurnerBlock.FlameType.REGULAR?1:2,"Flame comparator level");
            var drops=Block.getDrops(state,h.getLevel(),h.absolutePos(POS),null);h.assertTrue(drops.size()==1&&drops.getFirst().is(AllItems.EMPTY_BLAZE_BURNER),"Fire drops one empty cage");
        } h.succeed();
    }
    @GameTest
    public void litCageCanBeRemovedWithWrenchInSurvival(GameTestHelper h) {
        h.setBlock(POS,AllBlocks.LIT_BLAZE_BURNER);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var pos=h.absolutePos(POS);
        h.assertTrue(AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))).consumesAction(),"Sneak wrench removes fire");h.assertBlockPresent(Blocks.AIR,POS);h.assertTrue(player.getInventory().contains(new ItemStack(AllItems.EMPTY_BLAZE_BURNER)),"Empty cage returned to player");h.succeed();
    }
}
