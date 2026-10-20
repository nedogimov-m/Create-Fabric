package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.potion.*;
import com.simibubi.create.content.fluids.potion.PotionFluid.BottleType;
import com.simibubi.create.content.fluids.spout.*;
import com.simibubi.create.content.fluids.transfer.*;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;

public class CustomFluidGameTests {
    private static final long B=FluidConstants.BUCKET;
    @GameTest public void pairedFluidsHaveOriginalIdsAndWorldSourceStates(GameTestHelper h){
        for(var family:java.util.List.of(AllFluids.POTION_FAMILY,AllFluids.TEA_FAMILY,AllFluids.MILK_FAMILY,AllFluids.HONEY_FAMILY,AllFluids.CHOCOLATE_FAMILY)){
            h.assertTrue(family.source.isSame(family.flowing)&&family.flowing.isSame(family.source),"Source/flowing identity");
            h.assertTrue(family.source.defaultFluidState().isSource(),"Source state");h.assertFalse(family.flowing.defaultFluidState().isSource(),"Flowing state");
            h.assertValueEqual(family.source.defaultFluidState().createLegacyBlock().isAir(),family.virtual,"Only placeable fluids have block states");
        }
        h.assertValueEqual(BuiltInRegistries.FLUID.getKey(AllFluids.HONEY),Create.asResource("honey"),"Original fluid ID");h.assertValueEqual(BuiltInRegistries.FLUID.getKey(AllFluids.FLOWING_CHOCOLATE),Create.asResource("flowing_chocolate"),"Flowing ID");h.succeed();
    }
    @GameTest public void honeyAndChocolateBucketsRoundTripThroughFabricContainers(GameTestHelper h){
        for(var fluid:new net.minecraft.world.level.material.Fluid[]{AllFluids.HONEY,AllFluids.CHOCOLATE}){
            var available=new FluidStack(FluidVariant.of(fluid),B);var input=new ItemStack(Items.BUCKET,2);
            var filled=GenericItemFilling.fillItem(h.getLevel(),B,input,available);h.assertTrue(filled.is(fluid.getBucket()),"Correct bucket");h.assertValueEqual(input.getCount(),1,"One empty bucket used");h.assertTrue(available.isEmpty(),"Bucket amount used");
            var emptied=GenericItemEmptying.emptyItem(h.getLevel(),filled,false);h.assertValueEqual(emptied.getFirst().variant(),FluidVariant.of(fluid),"Same fluid recovered");h.assertValueEqual(emptied.getFirst().getAmount(),B,"Bucket conserved");h.assertTrue(emptied.getSecond().is(Items.BUCKET),"Empty bucket returned");
        }h.succeed();
    }
    @GameTest public void dispenserPlacesCustomFluidAndReturnsEmptyBucket(GameTestHelper h){
        var pos=new BlockPos(3,3,3);h.setBlock(pos,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));
        var source=new net.minecraft.core.dispenser.BlockSource(h.getLevel(),h.absolutePos(pos),h.getBlockState(pos),h.getBlockEntity(pos,net.minecraft.world.level.block.entity.DispenserBlockEntity.class));
        for(var fluid:new net.minecraft.world.level.material.Fluid[]{AllFluids.HONEY,AllFluids.CHOCOLATE}){
            h.setBlock(pos.east(),Blocks.AIR);
            var result=DispenserBlock.DISPENSER_REGISTRY.get(fluid.getBucket()).dispense(source,new ItemStack(fluid.getBucket()));
            h.assertTrue(result.is(Items.BUCKET),"Placed fluid returns empty bucket");
            h.assertTrue(h.getBlockState(pos.east()).getFluidState().getType().isSame(fluid),"Correct source placed by dispenser");
        }h.succeed();
    }
    @GameTest public void blockedDispenserEjectsFullBucketWithoutPlacingFluid(GameTestHelper h){
        var pos=new BlockPos(3,3,3);h.setBlock(pos,Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.EAST));h.setBlock(pos.east(),Blocks.STONE);
        var source=new net.minecraft.core.dispenser.BlockSource(h.getLevel(),h.absolutePos(pos),h.getBlockState(pos),h.getBlockEntity(pos,net.minecraft.world.level.block.entity.DispenserBlockEntity.class));
        for(var item:new Item[]{AllItems.HONEY_BUCKET,AllItems.CHOCOLATE_BUCKET}){
            h.assertTrue(DispenserBlock.DISPENSER_REGISTRY.get(item).dispense(source,new ItemStack(item)).isEmpty(),"Blocked bucket dispensed as item");
            h.assertItemEntityPresent(item,pos.east(),2);h.assertBlockPresent(Blocks.STONE,pos.east());
        }h.succeed();
    }
    @GameTest public void milkBucketProvidesFluidAndCannotBeRefilledAsMilkBucketItem(GameTestHelper h){
        var stack=new ItemStack(Items.MILK_BUCKET);var emptied=GenericItemEmptying.emptyItem(h.getLevel(),stack,false);h.assertValueEqual(emptied.getFirst().variant(),FluidVariant.of(AllFluids.MILK),"Platform milk available");h.assertValueEqual(emptied.getFirst().getAmount(),B,"Milk volume");h.assertTrue(emptied.getSecond().is(Items.BUCKET),"Milk container returned");
        h.assertFalse(GenericItemFilling.canItemBeFilled(h.getLevel(),new ItemStack(Items.MILK_BUCKET)),"Filled milk container excluded");
        var available=new FluidStack(FluidVariant.of(AllFluids.MILK),B);h.assertTrue(GenericItemFilling.fillItem(h.getLevel(),B,new ItemStack(Items.BUCKET),available).is(Items.MILK_BUCKET),"Empty bucket fills with milk");h.succeed();
    }
    @GameTest public void everyPotionAndBottleTypeRoundTripsItsComponents(GameTestHelper h){
        for(var potion:h.getLevel().registryAccess().lookupOrThrow(Registries.POTION).listElements().toList())for(var bottle:BottleType.values()){
            var input=PotionContents.createItemStack(PotionFluidHandler.itemFromBottleType(bottle),potion);var expected=input.get(DataComponents.POTION_CONTENTS);
            var emptied=GenericItemEmptying.emptyItem(h.getLevel(),input,false);h.assertValueEqual(emptied.getFirst().getAmount(),B/4,"Create bottle volume");
            var result=GenericItemFilling.fillItem(h.getLevel(),B/4,emptied.getSecond(),emptied.getFirst());
            h.assertTrue(result.is(PotionFluidHandler.itemFromBottleType(bottle)),"Bottle type retained: "+bottle);h.assertValueEqual(result.get(DataComponents.POTION_CONTENTS),expected,"Potion contents retained: "+potion.getRegisteredName());
        }h.succeed();
    }
    @GameTest public void customEffectsColorNameAndBottleTypeSurviveTankSave(GameTestHelper h){
        var contents=new PotionContents(java.util.Optional.of(Potions.STRENGTH),java.util.Optional.of(0x123456),java.util.List.of(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE,1234,2)),java.util.Optional.of("Custom brew"));
        var fluid=PotionFluid.of(B/2,contents,BottleType.LINGERING);var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=FluidVariant.CODEC.encodeStart(ops,fluid.variant()).getOrThrow();var restored=FluidVariant.CODEC.parse(ops,encoded).getOrThrow();
        h.assertValueEqual(restored,fluid.variant(),"Complete variant serialization");var bottle=PotionFluidHandler.fillBottle(restored);h.assertTrue(bottle.is(Items.LINGERING_POTION),"Lingering retained");h.assertValueEqual(bottle.get(DataComponents.POTION_CONTENTS),contents,"All components retained");h.succeed();
    }
    @GameTest public void onlyRegularPlainWaterUsesVanillaWaterFluid(GameTestHelper h){
        var regular=PotionContents.createItemStack(Items.POTION,Potions.WATER);var splash=PotionContents.createItemStack(Items.SPLASH_POTION,Potions.WATER);
        h.assertValueEqual(PotionFluidHandler.getFluidFromPotionItem(regular).variant(),FluidVariant.of(Fluids.WATER),"Regular water uses water fluid");h.assertTrue(PotionFluidHandler.getFluidFromPotionItem(splash).variant().isOf(AllFluids.POTION),"Splash water retains bottle component");
        regular.set(DataComponents.POTION_CONTENTS,regular.get(DataComponents.POTION_CONTENTS).withEffectAdded(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE,200)));
        h.assertTrue(PotionFluidHandler.getFluidFromPotionItem(regular).variant().isOf(AllFluids.POTION),"Water with custom effect retained as potion");h.succeed();
    }
    @GameTest public void namedOrColoredWaterRetainsItsPotionComponents(GameTestHelper h){
        for(var contents:java.util.List.of(
            new PotionContents(java.util.Optional.of(Potions.WATER),java.util.Optional.of(0x123456),java.util.List.of(),java.util.Optional.empty()),
            new PotionContents(java.util.Optional.of(Potions.WATER),java.util.Optional.empty(),java.util.List.of(),java.util.Optional.of("Special water")))){
            var input=new ItemStack(Items.POTION);input.set(DataComponents.POTION_CONTENTS,contents);
            var fluid=PotionFluidHandler.getFluidFromPotionItem(input);h.assertTrue(fluid.variant().isOf(AllFluids.POTION),"Custom water kept as component-bearing potion");
            h.assertValueEqual(PotionFluidHandler.fillBottle(fluid.variant()).get(DataComponents.POTION_CONTENTS),contents,"Custom color and name retained");
        }h.succeed();
    }
    @GameTest public void potionRecipeMatchesContentsAndBottleTypeAfterCodecRoundTrip(GameTestHelper h){
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(AllRecipeTypes.FILLING,new SingleRecipeInput(new ItemStack(AllItems.CINDER_FLOUR)),h.getLevel());h.assertTrue(recipe.isPresent(),"Potion filling recipes loaded");
        var ingredient=new FluidIngredient(new FluidIngredient.ExactSelector(AllFluids.POTION,DataComponentExactPredicate.builder().expect(DataComponents.POTION_CONTENTS,new PotionContents(Potions.STRENGTH)).expect(AllDataComponents.POTION_FLUID_BOTTLE_TYPE,BottleType.REGULAR).build()),25*81L);
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var restored=FluidIngredient.CODEC.parse(ops,FluidIngredient.CODEC.encodeStart(ops,ingredient).getOrThrow()).getOrThrow();
        h.assertTrue(restored.test(PotionFluid.of(B,new PotionContents(Potions.STRENGTH),BottleType.REGULAR).variant()),"Correct potion");h.assertFalse(restored.test(PotionFluid.of(B,new PotionContents(Potions.WEAKNESS),BottleType.REGULAR).variant()),"Different potion rejected");h.assertFalse(restored.test(PotionFluid.of(B,new PotionContents(Potions.STRENGTH),BottleType.SPLASH).variant()),"Wrong bottle rejected");h.succeed();
    }
    @GameTest public void strengthPotionFillsCinderFlourIntoRedstone(GameTestHelper h){
        var input=new ItemStack(AllItems.CINDER_FLOUR,2);var fluid=PotionFluid.of(B/4,new PotionContents(Potions.STRENGTH),BottleType.REGULAR);
        h.assertValueEqual(FillingBySpout.getRequiredAmountForItem(h.getLevel(),input,fluid),25*81L,"25 mB for potion recipe");h.assertTrue(FillingBySpout.fillItem(h.getLevel(),25*81L,input,fluid).is(Items.REDSTONE),"Redstone recipe chosen by matching fluid");h.assertValueEqual(input.getCount(),1,"One flour used");h.assertValueEqual(fluid.getAmount(),225*81L,"Exact potion consumption");h.succeed();
    }
    @GameTest public void honeyTeaAndMilkFoodRecipesConsumeExactVolumes(GameTestHelper h){
        for(var entry:java.util.List.of(new Object[]{Items.GLASS_BOTTLE,AllFluids.HONEY,Items.HONEY_BOTTLE},new Object[]{Items.GLASS_BOTTLE,AllFluids.TEA,AllItems.BUILDERS_TEA},new Object[]{Items.BREAD,AllFluids.MILK,AllItems.SWEET_ROLL},new Object[]{Items.APPLE,AllFluids.HONEY,AllItems.HONEYED_APPLE},new Object[]{Items.SWEET_BERRIES,AllFluids.CHOCOLATE,AllItems.CHOCOLATE_BERRIES})){
            var stack=new ItemStack((Item)entry[0]);var fluid=new FluidStack(FluidVariant.of((net.minecraft.world.level.material.Fluid)entry[1]),B);
            long amount=FillingBySpout.getRequiredAmountForItem(h.getLevel(),stack,fluid);h.assertTrue(amount>0,"Recipe present");h.assertTrue(FillingBySpout.fillItem(h.getLevel(),amount,stack,fluid).is((Item)entry[2]),"Correct food output");h.assertValueEqual(fluid.getAmount(),B-amount,"Exact volume used");
        }h.succeed();
    }
    @GameTest(maxTicks=100)public void honeyAndChocolateSpreadWithOriginalTickRate(GameTestHelper h){
        for(int x:new int[]{2,5}){for(var pos:BlockPos.betweenClosed(new BlockPos(x-1,1,1),new BlockPos(x+1,1,5)))h.setBlock(pos,Blocks.STONE);h.setBlock(new BlockPos(x,2,3),x==2?AllBlocks.HONEY:AllBlocks.CHOCOLATE);}
        h.runAfterDelay(10,()->{h.assertBlockPresent(Blocks.AIR,new BlockPos(2,2,2));h.assertBlockPresent(Blocks.AIR,new BlockPos(5,2,2));});
        h.succeedWhen(()->{h.assertTrue(h.getBlockState(new BlockPos(2,2,2)).getFluidState().getType().isSame(AllFluids.HONEY),"Honey flows");h.assertTrue(h.getBlockState(new BlockPos(5,2,2)).getFluidState().getType().isSame(AllFluids.CHOCOLATE),"Chocolate flows");});
    }
    @GameTest public void fluidAttributeViscositiesAndEmptyingRecipesAreAvailable(GameTestHelper h){
        h.assertValueEqual(FluidVariantAttributes.getViscosity(FluidVariant.of(AllFluids.HONEY),h.getLevel()),2000,"Honey viscosity");h.assertValueEqual(FluidVariantAttributes.getViscosity(FluidVariant.of(AllFluids.CHOCOLATE),h.getLevel()),1500,"Chocolate viscosity");
        for(var item:new Item[]{Items.HONEY_BOTTLE,AllItems.BUILDERS_TEA}){var input=new ItemStack(item,2);var result=GenericItemEmptying.emptyItem(h.getLevel(),input,false);h.assertValueEqual(result.getFirst().getAmount(),B/4,"Bottle drained with recipe");h.assertTrue(result.getSecond().is(Items.GLASS_BOTTLE),"Bottle returned");h.assertValueEqual(input.getCount(),1,"One input used");}h.succeed();
    }
}
