package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.processing.sequenced.*;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.util.RandomSource;

public class SequencedAssemblyGameTests {
    @GameTest public void loadedTrackRecipeAcceptsBothNuggetTagsAndFinishesOnlyAfterPressing(GameTestHelper h){
        for(var nugget:new Item[]{Items.IRON_NUGGET,AllItems.ZINC_NUGGET}){
            ItemStack input=new ItemStack(net.minecraft.world.level.block.Blocks.STONE_SLAB);
            for(int step=0;step<2;step++){
                var match=SequencedAssemblyRecipe.getRecipe(h.getLevel(),input,AllRecipeTypes.DEPLOYING,
                    recipe->recipe instanceof ItemApplicationRecipe application&&application.getRequiredHeldItem().test(new ItemStack(nugget))).orElseThrow();
                h.assertTrue(match.id().equals(Create.asResource("sequenced_assembly/track"))&&match.parent().totalSteps()==3,"Real registered three-step track recipe accepts both source nugget tags");
                h.assertFalse(((ItemApplicationRecipe)match.recipe()).getRequiredHeldItem().test(new ItemStack(Items.GOLD_NUGGET)),"An unrelated nugget tag cannot replace iron or zinc");
                input=match.roll(RandomSource.create(step)).getFirst();
                h.assertTrue(input.is(AllItems.INCOMPLETE_TRACK)&&input.get(AllDataComponents.SEQUENCED_ASSEMBLY).step()==step+1,"Each real deployment retains the exact unfinished step");
            }
            h.assertTrue(SequencedAssemblyRecipe.getRecipe(h.getLevel(),input,AllRecipeTypes.DEPLOYING,recipe->true).isEmpty(),"Two deployments cannot skip the final press");
            var pressed=SequencedAssemblyRecipe.getRecipe(h.getLevel(),input,AllRecipeTypes.PRESSING,recipe->true).orElseThrow().roll(RandomSource.create(2)).getFirst();
            h.assertTrue(pressed.is(AllBlocks.TRACK.asItem())&&pressed.getCount()==1&&!pressed.has(AllDataComponents.SEQUENCED_ASSEMBLY),"Native pressing completes exactly the source output");
        }
        h.succeed();
    }
    @GameTest public void precisionRecipeLoadsAllFifteenStepsAndUsesWeightedOutputs(GameTestHelper h){
        var first=SequencedAssemblyRecipe.getRecipe(h.getLevel(),new ItemStack(AllItems.GOLDEN_SHEET),AllRecipeTypes.DEPLOYING,r->r instanceof ItemApplicationRecipe recipe&&recipe.getRequiredHeldItem().test(new ItemStack(AllBlocks.COGWHEEL))).orElseThrow();
        h.assertValueEqual(first.parent().totalSteps(),15,"Three deploying actions repeated five times");h.assertValueEqual(first.parent().resultPool().getFirst().chance(),120f,"Original 120-weight result retained");h.assertTrue(Math.abs(first.parent().getOutputChance()-0.8f)<0.0001,"80 percent primary result");
        ItemStack input=new ItemStack(AllItems.GOLDEN_SHEET);for(int n=0;n<15;n++){
            var tool=n%3==0?new ItemStack(AllBlocks.COGWHEEL):n%3==1?new ItemStack(AllBlocks.LARGE_COGWHEEL):new ItemStack(Items.IRON_NUGGET);var match=SequencedAssemblyRecipe.getRecipe(h.getLevel(),input,AllRecipeTypes.DEPLOYING,r->r instanceof ItemApplicationRecipe recipe&&recipe.getRequiredHeldItem().test(tool));h.assertTrue(match.isPresent(),"Correct next step found "+n);input=match.get().roll(RandomSource.create(n)).getFirst();
            if(n<14){h.assertTrue(input.is(AllItems.INCOMPLETE_PRECISION_MECHANISM),"Transitional item");var progress=input.get(AllDataComponents.SEQUENCED_ASSEMBLY);h.assertValueEqual(progress.step(),n+1,"Exact step persisted in component");h.assertTrue(Math.abs(progress.progress()-(n+1f)/15)<0.0001,"Progress fraction");}else h.assertFalse(input.has(AllDataComponents.SEQUENCED_ASSEMBLY),"Finished result loses assembly component");
        }h.succeed();
    }
    @GameTest public void intermediateItemCannotRestartOrSkipToWrongTool(GameTestHelper h){
        var first=SequencedAssemblyRecipe.getRecipe(h.getLevel(),new ItemStack(AllItems.GOLDEN_SHEET),AllRecipeTypes.DEPLOYING,r->true).orElseThrow();var intermediate=first.roll(RandomSource.create(0)).getFirst();
        h.assertTrue(SequencedAssemblyRecipe.getRecipe(h.getLevel(),intermediate,AllRecipeTypes.DEPLOYING,r->r instanceof ItemApplicationRecipe recipe&&recipe.getRequiredHeldItem().test(new ItemStack(AllBlocks.COGWHEEL))).isEmpty(),"Cannot repeat initial cog action");
        intermediate.set(AllDataComponents.SEQUENCED_ASSEMBLY,new SequencedAssemblyRecipe.SequencedAssembly(Create.asResource("missing_assembly"),1,0.1f));h.assertTrue(SequencedAssemblyRecipe.getRecipe(h.getLevel(),intermediate,AllRecipeTypes.DEPLOYING,r->true).isEmpty(),"Stale recipe id does not start unrelated recipe");h.succeed();
    }
    @GameTest(maxTicks=140)public void sturdySheetRunsOriginalLavaFillingAndTwoPressingSteps(GameTestHelper h){
        var base=new net.minecraft.core.BlockPos(3,2,3);var machine=base.above(2);h.setBlock(base,AllBlocks.DEPOT);h.setBlock(machine,AllBlocks.SPOUT);var depot=h.getBlockEntity(base,com.simibubi.create.content.logistics.depot.DepotBlockEntity.class);var spout=h.getBlockEntity(machine,com.simibubi.create.content.fluids.spout.SpoutBlockEntity.class);
        try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){depot.inventory.automation.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(AllItems.POWDERED_OBSIDIAN),1,tx);spout.tank.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA),40500,tx);tx.commit();}
        h.runAfterDelay(28,()->{h.assertTrue(depot.getHeldItem().is(AllItems.UNPROCESSED_OBSIDIAN_SHEET),"Lava filling produces original transitional item");h.assertValueEqual(depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY).step(),1,"Filling advances first step");h.assertValueEqual(spout.tank.getAmount(),0L,"Original 500 mB lava consumed");h.setBlock(machine,AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(com.simibubi.create.content.kinetics.press.MechanicalPressBlock.FACING,net.minecraft.core.Direction.EAST));h.setBlock(machine.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,net.minecraft.core.Direction.EAST));h.getBlockEntity(machine.west(),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(256);});
        h.succeedWhen(()->h.assertTrue(java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).anyMatch(slot->slot.stack().is(AllItems.STURDY_SHEET)),"Two separate press strikes finish sturdy sheet"));
    }
    @GameTest(maxTicks=70)public void cuttingStepUsesAssemblyIngredientAndPersistsProgressBeforeExport(GameTestHelper h){
        var pos=new net.minecraft.core.BlockPos(3,3,3);h.setBlock(pos,AllBlocks.MECHANICAL_SAW);h.setBlock(pos.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,net.minecraft.core.Direction.SOUTH));h.getBlockEntity(pos.north(),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(256);var saw=h.getBlockEntity(pos,com.simibubi.create.content.kinetics.saw.SawBlockEntity.class);
        try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){h.assertValueEqual(saw.inventory.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(RuntimeWoodFixtures.ASSEMBLY_BLANK),1,tx),1L,"Assembly input accepted");tx.commit();}
        h.succeedWhen(()->{h.assertTrue(saw.inventory.appliedRecipe,"Saw applied assembly step");var output=saw.inventory.outputs().getFirst();h.assertTrue(output.is(AllItems.INCOMPLETE_PRECISION_MECHANISM),"Saw outputs transitional item");h.assertValueEqual(output.get(AllDataComponents.SEQUENCED_ASSEMBLY).step(),1,"Cutting advances assembly");h.assertTrue(SequencedAssemblyRecipe.getRecipe(h.getLevel(),output,AllRecipeTypes.DEPLOYING,r->true).isPresent(),"Next deployer step available");});
    }
}
