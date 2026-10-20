package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.component.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.material.Fluids;

public class ItemFluidTransferGameTests {
    private static final long B=FluidConstants.BUCKET;
    @GameTest public void bucketFillSimulationPreservesInputAndFluid(GameTestHelper h){
        var stack=new ItemStack(Items.BUCKET,3);var fluid=new FluidStack(FluidVariant.of(Fluids.WATER),2*B);
        h.assertTrue(GenericItemFilling.canItemBeFilled(h.getLevel(),stack),"Bucket fillable");
        h.assertValueEqual(GenericItemFilling.getRequiredAmountForItem(h.getLevel(),stack,fluid),B,"One bucket requested");
        h.assertValueEqual(stack.getCount(),3,"Simulation retains stack");h.assertValueEqual(fluid.getAmount(),2*B,"Simulation retains fluid");
        var filled=GenericItemFilling.fillItem(h.getLevel(),B,stack,fluid);
        h.assertTrue(filled.is(Items.WATER_BUCKET),"Filled bucket");h.assertValueEqual(stack.getCount(),2,"Only one item consumed");h.assertValueEqual(fluid.getAmount(),B,"Exactly one bucket consumed");h.succeed();
    }
    @GameTest public void bucketWaitsForWholeBucketAndWrongAmountDoesNotConsume(GameTestHelper h){
        var stack=new ItemStack(Items.BUCKET);var fluid=new FluidStack(FluidVariant.of(Fluids.LAVA),B/2);
        h.assertValueEqual(GenericItemFilling.getRequiredAmountForItem(h.getLevel(),stack,fluid),B,"Required amount independent of partial tank");
        h.assertTrue(GenericItemFilling.fillItem(h.getLevel(),B,stack,fluid).isEmpty(),"Not enough fluid");
        h.assertTrue(GenericItemFilling.fillItem(h.getLevel(),B/2,stack,fluid).isEmpty(),"Incorrect required amount rejected");
        h.assertValueEqual(stack.getCount(),1,"Input retained");h.assertValueEqual(fluid.getAmount(),B/2,"Fluid retained");h.succeed();
    }
    @GameTest public void bucketEmptyingCopiesOneAndRetainsItemComponents(GameTestHelper h){
        var stack=new ItemStack(Items.LAVA_BUCKET);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Named"));
        var simulated=GenericItemEmptying.emptyItem(h.getLevel(),stack,true);h.assertValueEqual(stack.getCount(),1,"Simulation retains input");
        h.assertValueEqual(simulated.getFirst().getAmount(),B,"Full bucket drained");h.assertTrue(simulated.getSecond().is(Items.BUCKET),"Empty container simulated");
        var result=GenericItemEmptying.emptyItem(h.getLevel(),stack,false);h.assertTrue(stack.isEmpty(),"One item consumed");h.assertValueEqual(result.getSecond().get(DataComponents.CUSTOM_NAME),Component.literal("Named"),"Container components retained");h.succeed();
    }
    @GameTest public void waterBottleRoundTripUsesCreateQuarterBucket(GameTestHelper h){
        var bottles=new ItemStack(Items.GLASS_BOTTLE,4);var fluid=new FluidStack(FluidVariant.of(Fluids.WATER),B);
        for(int i=0;i<4;i++){
            var filled=GenericItemFilling.fillItem(h.getLevel(),GenericItemFilling.BOTTLE,bottles,fluid);
            h.assertTrue(filled.is(Items.POTION)&&filled.get(DataComponents.POTION_CONTENTS).is(Potions.WATER),"Water bottle");
            var emptied=GenericItemEmptying.emptyItem(h.getLevel(),filled,false);
            h.assertValueEqual(emptied.getFirst().getAmount(),B/4,"Create uses 250 mB, not Fabric's 1/3 bucket");h.assertTrue(emptied.getSecond().is(Items.GLASS_BOTTLE),"Bottle returned");
        }h.assertTrue(bottles.isEmpty()&&fluid.isEmpty(),"Four bottles consume exactly one bucket");h.succeed();
    }
    @GameTest public void nonfluidAndFullContainersAreRejected(GameTestHelper h){
        h.assertFalse(GenericItemFilling.canItemBeFilled(h.getLevel(),new ItemStack(Items.MILK_BUCKET)),"Milk bucket not fillable");
        h.assertFalse(GenericItemFilling.canItemBeFilled(h.getLevel(),new ItemStack(Items.WATER_BUCKET)),"Full bucket not fillable");
        h.assertFalse(GenericItemEmptying.canItemBeEmptied(h.getLevel(),new ItemStack(Items.DIAMOND)),"Diamond not drainable");
        h.assertTrue(GenericItemFilling.preview(new ItemStack(Items.GLASS_BOTTLE),new FluidStack(FluidVariant.of(Fluids.LAVA),B))==null,"No lava glass bottles");h.succeed();
    }
    @GameTest public void moddedContainerSimulatesFillsAndDrainsComponents(GameTestHelper h){
        var stack=new ItemStack(FluidTransferFixtures.FLASK);var name=Component.literal("Treated water");
        var variant=FluidVariant.of(Fluids.WATER,DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME,name).build());var fluid=new FluidStack(variant,3*B/2);
        var required=GenericItemFilling.getRequiredAmountForItem(h.getLevel(),stack,fluid);h.assertValueEqual(required,3*B/2,"Addon accepts partial amount");h.assertFalse(stack.has(DataComponents.DAMAGE),"Preview does not mutate container");
        var filled=GenericItemFilling.fillItem(h.getLevel(),required,stack,fluid);h.assertValueEqual(filled.get(DataComponents.DAMAGE),(int)(3*B/2),"Fluid stored in item component");
        var empty=GenericItemEmptying.emptyItem(h.getLevel(),filled,true);h.assertValueEqual(empty.getFirst().variant(),variant,"Fluid components retained");h.assertValueEqual(empty.getFirst().getAmount(),B,"At most one bucket per drain operation");h.assertValueEqual(empty.getSecond().get(DataComponents.DAMAGE),(int)(B/2),"Partly filled container returned");h.assertValueEqual(filled.get(DataComponents.DAMAGE),(int)(3*B/2),"Simulation does not mutate filled item");h.succeed();
    }
    @GameTest public void containerPreviewCanRunInsideOuterTransaction(GameTestHelper h){
        try(var tx=Transaction.openOuter()){
            var result=GenericItemFilling.preview(new ItemStack(Items.BUCKET),new FluidStack(FluidVariant.of(Fluids.WATER),B));h.assertTrue(result!=null&&result.item().is(Items.WATER_BUCKET),"Nested fill preview");
            h.assertValueEqual(GenericItemEmptying.emptyItem(h.getLevel(),new ItemStack(Items.WATER_BUCKET),true).getFirst().getAmount(),B,"Nested drain preview");
        }h.succeed();
    }
    @GameTest public void fillingRecipePrecedesGenericAndChecksFluid(GameTestHelper h){
        var stack=new ItemStack(Items.DIRT,2);var fluid=new FluidStack(FluidVariant.of(Fluids.WATER),B);
        h.assertTrue(FillingBySpout.canItemBeFilled(h.getLevel(),stack),"Dirt filling recipe loaded");h.assertValueEqual(FillingBySpout.getRequiredAmountForItem(h.getLevel(),stack,fluid),B/2,"Recipe amount");
        h.assertValueEqual(FillingBySpout.getRequiredAmountForItem(h.getLevel(),stack,new FluidStack(FluidVariant.of(Fluids.LAVA),B)),-1L,"Wrong fluid rejected");
        h.assertTrue(FillingBySpout.fillItem(h.getLevel(),B/2,stack,fluid).is(Items.GRASS_BLOCK),"Recipe result");h.assertValueEqual(stack.getCount(),1,"One dirt consumed");h.assertValueEqual(fluid.getAmount(),B/2,"Recipe volume consumed");h.succeed();
    }
    @GameTest public void emptyingRecipeProducesComponentFluidAndOneContainer(GameTestHelper h){
        var stack=new ItemStack(Items.POPPY,2);var result=GenericItemEmptying.emptyItem(h.getLevel(),stack,false);
        h.assertValueEqual(result.getFirst().getAmount(),B/4,"Recipe volume");h.assertValueEqual(result.getFirst().variant().getComponents().get(DataComponents.CUSTOM_NAME),Component.literal("Flower water"),"Recipe fluid components");h.assertTrue(result.getSecond().is(Items.STICK),"Recipe item output");h.assertValueEqual(stack.getCount(),1,"One recipe item consumed");h.succeed();
    }
}
