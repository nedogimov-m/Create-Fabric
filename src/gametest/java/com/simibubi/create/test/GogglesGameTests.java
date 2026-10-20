package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.equipment.goggles.*;
import com.simibubi.create.content.equipment.goggles.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;

public class GogglesGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    @GameTest public void gogglesCraftFromGlassGoldSheetAndString(GameTestHelper h){
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,2,List.of(ItemStack.EMPTY,new ItemStack(Items.STRING),ItemStack.EMPTY,new ItemStack(Items.GLASS),new ItemStack(AllItems.GOLDEN_SHEET),new ItemStack(Items.GLASS)));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel());h.assertTrue(recipe.isPresent()&&recipe.get().value().assemble(input).is(AllItems.GOGGLES),"Original goggles survival recipe and tags loaded");h.succeed();
    }
    @GameTest public void gogglesEquipThroughModernComponentAndPreserveReplacedHelmet(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.GOGGLES));
        var result=AllItems.GOGGLES.use(h.getLevel(),player,InteractionHand.MAIN_HAND);h.assertTrue(result.consumesAction(),"Use equips goggles");h.assertTrue(GogglesItem.isWearingGoggles(player),"Goggles recognized in head slot");
        h.assertTrue(result instanceof net.minecraft.world.InteractionResult.Success success&&success.heldItemTransformedTo().is(Items.IRON_HELMET),"Previous helmet returned by native interaction result");h.assertFalse(player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.EQUIPPABLE).damageOnHurt(),"No armour durability behavior");h.succeed();
    }
    @GameTest(maxTicks=25)public void nativeDispenserEquipsGogglesInsteadOfDroppingThem(GameTestHelper h){
        h.setBlock(P,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));h.setBlock(P.east().below(),Blocks.STONE);
        var zombie=h.spawnWithNoFreeWill(EntityTypes.ZOMBIE,P.east());zombie.setNoGravity(true);zombie.setCanPickUpLoot(true);zombie.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        h.getBlockEntity(P,DispenserBlockEntity.class).setItem(0,new ItemStack(AllItems.GOGGLES));h.setBlock(P.west(),Blocks.REDSTONE_BLOCK);
        h.succeedWhen(()->{h.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(AllItems.GOGGLES),"Native equippable dispenser path used");h.assertTrue(h.getBlockEntity(P,DispenserBlockEntity.class).getItem(0).isEmpty(),"One goggles item consumed");});
    }
    @GameTest public void alternativeGogglePredicatesSupportAddonEquipment(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var enabled=new java.util.concurrent.atomic.AtomicBoolean();GogglesItem.addIsWearingPredicate(candidate->candidate==player&&enabled.get());
        h.assertFalse(GogglesItem.isWearingGoggles(player),"Empty head not recognized");enabled.set(true);h.assertTrue(GogglesItem.isWearingGoggles(player),"Addon predicate supplies goggles");enabled.set(false);h.succeed();
    }
    @GameTest public void tankTooltipUsesMillibucketsAndAggregateControllerCapacity(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_TANK);var tank=h.getBlockEntity(P,FluidTankBlockEntity.class);try(var tx=Transaction.openOuter()){tank.storage().insert(FluidVariant.of(Fluids.WATER),81000,tx);tx.commit();}
        var lines=new ArrayList<Component>();h.assertTrue(tank.addToGoggleTooltip(lines,false),"Tank tooltip available");var text=lines.toString();
        h.assertTrue(text.contains("1,000")&&text.contains("8,000"),"Fabric bucket shown as 1000 of 8000 mB");h.assertFalse(text.contains("81,000"),"Fabric units not exposed");h.succeed();
    }
    @GameTest public void emptyTankShowsCapacityAndBasinListsItemsAndFluids(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_TANK);var lines=new ArrayList<Component>();h.assertTrue(h.getBlockEntity(P,FluidTankBlockEntity.class).addToGoggleTooltip(lines,false),"Empty tank shows capacity");h.assertTrue(lines.toString().contains("8,000"),"Empty capacity");
        h.setBlock(P,AllBlocks.BASIN);var basin=h.getBlockEntity(P,BasinBlockEntity.class);lines.clear();h.assertTrue(basin.addToGoggleTooltip(lines,false)&&lines.isEmpty(),"Empty basin suppresses contents section");
        try(var tx=Transaction.openOuter()){basin.inventory.automation.insert(ItemVariant.of(Items.APPLE),3,tx);basin.fluids.automation.insert(FluidVariant.of(AllFluids.MILK),20250,tx);tx.commit();}
        h.assertTrue(basin.addToGoggleTooltip(lines,false),"Basin tooltip available");h.assertTrue(lines.toString().contains("x3")&&lines.toString().contains("250"),"Both item count and millibucket contents");h.succeed();
    }
    @GameTest public void motorAndMachineTooltipsShowOwnStressContribution(GameTestHelper h){
        h.setBlock(P,AllBlocks.CREATIVE_MOTOR);var motor=h.getBlockEntity(P,KineticBlockEntity.class);var lines=new ArrayList<Component>();motor.setNetworkState(128,128,false,new KineticGraph.Stress(262144,0,false),Map.of());
        h.assertTrue(motor.addToGoggleTooltip(lines,false),"Generator stats");h.assertTrue(lines.toString().contains("262,144"),"Motor contribution uses generated 16 RPM, not faster network speed");
        motor.setNetworkState(0,0,false,new KineticGraph.Stress(0,0,false),Map.of());lines.clear();motor.addToGoggleTooltip(lines,false);h.assertFalse(lines.toString().contains("262,144"),"Disconnected generator has no current capacity");
        h.setBlock(P,AllBlocks.MECHANICAL_PRESS);var press=h.getBlockEntity(P,KineticBlockEntity.class);press.setNetworkState(64,64,false,new KineticGraph.Stress(1000,512,false),Map.of());lines.clear();
        h.assertTrue(press.addToGoggleTooltip(lines,false)&&lines.toString().contains("512"),"Press uses own stress at current theoretical RPM");h.succeed();
    }
    @GameTest public void hoverWarningsExposeOverstressAndMixerMinimumSpeedWithoutGoggles(GameTestHelper h){
        h.setBlock(P,AllBlocks.MECHANICAL_MIXER);var mixer=h.getBlockEntity(P,KineticBlockEntity.class);var lines=new ArrayList<Component>();mixer.setNetworkState(16,16,false,new KineticGraph.Stress(1000,64,false),Map.of());
        h.assertTrue(mixer.addToTooltip(lines,false)&&lines.size()==2,"Slow mixer explains speed requirement");h.assertFalse(lines.getLast().getString().contains("_"),"Source highlight markers formatted instead of displayed");
        mixer.setNetworkState(0,64,false,new KineticGraph.Stress(100,256,true),Map.of());lines.clear();h.assertTrue(mixer.addToTooltip(lines,false),"Overstress warning available");h.assertTrue(lines.getFirst().getString().toLowerCase(Locale.ROOT).contains("overstressed"),"Overstress title");h.succeed();
    }
    @GameTest public void boilerTooltipReportsLimitsMissingWaterAndGeneratedCapacity(GameTestHelper h){
        var boiler=new BoilerData();boiler.attachedEngines=1;boiler.activeHeat=1;var lines=new ArrayList<Component>();h.assertTrue(boiler.addToGoggleTooltip(lines,false,4),"Boiler tooltip available");h.assertTrue(lines.toString().contains("water_input_rate"),"Missing water diagnosed");
        lines.clear();boiler.waterSupply=10;boiler.addToGoggleTooltip(lines,false,4);h.assertTrue(lines.toString().contains("16,384"),"Original boiler tooltip capacity");h.assertValueEqual(boiler.getActualHeat(4),1,"One level");h.succeed();
    }
    @GameTest public void burnerGogglesDoNotConsumeEquipmentAndEmptyHandRemovesThem(GameTestHelper h){
        h.setBlock(P,AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var burner=h.getBlockEntity(P,BlazeBurnerBlockEntity.class);var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=new ItemStack(AllItems.GOGGLES);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var pos=h.absolutePos(P);var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
        h.assertTrue(h.getBlockState(P).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Burner accepts goggles");h.assertTrue(burner.goggles&&stack.getCount()==1,"Decoration does not consume survival equipment");
        h.assertFalse(h.getBlockState(P).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Duplicate decoration passes interaction");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.assertTrue(h.getBlockState(P).useWithoutItem(h.getLevel(),player,hit).consumesAction(),"Empty hand removes decoration");h.assertFalse(burner.goggles,"Decoration removed");h.assertTrue(player.getMainHandItem().isEmpty(),"Removal does not duplicate goggles");h.succeed();
    }
    @GameTest public void burnerGogglesPersistAndOldBurnersDefaultToUndecorated(GameTestHelper h){
        h.setBlock(P,AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));var burner=h.getBlockEntity(P,BlazeBurnerBlockEntity.class);burner.setGoggles(true);
        var saved=burner.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(BlazeBurnerBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(P),burner.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(restored.goggles,"Decoration persisted");
        saved.remove("Goggles");restored=(BlazeBurnerBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(P),burner.getBlockState(),saved,h.getLevel().registryAccess());h.assertFalse(restored.goggles,"Old save defaults false");h.succeed();
    }
    @GameTest public void emptyBurnerDoesNotAcceptDecorativeGoggles(GameTestHelper h){
        h.setBlock(P,AllBlocks.BLAZE_BURNER);var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=new ItemStack(AllItems.GOGGLES);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var pos=h.absolutePos(P);
        h.assertFalse(h.getBlockState(P).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction(),"Empty cage passes to native equipment interaction");h.assertFalse(h.getBlockEntity(P,BlazeBurnerBlockEntity.class).goggles,"No decoration on empty cage");h.succeed();
    }
}
