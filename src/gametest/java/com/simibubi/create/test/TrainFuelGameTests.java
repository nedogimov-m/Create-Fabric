package com.simibubi.create.test;

import com.simibubi.create.content.trains.entity.TrainFuel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.phys.Vec3;

/** Genuine native components and transaction-backed native inventories; no simulated cargo providers. */
public final class TrainFuelGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static Vec3 position(GameTestHelper h){return Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos.ZERO));}
    private static int consume(GameTestHelper h,SimpleContainer actual){return TrainFuel.consume(ContainerStorage.of(actual,null),h.getLevel(),position(h),null);}
    private static ItemStack fuel(Item item,int count,int duration){var stack=new ItemStack(item,count);stack.set(DataComponents.COOKING_FUEL,new CookingFuel(new ResolvableInt.Constant(duration),new ResolvableFloat.Constant(1)));return stack;}

    @GameTest(structure=STRUCTURE) public void actualCoalProviderConsumesExactlyOneOriginalFuel(GameTestHelper h){
        var inventory=new SimpleContainer(new ItemStack(Items.COAL,3));
        int expected=TrainFuel.duration(inventory.getItem(0),h.getLevel(),position(h),null);
        h.assertTrue(expected>0,"Real coal has an actual native fuel provider");
        h.assertTrue(consume(h,inventory)==expected&&inventory.getItem(0).is(Items.COAL)&&inventory.getItem(0).getCount()==2,"Native source consumes exactly one original item and its real data-provider duration");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualLavaFuelReturnsTheOriginalNativeCraftingRemainder(GameTestHelper h){
        var inventory=new SimpleContainer(new ItemStack(Items.LAVA_BUCKET));
        int expected=TrainFuel.duration(inventory.getItem(0),h.getLevel(),position(h),null);
        h.assertTrue(expected>0&&consume(h,inventory)==expected&&inventory.getItem(0).is(Items.BUCKET)&&inventory.getItem(0).getCount()==1,"Real lava fuel becomes exactly one actual native bucket");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nativeFuelComponentOverridesTheItemDefault(GameTestHelper h){
        var inventory=new SimpleContainer(fuel(Items.COAL,2,73));
        h.assertTrue(consume(h,inventory)==73&&inventory.getItem(0).getCount()==1&&inventory.getItem(0).get(DataComponents.COOKING_FUEL).burnTime().equals(new ResolvableInt.Constant(73)),"Native item components determine duration and the remaining original stack keeps its actual components");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void fullActualFuelInventoryRollsBackTheWholeRemainderTransaction(GameTestHelper h){
        var original=fuel(Items.HONEY_BOTTLE,2,91);
        h.assertTrue(original.getItem().getCraftingRemainder()!=null,"Real honey bottle has a native glass-bottle crafting remainder");
        var inventory=new SimpleContainer(original.copy());
        h.assertTrue(consume(h,inventory)==0&&ItemStack.matches(original,inventory.getItem(0)),"An actual full slot restores the complete original fuel when its different remainder cannot fit");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void zeroDurationAndNonfuelCargoAreNeverExtracted(GameTestHelper h){
        var zero=fuel(Items.COAL,3,0);var stone=new ItemStack(Items.STONE,7);var inventory=new SimpleContainer(zero.copy(),stone.copy());
        h.assertTrue(consume(h,inventory)==0&&ItemStack.matches(zero,inventory.getItem(0))&&ItemStack.matches(stone,inventory.getItem(1)),"Native zero duration and nonfuel entries remain exactly the original cargo");h.succeed();
    }
}
