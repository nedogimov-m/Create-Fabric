package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import java.util.*;

public class DeployerGameTests {
    @GameTest(maxTicks=60)public void sampleFilterKeepsToolAfterItsDurabilityChanges(GameTestHelper h){
        var deployer=setup(h,new ItemStack(AllItems.SAND_PAPER));deployer.setFilter(new ItemStack(AllItems.SAND_PAPER));var depot=depot(h,new ItemStack(AllItems.ROSE_QUARTZ));h.succeedWhen(()->{h.assertValueEqual(count(depot,AllItems.POLISHED_ROSE_QUARTZ),1L,"Filtered polishing finished");h.assertTrue(deployer.accepts(deployer.getHeldItem()),"Direct sample filter ignores tool wear");try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.extract(ItemVariant.of(deployer.getHeldItem()),1,tx),0L,"Damaged matching tool remains protected from extraction");}});
    }
    @GameTest(maxTicks=100)public void deniedBucketInteractionCannotFallThroughToUnprotectedItemUse(GameTestHelper h){
        var target=h.absolutePos(BASE);DeployerTestFixtures.DENY_USE.add(target);var deployer=setup(h,new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(30,()->{h.assertBlockPresent(Blocks.AIR,BASE);h.assertTrue(deployer.getHeldItem().is(Items.WATER_BUCKET),"Denied block callback also prevents fallback bucket use");DeployerTestFixtures.DENY_USE.remove(target);});h.succeedWhen(()->{h.assertBlockPresent(Blocks.WATER,BASE);h.assertTrue(deployer.getHeldItem().is(Items.BUCKET),"Native bucket recovers after protection unlocks");});
    }
    @GameTest(maxTicks=70)public void removingMachineDropsHandAndCapturedLootExactlyOnce(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.DIAMOND_PICKAXE));deployer.changeMode();h.setBlock(BASE,Blocks.STONE);
        h.succeedWhen(()->{h.assertFalse(deployer.getOverflowItems().isEmpty(),"Mining created stored loot");var box=new AABB(h.absolutePos(P)).inflate(1.5);h.setBlock(P,Blocks.AIR);var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,box);h.assertValueEqual(drops.stream().filter(item->item.getItem().is(Items.DIAMOND_PICKAXE)).mapToInt(item->item.getItem().getCount()).sum(),1,"Held tool dropped once");h.assertValueEqual(drops.stream().filter(item->item.getItem().is(Items.COBBLESTONE)).mapToInt(item->item.getItem().getCount()).sum(),1,"Captured loot dropped once");deployer.setRemoved();h.assertValueEqual(h.getLevel().getEntitiesOfClass(ItemEntity.class,box).size(),drops.size(),"Removal callback does not duplicate drops");});
    }
    private static final class DrinkRecipient extends net.fabricmc.fabric.api.entity.FakePlayer {
        DrinkRecipient(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DrinkRecipient"));setGameMode(GameType.SURVIVAL);}
    }
    @GameTest(maxTicks=70)public void deployableMilkDrinksAffectTargetPlayerAndReturnContainerToHand(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.MILK_BUCKET));var recipient=new DrinkRecipient(h.getLevel());recipient.setPos(Vec3.atCenterOf(h.absolutePos(BASE)));recipient.setNoGravity(true);h.getLevel().addNewPlayer(recipient);recipient.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,1000));
        h.succeedWhen(()->{h.assertFalse(recipient.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Milk cures the target player");h.assertTrue(deployer.getHeldItem().is(Items.BUCKET),"Drinking container returned to machine hand");recipient.discard();});
    }
    @GameTest(maxTicks=60)public void fullBucketPlacesInsideUnsupportedWorkAreaAndNeverBesideSolidTarget(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.WATER_BUCKET));h.setBlock(BASE,Blocks.STONE);
        h.runAfterDelay(24,()->{h.assertTrue(deployer.getHeldItem().is(Items.WATER_BUCKET),"Solid target does not consume bucket");h.assertBlockPresent(Blocks.AIR,BASE.above());h.setBlock(BASE,Blocks.AIR);});
        h.succeedWhen(()->{h.assertBlockPresent(Blocks.WATER,BASE);h.assertTrue(deployer.getHeldItem().is(Items.BUCKET),"Water placed only inside empty work target");});
    }
    @GameTest(maxTicks=80)public void recipeRetainsReusableHeldItem(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.APPLE,2));var depot=depot(h,new ItemStack(RuntimeWoodFixtures.ASSEMBLY_BLANK,2));h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.DIAMOND),2L,"Two recipe operations completed");h.assertValueEqual(deployer.getHeldItem().getCount(),2,"keep_held_item preserves the whole stack");});
    }
    @GameTest(maxTicks=60)public void recipeConsumptionReturnsNativeCraftingRemainder(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.MILK_BUCKET));var depot=depot(h,new ItemStack(RuntimeWoodFixtures.ASSEMBLY_BLANK));h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.EMERALD),1L,"Recipe applied");h.assertTrue(deployer.getHeldItem().is(Items.BUCKET),"Milk bucket crafting remainder returned");});
    }
    @GameTest(maxTicks=100)public void protectedBlockUseHonorsFabricCallbackAndRecoversAfterUnlock(GameTestHelper h){
        var target=h.absolutePos(BASE);DeployerTestFixtures.DENY_USE.add(target);var deployer=setup(h,new ItemStack(Items.STONE,2));h.setBlock(BASE.below(),Blocks.STONE);
        h.runAfterDelay(30,()->{h.assertBlockPresent(Blocks.AIR,BASE);h.assertValueEqual(deployer.getHeldItem().getCount(),2,"Protection preserved held stack");DeployerTestFixtures.DENY_USE.remove(target);});h.succeedWhen(()->h.assertBlockPresent(Blocks.STONE,BASE));
    }
    @GameTest(maxTicks=100)public void protectedMiningHonorsFabricBreakCallbackWithoutLootOrDamage(GameTestHelper h){
        var target=h.absolutePos(BASE);DeployerTestFixtures.DENY_BREAK.add(target);var deployer=setup(h,new ItemStack(Items.DIAMOND_PICKAXE));deployer.changeMode();h.setBlock(BASE,Blocks.STONE);
        h.runAfterDelay(30,()->{h.assertBlockPresent(Blocks.STONE,BASE);h.assertValueEqual(deployer.getHeldItem().getDamageValue(),0,"Canceled break does not damage tool");h.assertTrue(deployer.getOverflowItems().isEmpty(),"Canceled break creates no loot");DeployerTestFixtures.DENY_BREAK.remove(target);});h.succeedWhen(()->h.assertBlockPresent(Blocks.AIR,BASE));
    }
    @GameTest(maxTicks=100)public void addonRecipeSearchIsCancellableAndHigherPrioritySuppliersAreLazy(GameTestHelper h){
        var position=h.absolutePos(P);var supplierCalls=new int[1];DeployerTestFixtures.SEARCH.put(position,search->search.setCanceled(true));var deployer=setup(h,new ItemStack(Items.APPLE));var depot=depot(h,new ItemStack(RuntimeWoodFixtures.ASSEMBLY_BLANK));
        h.runAfterDelay(24,()->{h.assertValueEqual(count(depot,Items.DIAMOND),0L,"Canceled search does not process");h.assertTrue(deployer.getHeldItem().is(Items.APPLE),"Canceled search preserves tool");DeployerTestFixtures.SEARCH.put(position,search->{
            search.addRecipe(()->{supplierCalls[0]++;return Optional.empty();},50);
            var original=search.getRecipe();search.addRecipe(()->Optional.of(new DeployerRecipes.Match(Create.asResource("test_override"),original.recipe(),null,List.of(new com.simibubi.create.content.processing.recipe.ProcessingOutput(Items.EMERALD,1,net.minecraft.core.component.DataComponentPatch.EMPTY,1)),true)),200);
        });});
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.EMERALD),1L,"Higher priority addon output selected");h.assertValueEqual(count(depot,Items.DIAMOND),0L,"Original recipe not also applied");h.assertValueEqual(supplierCalls[0],0,"Equal priority supplier is never evaluated");DeployerTestFixtures.SEARCH.remove(position);});
    }
    @GameTest public void adjacentPlacementPreservesBothOrientationPropertiesAndOwner(GameTestHelper h){
        var deployer=setup(h,ItemStack.EMPTY);var state=deployer.getBlockState();var player=new DeployerFakePlayer(h.getLevel(),UUID.randomUUID());player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(3,0,0));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.DEPLOYER,2));var pos=h.absolutePos(P);var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0.5,0,0),Direction.EAST,pos,false);
        var result=player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);h.assertTrue(result.consumesAction(),"Placement helper consumes native use");h.assertValueEqual(h.getBlockState(P.east()),state,"Adjacent hand retains facing and shaft alignment");h.assertValueEqual(player.getMainHandItem().getCount(),1,"Native survival placement consumes one");h.assertValueEqual(h.getBlockEntity(P.east(),DeployerBlockEntity.class).owner,player.getUUID(),"Owner assigned by native block placement");h.succeed();
    }
    @GameTest public void sourceInventoryAndPoweredFlagMigrateWithoutDuplicatingMainHand(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.APPLE,2));var saved=deployer.saveWithFullMetadata(h.getLevel().registryAccess());var items=new net.minecraft.nbt.ListTag();
        var ops=h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var main=(net.minecraft.nbt.CompoundTag)ItemStack.CODEC.encodeStart(ops,new ItemStack(Items.APPLE,2)).getOrThrow();main.putByte("Slot",(byte)0);items.add(main);var other=(net.minecraft.nbt.CompoundTag)ItemStack.CODEC.encodeStart(ops,new ItemStack(Items.DIAMOND,3)).getOrThrow();other.putByte("Slot",(byte)9);items.add(other);saved.put("Inventory",items);saved.remove("RedstoneLocked");saved.putBoolean("Powered",true);
        // The historical sparse representation never had a second modern inventory owner.
        saved.remove("PlayerInventory");saved.remove("SelectedSlot");
        DeployerFakePlayer previous=null;
        for(boolean mirror:new boolean[]{true,false}){
            if(!mirror)saved.remove("HeldItem");
            var restored=(DeployerBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),deployer.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(restored!=null,"Unambiguous historical sparse inventory loads through native block entity deserialization");h.assertTrue(restored.getHeldItem().is(Items.APPLE)&&restored.getHeldItem().getCount()==2,"Source slot zero owns exactly two apples with or without its HeldItem mirror");
            var current=restored.saveWithoutMetadata(h.getLevel().registryAccess());var retained=DeployerInventory.CODEC.parse(ops,current.get("PlayerInventory")).getOrThrow();h.assertTrue(retained.size()==1&&retained.getFirst().slot()==9&&retained.getFirst().item().is(Items.DIAMOND)&&retained.getFirst().item().getCount()==3&&restored.getOverflowItems().isEmpty()&&!current.contains("Inventory"),"Other source inventory retains its real slot until player hydration, with no second hand or overflow copy");h.assertTrue(restored.isRedstoneLocked(),"Source Powered flag retained");
            h.getLevel().removeBlockEntity(h.absolutePos(P));if(previous!=null)h.assertTrue(previous.isRemoved(),"Native replacement retires the previous private player before another owner hydrates");restored.setLevel(h.getLevel());h.getLevel().setBlockEntity(restored);var actual=h.getBlockEntity(P,DeployerBlockEntity.class);var player=actual.getPlayer();
            long apples=0,diamonds=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++){var stack=player.getInventory().getItem(slot);if(stack.is(Items.APPLE))apples+=stack.getCount();if(stack.is(Items.DIAMOND))diamonds+=stack.getCount();}
            h.assertTrue(actual==restored&&player.getMainHandItem().is(Items.APPLE)&&player.getMainHandItem().getCount()==2&&player.getInventory().getItem(9).is(Items.DIAMOND)&&player.getInventory().getItem(9).getCount()==3&&apples==2&&diamonds==3&&actual.getOverflowItems().isEmpty(),"Actual installed native machine hydrates exactly its one original hand and slot nine, with no duplicated stock");previous=player;
        }h.succeed();
    }
    @GameTest(maxTicks=120)public void sourceRandomResultsKeepExactRealOwnersAcrossFreshSaveLoadAndNeverReroll(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.CARROT));var depot=depot(h,new ItemStack(RuntimeWoodFixtures.ASSEMBLY_BLANK));
        try(var tx=Transaction.openOuter()){for(var slot:depot.inventory.outputs)slot.insert(ItemVariant.of(Items.STONE),64,tx);tx.commit();}
        var copper=new long[1];
        h.startSequence().thenWaitUntil(()->{h.assertValueEqual(count(depot,Items.DIAMOND),1L,"Actual source recipe first result owns real main");h.assertTrue(deployer.getHeldItem().isEmpty(),"Actual recipe paid its one original carrot");})
                .thenExecute(()->{
                    copper[0]=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(BASE)).inflate(6),item->item.getItem().is(Items.COPPER_INGOT)).stream().mapToLong(item->item.getItem().getCount()).sum();
                    h.assertTrue(copper[0]==0||copper[0]==1,"Original50percent additional result is either absent or one genuine world ingot");
                    h.assertValueEqual(count(depot,Items.STONE),512L,"Actual8 full buffers retain every original item");
                    var savedDeployer=deployer.saveWithFullMetadata(h.getLevel().registryAccess());var savedDepot=depot.saveWithFullMetadata(h.getLevel().registryAccess());
                    var restoredDeployer=BlockEntity.loadStatic(deployer.getBlockPos(),deployer.getBlockState(),savedDeployer,h.getLevel().registryAccess());var restoredDepot=BlockEntity.loadStatic(depot.getBlockPos(),depot.getBlockState(),savedDepot,h.getLevel().registryAccess());
                    h.getLevel().removeBlockEntity(deployer.getBlockPos());h.getLevel().setBlockEntity(restoredDeployer);h.getLevel().removeBlockEntity(depot.getBlockPos());h.getLevel().setBlockEntity(restoredDepot);
                }).thenExecuteAfter(35,()->{
                    var current=h.getBlockEntity(BASE,DepotBlockEntity.class);h.assertValueEqual(count(current,Items.DIAMOND),1L,"Fresh real owner still has exactly original one diamond");h.assertValueEqual(count(current,RuntimeWoodFixtures.ASSEMBLY_BLANK),0L,"Original input was consumed once");h.assertValueEqual(count(current,Items.STONE),512L,"All full buffers survive actual reload");
                    long after=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(BASE)).inflate(6),item->item.getItem().is(Items.COPPER_INGOT)).stream().mapToLong(item->item.getItem().getCount()).sum();h.assertValueEqual(after,copper[0],"The exact original random outcome survives fresh owners without reroll or duplicate overflow");h.assertTrue(h.getBlockEntity(P,DeployerBlockEntity.class).getHeldItem().isEmpty(),"Only the original actual carrot was paid");
                }).thenSucceed();
    }
    @GameTest(maxTicks=60)public void heldDrinkWaitsForPlayerInsteadOfBeingConsumedByMachine(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.MILK_BUCKET));h.runAfterDelay(32,()->{h.assertTrue(deployer.getHeldItem().is(Items.MILK_BUCKET),"Deployer cannot drink a deployable container itself");h.succeed();});
    }
    private static final BlockPos P=new BlockPos(3,5,3),BASE=P.below(2);
    private static DeployerBlockEntity setup(GameTestHelper h,ItemStack held){
        h.setBlock(P,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.DOWN).setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,true));h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(256);var deployer=h.getBlockEntity(P,DeployerBlockEntity.class);deployer.setHeldItem(held);return deployer;
    }
    private static DepotBlockEntity depot(GameTestHelper h,ItemStack input){h.setBlock(BASE,AllBlocks.DEPOT);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);try(var tx=Transaction.openOuter()){depot.inventory.automation.insert(ItemVariant.of(input),input.getCount(),tx);tx.commit();}return depot;}
    private static long count(DepotBlockEntity depot,Item item){return java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).filter(slot->slot.stack().is(item)).mapToLong(slot->slot.getAmount()).sum();}
    @GameTest(maxTicks=80)public void deployerPlacesOneBlockWithNativeSurvivalUseAndDoesNotRepeatSameBlock(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.STONE,2));h.setBlock(BASE.below(),Blocks.STONE);
        h.runAfterDelay(40,()->{h.assertBlockPresent(Blocks.STONE,BASE);h.assertValueEqual(deployer.getHeldItem().getCount(),1,"Only one block consumed despite creative motor");h.assertTrue(deployer.getPlayer().gameMode.isSurvival(),"Fabric hand uses survival mode");h.succeed();});
    }
    @GameTest(maxTicks=80)public void punchingHarvestsNativeLootAndToolDurabilityIntoOutputStorage(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.DIAMOND_PICKAXE));deployer.changeMode();h.setBlock(BASE,Blocks.STONE);
        h.succeedWhen(()->{h.assertBlockPresent(Blocks.AIR,BASE);h.assertValueEqual(deployer.getHeldItem().getDamageValue(),1,"One native mining durability");try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.extract(ItemVariant.of(Items.COBBLESTONE),64,tx),1L,"Mined loot captured exactly once");tx.commit();}h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(BASE))).isEmpty(),"No duplicate world drop");});
    }
    @GameTest(maxTicks=80)public void deployerPolishesOneItemAtATimeAndDamagesSandpaper(GameTestHelper h){
        var deployer=setup(h,new ItemStack(AllItems.SAND_PAPER));var depot=depot(h,new ItemStack(AllItems.ROSE_QUARTZ,2));h.succeedWhen(()->{h.assertValueEqual(count(depot,AllItems.POLISHED_ROSE_QUARTZ),2L,"Two polishing cycles");h.assertValueEqual(deployer.getHeldItem().getDamageValue(),2,"Paper damaged once per item");});
    }
    @GameTest(maxTicks=100)public void redstoneLocksStartAndLossOfRotationPausesExtendedHand(GameTestHelper h){
        var deployer=setup(h,new ItemStack(AllItems.SAND_PAPER));var depot=depot(h,new ItemStack(AllItems.ROSE_QUARTZ));h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(12,()->{h.assertValueEqual(deployer.getState(),DeployerBlockEntity.State.WAITING,"Redstone inhibits start");h.setBlock(P.east(),Blocks.AIR);h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(16);});
        h.runAfterDelay(20,()->{h.assertValueEqual(deployer.getState(),DeployerBlockEntity.State.EXPANDING,"Hand extends after unlock");h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(0);});
        var timer=new int[1];h.runAfterDelay(24,()->timer[0]=deployer.getTimer());h.runAfterDelay(40,()->{h.assertValueEqual(deployer.getTimer(),timer[0],"Paused timer does not progress");h.assertValueEqual(count(depot,AllItems.POLISHED_ROSE_QUARTZ),0L,"No work without rotation");h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(256);});h.succeedWhen(()->h.assertValueEqual(count(depot,AllItems.POLISHED_ROSE_QUARTZ),1L,"Resumed cycle finishes"));
    }
    @GameTest public void handStorageRollsBackAndProtectsFilteredToolsFromExtraction(GameTestHelper h){
        var deployer=setup(h,ItemStack.EMPTY);try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.insert(ItemVariant.of(AllBlocks.COGWHEEL.asItem()),10,tx),10L,"Insertion accepted");}h.assertTrue(deployer.getHeldItem().isEmpty(),"Aborted insertion leaves empty hand");
        try(var tx=Transaction.openOuter()){deployer.inventory.insert(ItemVariant.of(AllBlocks.COGWHEEL.asItem()),10,tx);tx.commit();}deployer.setFilter(new ItemStack(AllBlocks.COGWHEEL));try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.extract(ItemVariant.of(AllBlocks.COGWHEEL.asItem()),10,tx),0L,"Matching filter keeps hand item");}
        deployer.setFilter(ItemStack.EMPTY);try(var tx=Transaction.openOuter()){h.assertValueEqual(deployer.inventory.extract(ItemVariant.of(AllBlocks.COGWHEEL.asItem()),2,tx),2L,"Unfiltered hand extractable");}h.assertValueEqual(deployer.getHeldItem().getCount(),10,"Extraction abort restores count");h.succeed();
    }
    @GameTest public void frontInteractionSwapsWholeStacksAndWrenchChangesMode(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.APPLE,3));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CARROT,2));var pos=h.absolutePos(P);var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0,-0.5,0),Direction.DOWN,pos,false);
        h.getBlockState(P).useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit);h.assertTrue(player.getMainHandItem().is(Items.APPLE)&&player.getMainHandItem().getCount()==3,"Previous hand returned");h.assertTrue(deployer.getHeldItem().is(Items.CARROT)&&deployer.getHeldItem().getCount()==2,"Player stack now in hand");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));AllItems.WRENCH.useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit));h.assertValueEqual(deployer.getMode(),DeployerBlockEntity.Mode.PUNCH,"Wrench on hand toggles mode");h.succeed();
    }
    @GameTest(maxTicks=100)public void fullSourceDepotBuffersRetainAllStockAndPayTheRealToolForHeldReplacement(GameTestHelper h){
        var deployer=setup(h,new ItemStack(AllItems.SAND_PAPER));var depot=depot(h,new ItemStack(AllItems.ROSE_QUARTZ));
        try(var tx=Transaction.openOuter()){for(var slot:depot.inventory.outputs)slot.insert(ItemVariant.of(Items.STONE),64,tx);tx.commit();}
        h.succeedWhen(()->{h.assertValueEqual(count(depot,AllItems.POLISHED_ROSE_QUARTZ),1L,"Real source first result owns the emptied main");h.assertTrue(depot.getHeldItem().is(AllItems.POLISHED_ROSE_QUARTZ),"Result is actual source held item");h.assertValueEqual(count(depot,AllItems.ROSE_QUARTZ),0L,"Original input paid exactly once");h.assertValueEqual(deployer.getHeldItem().getDamageValue(),1,"Actual original tool pays one native durability action");h.assertValueEqual(count(depot,Items.STONE),512L,"All original eight full output slots retain their real contents");});
    }
    @GameTest(maxTicks=80)public void originalHeldItemAndCycleStatePersistAcrossLoad(GameTestHelper h){
        var deployer=setup(h,new ItemStack(AllItems.SAND_PAPER));depot(h,new ItemStack(AllItems.ROSE_QUARTZ));h.runAfterDelay(3,()->{var saved=deployer.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(DeployerBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),deployer.getBlockState(),saved,h.getLevel().registryAccess());h.assertValueEqual(restored.getState(),deployer.getState(),"Cycle state persisted");h.assertValueEqual(restored.getTimer(),deployer.getTimer(),"Timer persisted");h.assertTrue(restored.getHeldItem().is(AllItems.SAND_PAPER),"Held item persisted");h.succeed();});
    }
    @GameTest(maxTicks=550)public void threeDeployerBeltLinePerformsAllFifteenPrecisionAssemblyOperations(GameTestHelper h){
        var start=new BlockPos(1,2,3);var end=start.east(6);h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS,Direction.Axis.Z));h.assertTrue(com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end)),"Assembly belt formed");var belt=h.getBlockEntity(start,com.simibubi.create.content.kinetics.belt.BeltBlockEntity.class);h.setBlock(start.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(start.north(),KineticBlockEntity.class).setGeneratedSpeed(-64);
        var deployers=new ArrayList<DeployerBlockEntity>();for(int i=0;i<3;i++){var pos=start.east(1+i*2).above(2);h.setBlock(pos,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.DOWN).setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,false));h.setBlock(pos.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(pos.north(),KineticBlockEntity.class).setGeneratedSpeed(256);var deployer=h.getBlockEntity(pos,DeployerBlockEntity.class);deployer.setHeldItem(i==0?new ItemStack(AllBlocks.COGWHEEL,5):i==1?new ItemStack(AllBlocks.LARGE_COGWHEEL,5):new ItemStack(Items.IRON_NUGGET,5));deployers.add(deployer);}
        h.setBlock(end.east(),AllBlocks.DEPOT);var output=h.getBlockEntity(end.east(),DepotBlockEntity.class);try(var tx=Transaction.openOuter()){belt.storage(Direction.UP).insert(ItemVariant.of(AllItems.GOLDEN_SHEET),1,tx);tx.commit();}
        var passes=new int[1];h.onEachTick(()->{var item=output.getHeldItem();if(item.isEmpty())return;if(passes[0]<4){var progress=item.get(AllDataComponents.SEQUENCED_ASSEMBLY);h.assertTrue(progress!=null,"Intermediate assembly reached end");h.assertValueEqual(progress.step(),(++passes[0])*3,"Each belt pass executes three different tools");try(var tx=Transaction.openOuter()){if(belt.storage(Direction.UP).insert(ItemVariant.of(item),1,tx)==1){output.inventory.automation.extract(ItemVariant.of(item),1,tx);tx.commit();}else passes[0]--;}}
            else {h.assertFalse(item.has(AllDataComponents.SEQUENCED_ASSEMBLY),"Fifth pass finishes weighted result");for(var deployer:deployers)h.assertTrue(deployer.getHeldItem().isEmpty(),"Exactly five tool items consumed by each deployer");h.succeed();}});
    }
    @GameTest(maxTicks=60)public void handUsesNativeBucketRayWithinItsWorkArea(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.BUCKET));h.assertValueEqual(deployer.getPlayer().getEyeHeight(),0f,"Hand ray starts at the work area");h.setBlock(BASE,Blocks.WATER);h.setBlock(BASE.below(),Blocks.STONE);
        h.succeedWhen(()->{h.assertTrue(deployer.getHeldItem().is(Items.WATER_BUCKET),"Native item use picks up target water");h.assertBlockPresent(Blocks.AIR,BASE);});
    }
    @GameTest(maxTicks=60)public void handInteractsWithCowAndKeepsNativeContainerResult(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.BUCKET));var cow=h.spawnWithNoFreeWill(EntityTypes.COW,BASE);cow.setNoGravity(true);
        h.succeedWhen(()->h.assertTrue(deployer.getHeldItem().is(Items.MILK_BUCKET),"Native cow interaction returns milk bucket"));
    }
    @GameTest(maxTicks=80)public void punchingUsesSwordAttributesAndCapturesEntityLoot(GameTestHelper h){
        var deployer=setup(h,new ItemStack(Items.DIAMOND_SWORD));deployer.changeMode();var cow=h.spawnWithNoFreeWill(EntityTypes.COW,BASE);cow.setNoGravity(true);cow.setHealth(1);
        h.succeedWhen(()->{h.assertTrue(cow.isDeadOrDying(),"Sword attack uses fake player");h.assertTrue(deployer.getHeldItem().getDamageValue()>0,"Native attack damages sword");h.assertTrue(deployer.getOverflowItems().stream().anyMatch(stack->stack.is(Items.BEEF)),"Entity loot captured into machine output");});
    }
    @GameTest(maxTicks=60)public void twoFacingPunchHandsPerformFistBump(GameTestHelper h){
        var first=new BlockPos(2,3,3);var second=first.east(3);for(var pos:List.of(first,second)){h.setBlock(pos,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,pos.equals(first)?Direction.EAST:Direction.WEST).setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,false));h.setBlock(pos.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(pos.north(),KineticBlockEntity.class).setGeneratedSpeed(64);h.getBlockEntity(pos,DeployerBlockEntity.class).changeMode();}
        var seen=new boolean[1];h.onEachTick(()->{var hand=h.getBlockEntity(first,DeployerBlockEntity.class);if(hand.isFistBumping())seen[0]=true;if(seen[0]&&!hand.isFistBumping()&&hand.getState()==DeployerBlockEntity.State.RETRACTING){h.assertBlockPresent(AllBlocks.DEPLOYER,first);h.assertBlockPresent(AllBlocks.DEPLOYER,second);h.succeed();}});
    }
}
