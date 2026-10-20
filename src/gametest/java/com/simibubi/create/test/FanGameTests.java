package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.*;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.*;
import net.minecraft.util.ProblemReporter;

public class FanGameTests {
    private static final BlockPos FAN = new BlockPos(2, 3, 3);
    private static final BlockPos DEPOT = FAN.east(2).below();
    private static EncasedFanBlockEntity fan(GameTestHelper helper) {
        helper.setBlock(FAN.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.setBlock(FAN, AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING, Direction.EAST));
        return helper.getBlockEntity(FAN, EncasedFanBlockEntity.class);
    }
    private static DepotBlockEntity depot(GameTestHelper helper, Item item, int count) {
        helper.setBlock(DEPOT, AllBlocks.DEPOT);
        var depot = helper.getBlockEntity(DEPOT, DepotBlockEntity.class);
        try (Transaction transaction = Transaction.openOuter()) {
            depot.inventory.automation.insert(ItemVariant.of(item), count, transaction); transaction.commit();
        }
        return depot;
    }
    private static long count(DepotBlockEntity depot, Item item) {
        try (Transaction transaction = Transaction.openOuter()) { return depot.inventory.automation.extract(ItemVariant.of(item), 10000, transaction); }
    }
    @GameTest(maxTicks = 250)
    public void fanWashesFlourOnDepot(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, AllItems.WHEAT_FLOUR, 3);
        helper.setBlock(FAN.east(), Blocks.WATER);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(depot, AllItems.DOUGH), 3L, "Washing processes whole stack");
            helper.assertValueEqual(count(depot, AllItems.WHEAT_FLOUR), 0L, "Input flour consumed");
        });
    }
    @GameTest(maxTicks = 380)
    public void fanProcessingTimeScalesWithStackSize(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, AllItems.WHEAT_FLOUR, 17);
        helper.setBlock(FAN.east(), Blocks.WATER);
        helper.runAfterDelay(180, () -> {
            helper.assertValueEqual(count(depot, AllItems.WHEAT_FLOUR), 17L, "17 items need two processing intervals");
            helper.assertValueEqual(count(depot, AllItems.DOUGH), 0L, "Large stack not processed prematurely");
        });
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, AllItems.DOUGH), 17L, "Whole stack processed after 300 ticks"));
    }
    @GameTest(maxTicks = 240)
    public void fanSmokesFoodThroughLitCampfire(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, Items.COD, 2);
        helper.setBlock(FAN.east(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, Items.COOKED_COD), 2L, "Smoking uses vanilla recipe"));
    }
    @GameTest(maxTicks = 240)
    public void fanSmokesThroughOrdinaryLitEmptyBurner(GameTestHelper helper) {
        fan(helper);var depot=depot(helper,Items.COD,2);helper.setBlock(FAN.east(),AllBlocks.LIT_BLAZE_BURNER);
        helper.assertValueEqual(FanProcessing.Type.at(helper.getLevel(),helper.absolutePos(FAN.east())),FanProcessing.Type.SMOKING,"Regular flame chooses smoking rather than haunting");
        helper.succeedWhen(()->helper.assertValueEqual(count(depot,Items.COOKED_COD),2L,"Air passes through cage and smokes food"));
    }
    @GameTest(maxTicks = 240)
    public void fanHauntsThroughSoulLitEmptyBurner(GameTestHelper helper) {
        fan(helper);var depot=depot(helper,Items.SAND,2);helper.setBlock(FAN.east(),AllBlocks.LIT_BLAZE_BURNER.defaultBlockState().setValue(com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FLAME_TYPE,com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FlameType.SOUL));
        helper.assertValueEqual(FanProcessing.Type.at(helper.getLevel(),helper.absolutePos(FAN.east())),FanProcessing.Type.HAUNTING,"Soul flame selects haunting");
        helper.succeedWhen(()->helper.assertValueEqual(count(depot,Items.SOUL_SAND),2L,"Air passes through soul cage and haunts sand"));
    }
    @GameTest(maxTicks = 230)
    public void fanHauntsThroughSoulFire(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, Items.SAND, 2);
        helper.setBlock(FAN.east().below(), Blocks.SOUL_SAND);
        helper.setBlock(FAN.east(), Blocks.SOUL_FIRE);
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, Items.SOUL_SAND), 2L, "Haunting uses Create recipe"));
    }
    @GameTest(maxTicks = 230)
    public void fanBlastsRawOreThroughLava(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, AllItems.RAW_ZINC, 2);
        helper.setBlock(FAN.east(), Blocks.LAVA);
        helper.succeedWhen(() -> helper.assertValueEqual(count(depot, AllItems.ZINC_INGOT), 2L, "Lava fan uses smelting recipe"));
    }
    @GameTest(maxTicks = 230)
    public void blastingBurnsFoodThatNeedsSmoking(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, Items.POTATO, 1);
        helper.setBlock(FAN.east(), Blocks.LAVA);
        helper.runAfterDelay(200, () -> {
            helper.assertValueEqual(count(depot, Items.POTATO), 0L, "Food is destroyed by blasting");
            helper.assertValueEqual(count(depot, Items.BAKED_POTATO), 0L, "Food is not cooked by lava fan"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 50)
    public void fullBlockStopsAirflowAndRemovingItRestoresFlow(GameTestHelper helper) {
        var fan = fan(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(fan.getAirCurrent().maxDistance > 3, "Powered fan creates airflow");
            helper.setBlock(FAN.east(), Blocks.STONE);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(fan.getAirCurrent().maxDistance < 0.1, "Solid block stops airflow");
            helper.setBlock(FAN.east(), Blocks.AIR);
        });
        helper.runAfterDelay(15, () -> { helper.assertTrue(fan.getAirCurrent().maxDistance > 3, "Removing blocker restores airflow"); helper.succeed(); });
    }
    @GameTest(maxTicks = 30)
    public void fanShaftConnectsOnlyAtBack(GameTestHelper helper) {
        helper.setBlock(FAN, AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING, Direction.EAST));
        helper.setBlock(FAN.east(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.WEST));
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(helper.getBlockEntity(FAN, EncasedFanBlockEntity.class).getSpeed(), 0f, "Propeller front is not a shaft connection"); helper.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void reversingFanChangesEntityMotion(GameTestHelper helper) {
        var fan = fan(helper);
        var position = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(FAN.east(2)));
        var item = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, new ItemStack(Items.DIAMOND), 0, 0, 0);
        item.setNoGravity(true); helper.getLevel().addFreshEntity(item);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(fan.isPushing() && item.getDeltaMovement().x > 0, "Positive shaft rotation pushes east");
            helper.getBlockEntity(FAN.west(), KineticBlockEntity.class).adjustGeneratedSpeed(true);
            item.setPos(position); item.setDeltaMovement(0, 0, 0);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!fan.isPushing() && item.getDeltaMovement().x < 0, "Reversed shaft rotation pulls west"); helper.succeed();
        });
    }
    @GameTest
    public void itemProcessingProgressUsesPersistentAttachment(GameTestHelper helper) {
        var pos = helper.absolutePos(FAN);
        var item = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(AllItems.WHEAT_FLOUR, 2));
        item.setAttached(FanProcessing.PROGRESS, new FanProcessing.Progress("SPLASHING", ItemVariant.of(AllItems.WHEAT_FLOUR), 2, 85, 1));
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        item.saveWithoutId(output);
        var restored = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(AllItems.WHEAT_FLOUR, 2));
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        helper.assertTrue(restored.getAttached(FanProcessing.PROGRESS) != null, "Processing attachment survived serialization");
        helper.assertValueEqual(restored.getAttached(FanProcessing.PROGRESS).time(), 85, "Remaining processing time retained"); helper.succeed();
    }

    @GameTest(maxTicks = 240)
    public void fanWashesItemEntityInWorld(GameTestHelper helper) {
        fan(helper);
        helper.setBlock(FAN.east().below(), Blocks.STONE); helper.setBlock(FAN.east().above(), Blocks.STONE);
        helper.setBlock(FAN.east().north(), Blocks.STONE); helper.setBlock(FAN.east().south(), Blocks.STONE);
        helper.setBlock(FAN.east(2), Blocks.STONE); helper.setBlock(FAN.east(), Blocks.WATER);
        var pos = helper.absolutePos(FAN.east());
        var item = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(AllItems.WHEAT_FLOUR, 2), 0, 0, 0);
        helper.getLevel().addFreshEntity(item);
        helper.succeedWhen(() -> helper.assertTrue(item.isAlive() && item.getItem().is(AllItems.DOUGH) && item.getItem().getCount() == 2, "Air current processes dropped item entity"));
    }
    @GameTest(maxTicks = 300)
    public void sourceFanProgressSurvivesReloadAndFullBuffersDoNotBlockActualHeldReplacement(GameTestHelper helper) {
        fan(helper);var depot=depot(helper,AllItems.WHEAT_FLOUR,1);helper.setBlock(FAN.east(),Blocks.WATER);
        try(var tx=Transaction.openOuter()){for(var slot:depot.inventory.outputs)slot.insert(ItemVariant.of(Items.DIAMOND),64,tx);tx.commit();}
        var paused=new int[1];
        helper.runAfterDelay(70,()->{
            var held=depot.getTransportedItem();helper.assertTrue(held!=null&&held.processedBy==FanProcessing.Type.SPLASHING&&held.processingTime>0,"Real source fan is still processing its original actual flour");
            var saved=depot.saveWithFullMetadata(helper.getLevel().registryAccess());paused[0]=held.processingTime;
            var restored=(DepotBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(depot.getBlockPos(),depot.getBlockState(),saved,helper.getLevel().registryAccess());
            helper.assertTrue(restored!=null&&restored.getTransportedItem().processedBy==held.processedBy&&restored.getTransportedItem().processingTime==paused[0],"Actual transported fan type and remaining time survive native save");
            helper.assertValueEqual(restored.inventory.outputs.stream().mapToLong(slot->slot.getAmount()).sum(),512L,"All original eight full output buffers survive save without exposing detached stock as an automation owner");
            helper.getLevel().removeBlockEntity(depot.getBlockPos());helper.getLevel().setBlockEntity(restored);helper.setBlock(FAN.west(),Blocks.AIR);
        });
        helper.runAfterDelay(88,()->{
            var current=helper.getBlockEntity(DEPOT,DepotBlockEntity.class);paused[0]=current.getTransportedItem().processingTime;
            helper.assertValueEqual(count(current,AllItems.WHEAT_FLOUR),1L,"Actual power loss retains original flour");
        });
        helper.runAfterDelay(110,()->{
            var current=helper.getBlockEntity(DEPOT,DepotBlockEntity.class);helper.assertValueEqual(current.getTransportedItem().processingTime,paused[0],"Naturally paused real fan does not consume progress");
            helper.setBlock(FAN.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        });
        helper.succeedWhen(()->{
            helper.assertTrue(helper.getTick()>110,"Wait for actual restore and power resumption");var current=helper.getBlockEntity(DEPOT,DepotBlockEntity.class);
            helper.assertTrue(current.getHeldItem().is(AllItems.DOUGH)&&current.getHeldItem().getCount()==1,"Source first result becomes genuine held stack while every buffer remains full");
            helper.assertValueEqual(count(current,AllItems.WHEAT_FLOUR),0L,"Original flour consumed exactly once");helper.assertValueEqual(count(current,Items.DIAMOND),512L,"All512 original buffer diamonds remain in actual owners");
        });
    }
    @GameTest(maxTicks = 240)
    public void unlitCampfireDoesNotSmokeFood(GameTestHelper helper) {
        fan(helper); var depot = depot(helper, Items.COD, 1);
        helper.setBlock(FAN.east(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        helper.runAfterDelay(200, () -> {
            helper.assertValueEqual(count(depot, Items.COD), 1L, "Unlit campfire cannot process food");
            helper.assertValueEqual(count(depot, Items.COOKED_COD), 0L, "No smoking without heat"); helper.succeed();
        });
    }
}
