package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.logistics.filter.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

public class FilterGameTests {
    @GameTest(maxTicks=70)public void sawSelectsOnlyOutputsAllowedByConfiguredList(GameTestHelper h){
        var pos=new BlockPos(3,3,3);h.setBlock(pos,AllBlocks.MECHANICAL_SAW);h.setBlock(pos.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(pos.north(),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(256);var saw=h.getBlockEntity(pos,com.simibubi.create.content.kinetics.saw.SawBlockEntity.class);saw.setFilter(filter(false,false,new ItemStack(Items.STONE_SLAB),new ItemStack(Items.STONE_STAIRS)));try(var tx=Transaction.openOuter()){saw.inventory.insert(ItemVariant.of(Items.STONE),1,tx);tx.commit();}h.succeedWhen(()->{h.assertTrue(saw.inventory.appliedRecipe,"Saw applied a recipe");h.assertTrue(saw.inventory.outputs().stream().allMatch(item->item.is(Items.STONE_SLAB)||item.is(Items.STONE_STAIRS)),"Saw output obeys either list entry");});
    }
    public static ItemStack filter(boolean deny,boolean data,ItemStack... items){var stack=new ItemStack(AllItems.FILTER);stack.set(AllDataComponents.FILTER_ITEMS,ItemContainerContents.fromItems(List.of(items)));stack.set(AllDataComponents.FILTER_ITEMS_BLACKLIST,deny);stack.set(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,data);return stack;}
    @GameTest public void allowDenyAndEmptyListsHaveOriginalSemantics(GameTestHelper h){
        var allow=FilterItemStack.of(filter(false,false,new ItemStack(Items.APPLE),new ItemStack(Items.CARROT)));h.assertTrue(allow.test(h.getLevel(),new ItemStack(Items.APPLE,12)),"Allow list ignores stack count");h.assertFalse(allow.test(h.getLevel(),new ItemStack(Items.DIAMOND)),"Unlisted item rejected");var deny=FilterItemStack.of(filter(true,false,new ItemStack(Items.APPLE)));h.assertFalse(deny.test(h.getLevel(),new ItemStack(Items.APPLE)),"Denied item rejected");h.assertTrue(deny.test(h.getLevel(),new ItemStack(Items.DIAMOND)),"Unlisted item accepted");h.assertFalse(FilterItemStack.of(filter(false,false)).test(h.getLevel(),new ItemStack(Items.APPLE)),"Empty allow list rejects all");h.assertTrue(FilterItemStack.of(filter(true,false)).test(h.getLevel(),new ItemStack(Items.APPLE)),"Empty deny list accepts all");h.assertTrue(FilterItemStack.empty().test(h.getLevel(),new ItemStack(Items.APPLE)),"No filter passes");h.succeed();
    }
    @GameTest public void strictComponentsAndNestedFiltersPersistThroughItemCodec(GameTestHelper h){
        var damaged=new ItemStack(Items.DIAMOND_PICKAXE);damaged.setDamageValue(20);var strict=filter(false,true,damaged);h.assertFalse(FilterItemStack.of(strict).test(h.getLevel(),new ItemStack(Items.DIAMOND_PICKAXE)),"Strict list checks durability");h.assertTrue(FilterItemStack.of(filter(false,false,damaged)).test(h.getLevel(),new ItemStack(Items.DIAMOND_PICKAXE)),"Loose list ignores durability");var outer=filter(false,false,strict,new ItemStack(Items.APPLE));var ops=h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,outer).getOrThrow()).getOrThrow();var wrapper=FilterItemStack.of(restored);h.assertTrue(wrapper.test(h.getLevel(),damaged),"Nested strict list restored");h.assertFalse(wrapper.test(h.getLevel(),new ItemStack(Items.DIAMOND_PICKAXE)),"Nested filter retains its own data mode");h.assertTrue(wrapper.test(h.getLevel(),new ItemStack(Items.APPLE)),"Other outer entry restored");h.succeed();
    }
    @GameTest public void fluidListUsesContainersAndItsComponentModeWithoutConsumingSamples(GameTestHelper h){
        var sample=new ItemStack(Items.WATER_BUCKET);var named=FluidVariant.of(Fluids.WATER,DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Treated")).build());var normal=new FluidStack(FluidVariant.of(Fluids.WATER),81000);var changed=new FluidStack(named,81000);var strict=FilterItemStack.of(filter(false,true,sample));h.assertTrue(strict.test(h.getLevel(),normal),"Water bucket resolves");h.assertFalse(strict.test(h.getLevel(),changed),"Strict fluid data retained");h.assertTrue(FilterItemStack.of(filter(false,false,sample)).test(h.getLevel(),changed),"Loose fluid list ignores data");h.assertFalse(FilterItemStack.of(filter(true,false,sample)).test(h.getLevel(),normal),"Fluid deny list blocks listed water");h.assertTrue(sample.is(Items.WATER_BUCKET),"Sample container never consumed");h.succeed();
    }
    @GameTest public void deployerHandStorageUsesConfiguredListAndProtectsEveryMatchingTool(GameTestHelper h){
        var pos=new BlockPos(3,3,3);h.setBlock(pos,AllBlocks.DEPLOYER);var deployer=h.getBlockEntity(pos,DeployerBlockEntity.class);deployer.setFilter(filter(false,false,new ItemStack(Items.APPLE),new ItemStack(Items.CARROT)));try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.insert(ItemVariant.of(Items.DIAMOND),1,tx),0L,"Unlisted tool rejected");h.assertValueEqual(deployer.inventory.insert(ItemVariant.of(Items.CARROT),5,tx),5L,"Allowed tool inserted");tx.commit();}try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.extract(ItemVariant.of(Items.CARROT),5,tx),0L,"Configured list protects matching hand");}h.succeed();
    }
    @GameTest public void smartPipeUsesListFiltersAndRecipeDerivedFluidSamples(GameTestHelper h){
        var pos=new BlockPos(3,3,3);h.setBlock(pos,AllBlocks.SMART_FLUID_PIPE);var pipe=h.getBlockEntity(pos,com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlockEntity.class);pipe.setFilter(filter(false,false,new ItemStack(Items.WATER_BUCKET),new ItemStack(Items.LAVA_BUCKET)));h.assertTrue(pipe.test(new FluidStack(FluidVariant.of(Fluids.LAVA),81000)),"Multiple fluid samples accepted");pipe.setFilter(filter(true,false,new ItemStack(Items.WATER_BUCKET)));h.assertFalse(pipe.test(new FluidStack(FluidVariant.of(Fluids.WATER),81000)),"Deny list changes route predicate");var flower=filter(false,true,new ItemStack(Items.POPPY));pipe.setFilter(flower);var named=FluidVariant.of(Fluids.WATER,DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Flower water")).build());h.assertTrue(pipe.test(new FluidStack(named,81000)),"Nested list sample resolves datapack emptying recipe");h.succeed();
    }
    private static FilterMenu menu(Player player){return new FilterMenu(1,player.getInventory(),player.getMainHandItem(),player.getInventory().getSelectedSlot());}
    @GameTest public void ghostCopiesAndShiftClicksNeverConsumeOrCreateRealItems(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(AllItems.FILTER));player.getInventory().setItem(9,new ItemStack(Items.DIAMOND,16));var menu=menu(player);menu.quickMoveStack(player,0);h.assertTrue(menu.getSlot(36).getItem().is(Items.DIAMOND),"Shift click copies sample");h.assertValueEqual(player.getInventory().getItem(9).getCount(),16,"Actual diamonds unchanged");menu.clicked(36,0,ContainerInput.PICKUP,player);h.assertTrue(menu.getCarried().isEmpty(),"Ghost cannot be picked up");h.assertTrue(menu.getSlot(36).getItem().isEmpty(),"Empty cursor clears ghost");menu.setCarried(new ItemStack(Items.APPLE,8));menu.clicked(37,0,ContainerInput.PICKUP,player);h.assertValueEqual(menu.getCarried().getCount(),8,"Carried real stack not consumed");h.assertValueEqual(menu.getSlot(37).getItem().getCount(),1,"Ghost stores only one sample");menu.setCarried(ItemStack.EMPTY);menu.removed(player);h.assertValueEqual(ListFilterItem.getFilterItems(player.getMainHandItem()).get(1).getCount(),1,"Ghost data saved");h.succeed();
    }
    @GameTest public void menuLocksOwnerRejectsUnknownButtonsAndClosesAfterHandChange(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var filter=new ItemStack(AllItems.FILTER);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,filter);var menu=menu(player);menu.clicked(27+player.getInventory().getSelectedSlot(),0,ContainerInput.PICKUP,player);h.assertTrue(player.getMainHandItem()==filter&&menu.getCarried().isEmpty(),"Owner cannot be picked up");h.assertFalse(menu.clickMenuButton(player,99),"Unknown button ignored");h.assertTrue(menu.clickMenuButton(player,1)&&menu.blacklist(),"Deny option saved");h.assertTrue(menu.clickMenuButton(player,3)&&menu.respectData(),"Data option saved");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(AllItems.FILTER));h.assertFalse(menu.stillValid(player),"Replacing identical item closes old menu");h.assertFalse(menu.clickMenuButton(player,0),"Stale menu cannot modify replacement");h.assertTrue(filter.getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST,false),"Original settings retained");h.succeed();
    }
}
