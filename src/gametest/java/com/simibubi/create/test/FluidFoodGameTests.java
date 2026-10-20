package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;

public class FluidFoodGameTests {
    private static final long B=FluidConstants.BUCKET;
    private static final BlockPos P=new BlockPos(3,3,3);
    private static BasinBlockEntity basin(GameTestHelper h){
        h.setBlock(P,AllBlocks.BASIN);h.setBlock(P.below(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED));return h.getBlockEntity(P,BasinBlockEntity.class);
    }
    private static void items(BasinBlockEntity basin,Item... items){try(var tx=Transaction.openOuter()){for(var item:items)if(basin.inventory.automation.insert(ItemVariant.of(item),1,tx)!=1)throw new AssertionError("Input full");tx.commit();}}
    private static void fluid(BasinBlockEntity basin,net.minecraft.world.level.material.Fluid fluid,long amount){try(var tx=Transaction.openOuter()){if(basin.fluids.automation.insert(FluidVariant.of(fluid),amount,tx)!=amount)throw new AssertionError("Fluid full");tx.commit();}}
    private static ItemStack take(BasinBlockEntity basin,Item item){try(var tx=Transaction.openOuter()){if(basin.inventory.automation.extract(ItemVariant.of(item),1,tx)!=1)throw new AssertionError("Missing item result: "+item);tx.commit();return new ItemStack(item);}}
    private static FluidStack takeFluid(BasinBlockEntity basin,net.minecraft.world.level.material.Fluid fluid,long amount){try(var tx=Transaction.openOuter()){if(basin.fluids.automation.extract(FluidVariant.of(fluid),amount,tx)!=amount)throw new AssertionError("Missing fluid result");tx.commit();return new FluidStack(FluidVariant.of(fluid),amount);}}
    private static void process(GameTestHelper h,BasinBlockEntity basin,net.minecraft.world.item.crafting.RecipeType<BasinRecipe> type){var recipe=basin.findRecipe(type);h.assertTrue(recipe!=null&&basin.process(recipe.value(),false),"Food recipe executes");}
    @GameTest public void heatedChocolateProductionCompactsAndMeltsWithoutVolumeLoss(GameTestHelper h){
        var basin=basin(h);items(basin,Items.SUGAR,Items.COCOA_BEANS);fluid(basin,AllFluids.MILK,B/4);process(h,basin,AllRecipeTypes.MIXING);
        h.assertValueEqual(takeFluid(basin,AllFluids.CHOCOLATE,B/4).getAmount(),B/4,"Quarter bucket chocolate produced");
        fluid(basin,AllFluids.CHOCOLATE,B/4);process(h,basin,AllRecipeTypes.COMPACTING);take(basin,AllItems.BAR_OF_CHOCOLATE);
        items(basin,AllItems.BAR_OF_CHOCOLATE);process(h,basin,AllRecipeTypes.MIXING);takeFluid(basin,AllFluids.CHOCOLATE,B/4);
        h.assertTrue(basin.fluids.allTanks().stream().allMatch(tank->tank.isResourceBlank()),"No duplicate fluid remaining");h.succeed();
    }
    @GameTest public void teaCombinesMilkAndWaterIntoTwoBottles(GameTestHelper h){
        var basin=basin(h);items(basin,Items.OAK_LEAVES);fluid(basin,Fluids.WATER,B/4);fluid(basin,AllFluids.MILK,B/4);process(h,basin,AllRecipeTypes.MIXING);
        var tea=takeFluid(basin,AllFluids.TEA,B/2);var bottles=new ItemStack(Items.GLASS_BOTTLE,2);
        for(int i=0;i<2;i++)h.assertTrue(FillingBySpout.fillItem(h.getLevel(),B/4,bottles,tea).is(AllItems.BUILDERS_TEA),"Filled tea bottle");
        h.assertTrue(bottles.isEmpty()&&tea.isEmpty(),"Two bottles conserve fluid");h.succeed();
    }
    @GameTest public void blazeCakeCompactingAndLavaFillingChainIsAvailable(GameTestHelper h){
        var basin=basin(h);items(basin,Items.EGG,Items.SUGAR,AllItems.CINDER_FLOUR);process(h,basin,AllRecipeTypes.COMPACTING);
        var base=take(basin,AllItems.BLAZE_CAKE_BASE);var lava=new FluidStack(FluidVariant.of(Fluids.LAVA),B/4);
        h.assertTrue(FillingBySpout.fillItem(h.getLevel(),B/4,base,lava).is(AllItems.BLAZE_CAKE),"Blaze cake filling recipe");h.assertTrue(lava.isEmpty(),"Quarter bucket lava consumed");h.succeed();
    }
}
