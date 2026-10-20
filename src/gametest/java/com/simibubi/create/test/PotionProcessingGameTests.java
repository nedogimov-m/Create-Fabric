package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.potion.*;
import com.simibubi.create.content.fluids.potion.PotionFluid.BottleType;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.createmod.catnip.math.BlockFace;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import java.util.*;

public class PotionProcessingGameTests {
    private static final long B=FluidConstants.BUCKET;
    private static final BlockPos P=new BlockPos(3,3,3);
    private static BasinRecipeInput input(ItemStack reagent,FluidStack fluid,HeatCondition heat){return new BasinRecipeInput(List.of(reagent),List.of(new BasinRecipeInput.FluidAmount(fluid.variant(),fluid.getAmount())),heat);}
    private static BasinBlockEntity basin(GameTestHelper h){h.setBlock(P,AllBlocks.BASIN);h.setBlock(P.below(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));return h.getBlockEntity(P,BasinBlockEntity.class);}
    private static void add(BasinBlockEntity basin,ItemStack item,FluidStack fluid){try(var tx=Transaction.openOuter()){if(!item.isEmpty())basin.inventory.automation.insert(ItemVariant.of(item),item.getCount(),tx);basin.fluids.automation.insert(fluid.variant(),fluid.getAmount(),tx);tx.commit();}}
    @GameTest public void everyVanillaBrewingRecipeCanBeAdaptedToHeatedFluidMixing(GameTestHelper h){
        var level=h.getLevel();int checked=0;
        for(var holder:level.getServer().getRecipeManager().getRecipes())if(holder.value() instanceof BrewingRecipe brewing&&holder.id().identifier().getNamespace().equals("minecraft")){
            ItemStack bottle=null;
            for(var item:brewing.getInput().ingredient().items().toList())for(var potion:level.registryAccess().lookupOrThrow(Registries.POTION).listElements().toList()){
                var candidate=PotionContents.createItemStack(item.value(),potion);if(brewing.getInput().test(candidate)){bottle=candidate;break;}
            }
            h.assertTrue(bottle!=null,"Native input exists: "+holder.id());var reagent=brewing.getReagent().ingredient().items().findFirst().orElseThrow().value().getDefaultInstance();h.assertTrue(brewing.getReagent().test(reagent),"Native reagent");
            var fluid=PotionFluidHandler.getFluidFromPotionItem(bottle);fluid.setAmount(B);var converted=PotionMixingRecipes.findRecipe(level,input(reagent,fluid,HeatCondition.HEATED));h.assertTrue(converted!=null,"Brewing adapter: "+holder.id());
            var nativeResult=PotionFluidHandler.getFluidFromPotionItem(brewing.assemble(new BrewingInput(bottle,reagent)));
            var output=converted.value().results().getFirst().right().orElseThrow();h.assertValueEqual(output.variant(),nativeResult.variant(),"Native recipe output matches fluid output");h.assertValueEqual(output.amount(),B,"One bucket brewed per reagent");checked++;
        }h.assertValueEqual(checked,279,"All Minecraft 26.3 brewing recipes covered");h.succeed();
    }
    @GameTest public void basinBrewRequiresHeatAndFullBucket(GameTestHelper h){
        var fluid=PotionFluid.of(B,new PotionContents(Potions.AWKWARD),BottleType.REGULAR);var reagent=new ItemStack(Items.BLAZE_POWDER);
        h.assertTrue(PotionMixingRecipes.findRecipe(h.getLevel(),input(reagent,fluid,HeatCondition.NONE))==null,"Cold basin rejects brewing");fluid.setAmount(B-1);h.assertTrue(PotionMixingRecipes.findRecipe(h.getLevel(),input(reagent,fluid,HeatCondition.HEATED))==null,"Insufficient fluid");
        fluid.setAmount(B);h.assertTrue(PotionMixingRecipes.findRecipe(h.getLevel(),input(reagent,fluid,HeatCondition.HEATED))!=null,"Heated bucket accepted");h.succeed();
    }
    @GameTest(maxTicks=200)public void rotatingMixerBrewsNativePotionInHeatedBasin(GameTestHelper h){
        var basin=basin(h);var mixer=P.above(2);h.setBlock(mixer,AllBlocks.MECHANICAL_MIXER);
        h.assertTrue(h.getBlockEntity(P.below(),com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity.class).tryFuel(new ItemStack(Items.COAL),false,false),"Fuel keeps basin heated during mixer cycle");
        h.setBlock(mixer.west(),AllBlocks.COGWHEEL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS,Direction.Axis.Y));
        h.setBlock(mixer.west().below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.UP));
        h.getBlockEntity(mixer.west().below(),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).adjustGeneratedSpeed(false);
        add(basin,new ItemStack(Items.BLAZE_POWDER),PotionFluid.of(B,new PotionContents(Potions.AWKWARD),BottleType.LINGERING));
        h.succeedWhen(()->{h.assertTrue(basin.fluids.outputs.stream().anyMatch(tank->tank.getAmount()==B&&tank.getResource().equals(PotionFluid.of(B,new PotionContents(Potions.STRENGTH),BottleType.LINGERING).variant())),"Rotating mixer produces native lingering strength potion");h.assertTrue(basin.inventory.inputs.stream().allMatch(slot->slot.isResourceBlank()),"Reagent consumed once");});
    }
    @GameTest public void basinBrewsStrengthWithAtomicRollbackAndComponents(GameTestHelper h){
        var basin=basin(h);add(basin,new ItemStack(Items.BLAZE_POWDER,2),PotionFluid.of(B,new PotionContents(Potions.AWKWARD),BottleType.SPLASH));
        var recipe=basin.findRecipe(AllRecipeTypes.MIXING);h.assertTrue(recipe!=null,"Native recipe found by basin");h.assertTrue(basin.process(recipe.value(),true),"Simulated brew fits");h.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(),B,"Simulation retains fluid");h.assertValueEqual(basin.inventory.inputs.getFirst().getAmount(),2L,"Simulation retains reagent");
        h.assertTrue(basin.process(recipe.value(),false),"Brew committed");var result=basin.fluids.outputs.stream().filter(tank->!tank.isResourceBlank()).findFirst().orElseThrow();h.assertValueEqual(result.getAmount(),B,"Output volume");h.assertValueEqual(result.getResource(),PotionFluid.of(B,new PotionContents(Potions.STRENGTH),BottleType.SPLASH).variant(),"Strength splash fluid output");h.assertValueEqual(basin.inventory.inputs.getFirst().getAmount(),1L,"One reagent consumed");h.succeed();
    }
    @GameTest public void blockedBrewingOutputRetainsIngredients(GameTestHelper h){
        var basin=basin(h);add(basin,new ItemStack(Items.BLAZE_POWDER),PotionFluid.of(B,new PotionContents(Potions.AWKWARD),BottleType.REGULAR));
        try(var tx=Transaction.openOuter()){for(var tank:basin.fluids.outputs)tank.insert(FluidVariant.of(AllFluids.CHOCOLATE),B,tx);tx.commit();}
        var recipe=basin.findRecipe(AllRecipeTypes.MIXING);h.assertTrue(recipe!=null,"Recipe found");h.assertFalse(basin.process(recipe.value(),false),"No room for brewed potion");h.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(),B,"Fluid retained");h.assertValueEqual(basin.inventory.inputs.getFirst().getAmount(),1L,"Reagent retained");h.succeed();
    }
    @GameTest public void nativeDataPackBrewingRespectsReagentPotionPredicate(GameTestHelper h){
        var matching=PotionContents.createItemStack(Items.POTION,Potions.STRENGTH);var wrong=PotionContents.createItemStack(Items.POTION,Potions.WEAKNESS);var fluid=PotionFluid.of(B,new PotionContents(Potions.HEALING),BottleType.REGULAR);
        var input=new BasinRecipeInput(List.of(wrong,matching),List.of(new BasinRecipeInput.FluidAmount(fluid.variant(),B)),HeatCondition.HEATED);var recipe=PotionMixingRecipes.findRecipe(h.getLevel(),input);
        h.assertTrue(recipe!=null,"Data pack brewing recipe found");var consumption=recipe.value().consumption(input);h.assertValueEqual(consumption.items()[0],0L,"Wrong reagent components not consumed");h.assertValueEqual(consumption.items()[1],1L,"Correct reagent chosen");h.assertValueEqual(recipe.value().results().getFirst().right().orElseThrow().variant(),PotionFluid.of(B,new PotionContents(Potions.REGENERATION),BottleType.LINGERING).variant(),"Data pack output bottle and contents");h.succeed();
    }
    @GameTest public void honeyAndChocolateWorldLavaReactionsUseOriginalStones(GameTestHelper h){
        for(int x:new int[]{2,5}){h.setBlock(new BlockPos(x,1,3),Blocks.STONE);h.setBlock(new BlockPos(x,2,3),x==2?AllBlocks.HONEY:AllBlocks.CHOCOLATE);h.setBlock(new BlockPos(x,1,4),Blocks.STONE);h.setBlock(new BlockPos(x,2,4),Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL,3));}
        h.assertBlockPresent(AllBlocks.LIMESTONE,new BlockPos(2,2,4));h.assertBlockPresent(AllBlocks.SCORIA,new BlockPos(5,2,4));
        h.setBlock(new BlockPos(2,2,4),Blocks.LAVA);h.assertBlockPresent(Blocks.OBSIDIAN,new BlockPos(2,2,4));h.succeed();
    }
    @GameTest public void lavaPipeCollisionsCreateLimestoneAndScoria(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_PIPE);FluidReactions.handlePipeFlowCollision(h.getLevel(),h.absolutePos(P),new FluidStack(FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA),B),new FluidStack(FluidVariant.of(AllFluids.HONEY),B));h.assertBlockPresent(AllBlocks.LIMESTONE,P);
        h.setBlock(P,AllBlocks.CHOCOLATE);FluidReactions.handlePipeSpillCollision(h.getLevel(),h.absolutePos(P),net.minecraft.world.level.material.Fluids.LAVA,h.getBlockState(P).getFluidState());h.assertBlockPresent(AllBlocks.SCORIA,P);h.succeed();
    }
    private static OpenEndedPipe pipe(GameTestHelper h){var end=new OpenEndedPipe(new BlockFace(h.absolutePos(P.above()),Direction.DOWN));end.manageSource(h.getLevel(),null);return end;}
    @GameTest public void hiveHoneyDrainingIsPartialTransactionalAndPreservesHive(GameTestHelper h){
        for(var block:List.of(Blocks.BEEHIVE,Blocks.BEE_NEST)){
            h.setBlock(P,block.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5));var first=pipe(h);var second=pipe(h);var honey=FluidVariant.of(AllFluids.HONEY);
            try(var tx=Transaction.openOuter()){
                h.assertValueEqual(first.provideHandler().extract(honey,81,tx),81L,"One mB can be requested from a full hive");
                h.assertValueEqual(second.provideHandler().extract(honey,B,tx),0L,"Same hive cannot be reserved twice");
                h.assertValueEqual(h.getBlockState(P).getValue(BeehiveBlock.HONEY_LEVEL),5,"Hive unchanged while staged");
            }
            h.assertValueEqual(h.getBlockState(P).getValue(BeehiveBlock.HONEY_LEVEL),5,"Aborted drain retains honey");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(first.provideHandler().extract(honey,81,tx),81L,"Committed partial drain");tx.commit();}
            h.assertTrue(h.getBlockState(P).is(block),"Hive block preserved");h.assertValueEqual(h.getBlockState(P).getValue(BeehiveBlock.HONEY_LEVEL),0,"Honey level reset once");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(first.provideHandler().extract(honey,B,tx),B/4-81,"Original quarter bucket remainder retained in pipe");tx.commit();}
            h.assertTrue(h.getBlockEntity(P,net.minecraft.world.level.block.entity.BeehiveBlockEntity.class)!=null,"Hive block entity preserved");
        }h.succeed();
    }
    @GameTest public void potionSprayRunsOnlyAfterOuterCommitAndUsesOneMillibucket(GameTestHelper h){
        var cow=h.spawnWithNoFreeWill(EntityTypes.COW,P.getX()+0.5f,P.getY()-1,P.getZ()+0.5f);var end=pipe(h);var fluid=PotionFluid.of(B,new PotionContents(Potions.STRENGTH),BottleType.REGULAR);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().insert(fluid.variant(),B,tx),81L,"Virtual effect uses one mB");h.assertFalse(cow.hasEffect(MobEffects.STRENGTH),"No effect while staged");}
        h.assertFalse(cow.hasEffect(MobEffects.STRENGTH),"Aborted spray does nothing");
        try(var tx=Transaction.openOuter()){try(var nested=tx.openNested()){end.provideHandler().insert(fluid.variant(),B,nested);nested.commit();}h.assertFalse(cow.hasEffect(MobEffects.STRENGTH),"Nested commit still waits for outer");tx.commit();}
        h.assertTrue(cow.hasEffect(MobEffects.STRENGTH),"Committed spray applies potion");h.assertBlockPresent(Blocks.AIR,P);h.succeed();
    }
    @GameTest(maxTicks=30)public void milkAndTeaSpraysUseFiveTickCadence(GameTestHelper h){
        var cow=h.spawnWithNoFreeWill(EntityTypes.COW,P.getX()+0.5f,P.getY()-1,P.getZ()+0.5f);cow.addEffect(new MobEffectInstance(MobEffects.POISON,100));var end=pipe(h);
        h.succeedWhen(()->{h.assertTrue(h.getLevel().getGameTime()%5==0,"At effect cadence");try(var tx=Transaction.openOuter()){end.provideHandler().insert(FluidVariant.of(AllFluids.MILK),B,tx);tx.commit();}h.assertFalse(cow.hasEffect(MobEffects.POISON),"Milk clears poison using vanilla milk consume effect");
            try(var tx=Transaction.openOuter()){end.provideHandler().insert(FluidVariant.of(AllFluids.TEA),B,tx);tx.commit();}h.assertTrue(cow.hasEffect(MobEffects.HASTE),"Tea spray grants haste");h.assertValueEqual(cow.getEffect(MobEffects.HASTE).getDuration(),21,"Short spray duration");});
    }
    @GameTest public void teaFoodUsesModernConsumableAndReturnsGlassBottle(GameTestHelper h){
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var stack=new ItemStack(AllItems.BUILDERS_TEA);var result=stack.finishUsingItem(h.getLevel(),player);
        h.assertTrue(result.is(Items.GLASS_BOTTLE),"Drinking returns glass bottle");h.assertTrue(player.hasEffect(MobEffects.HASTE),"Tea grants haste");h.assertValueEqual(player.getEffect(MobEffects.HASTE).getDuration(),3600,"Three minute tea effect");h.assertValueEqual(AllItems.BUILDERS_TEA.getUseDuration(new ItemStack(AllItems.BUILDERS_TEA),player),42,"Original drinking duration");h.succeed();
    }
}
